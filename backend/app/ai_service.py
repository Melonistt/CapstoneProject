"""AI provider wrapper. Routes never touch a vendor SDK directly, so swapping provider is a change here only.

Provider selection (env AI_PROVIDER): "groq", "gemini", or unset = auto (Groq if GROQ_API_KEY is set, else Gemini).
Both providers are called over plain REST with the standard library (no vendor SDK).
"""
import json
import logging
import os
import urllib.error
import urllib.request

from fastapi import HTTPException, status

log = logging.getLogger("ai")

SAFETY_RULES = """You are the AI coach inside a wellness app for general fitness, nutrition and wellbeing.
Rules you must always follow:
- You do not give medical diagnoses, clinical advice or medication advice. If the user describes symptoms,
  an injury, pregnancy, an eating disorder or a medical condition, tell them kindly to see a GP or
  accredited practising dietitian, and keep any tips general.
- Base advice on mainstream, evidence-based guidance (e.g. Australian Dietary Guidelines, the
  Australian Physical Activity Guidelines). Never suggest crash diets, fasting for days, under-eating
  below the provided calorie target, dangerous supplements or unsafe exercise.
- Respect the user's dietary preferences and allergies at all times; never include a listed allergen.
- Be friendly, concise and practical. Use metric units and Australian spelling."""

CHAT_STYLE = """Reply format for chat: be brief. At most 4 short sentences, or up to 5 one-line bullet points
(about 80 words). No preamble, no headings, no bold. Give the single most useful answer rather than every
option. Only go longer if the user explicitly asks for a full plan, recipe or detailed explanation."""

# Output caps per call type. Chat is deliberately small so the coach stays snappy on a phone screen.
CHAT_MAX_TOKENS = 350
PLAN_MAX_TOKENS = 4096

GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"


# ---------------------------------------------------------------- provider selection
def provider() -> str:
    p = os.getenv("AI_PROVIDER", "").strip().lower()
    if p in ("groq", "gemini"):
        return p
    return "groq" if os.getenv("GROQ_API_KEY") else "gemini"


def model_name() -> str:
    if provider() == "groq":
        return os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")
    return os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite")


def _require_key(var: str) -> str:
    key = os.getenv(var)
    if not key:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, f"AI service not configured ({var} missing)")
    return key


# ---------------------------------------------------------------- shared helpers
def profile_context(profile, calorie_target) -> str:
    if profile is None:
        return "The user has not filled in their profile yet; ask for key details if needed."
    fields = {
        "age": profile.age,
        "sex": profile.sex,
        "height_cm": profile.height_cm,
        "weight_kg": profile.weight_kg,
        "activity_level": profile.activity_level,
        "goal": profile.goal,
        "dietary_preferences": profile.dietary_preferences,
        "allergies": profile.allergies,
        "daily_calorie_target_kcal": calorie_target,
    }
    known = {k: v for k, v in fields.items() if v not in (None, "")}
    return "User profile: " + json.dumps(known)


def today_context(day) -> str:
    """One line on what the user has eaten and trained today, so the coach can refer to it
    ('you've already done your strength session, so...'). `day` is a DayLogOut; None = not logged in."""
    if day is None:
        return ""
    meals = ", ".join(f"{m.name} ({m.kcal} kcal)" for m in day.meals) or "nothing logged yet"
    workouts = ", ".join(f"{w.name} ({w.duration_min} min, ~{w.kcal_burned} kcal)" for w in day.workouts) or "none yet"
    remaining = f"{day.calories_remaining} kcal remaining" if day.calories_remaining is not None else "no target set"
    return (
        f"Today so far ({day.date}): eaten {day.totals.kcal} kcal [{meals}]; "
        f"workouts: {workouts}; {remaining}. Refer to this when it is relevant to the question."
    )


def week_context(days, workouts) -> str:
    """Last-7-day summary so the coach can answer 'this week' questions. `days` = list of DaySummary
    (oldest first), `workouts` = WorkoutHistoryOut."""
    logged = [d for d in days if d.meals > 0]
    avg_eaten = round(sum(d.kcal for d in logged) / len(logged)) if logged else None
    t = workouts.totals
    sessions = "; ".join(f"{w.date}: {w.name} ({w.duration_min} min, {w.workout_type})" for w in workouts.entries[:7]) or "none"
    eaten_part = f"average {avg_eaten} kcal eaten per day over {len(logged)} logged day(s)" if avg_eaten else "no meals logged"
    return (
        f"Last 7 days: {eaten_part}; {t.sessions} workout(s), {t.minutes} min, ~{t.kcal_burned} kcal burned "
        f"[{sessions}]."
    )


def _wrap_vendor_error(exc: Exception) -> HTTPException:
    # network, quota, bad key, etc. (planning report risk R4) -> clean 502, app keeps working
    log.exception("AI call failed (%s)", provider())
    detail = f"AI service error: {type(exc).__name__}"
    if isinstance(exc, urllib.error.HTTPError):
        try:
            vendor_msg = json.loads(exc.read()).get("error", {}).get("message", "")
        except Exception:
            vendor_msg = ""
        log.error("Vendor response %s: %.500s", exc.code, vendor_msg)
        detail = f"AI service error: HTTP {exc.code}"
        if exc.code in (400, 401, 403):
            detail += " (check the API key)"
        elif exc.code == 429:
            detail += " (rate limit, try again shortly)"
        if vendor_msg:
            detail += f" - {vendor_msg[:200]}"
    return HTTPException(status.HTTP_502_BAD_GATEWAY, detail)


# ---------------------------------------------------------------- Groq (OpenAI-compatible)
def _groq_generate(messages: list[dict], system: str, json_mode: bool, max_tokens: int) -> str:
    key = _require_key("GROQ_API_KEY")
    body = {
        "model": model_name(),
        "messages": [{"role": "system", "content": system}] + messages,
        "temperature": 0.7,
        "max_tokens": max_tokens,
    }
    if json_mode:
        body["response_format"] = {"type": "json_object"}
    req = urllib.request.Request(
        GROQ_URL,
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read())
    except Exception as exc:
        raise _wrap_vendor_error(exc) from exc
    return (data["choices"][0]["message"]["content"] or "").strip()


# ---------------------------------------------------------------- Gemini (REST, no SDK)
GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"


def _gemini_generate(messages: list[dict], system: str, json_mode: bool, max_tokens: int,
                     image: tuple[str, str] | None = None) -> str:
    """image = (mime_type, base64_data) attached to the last user turn (meal photo analysis)."""
    key = _require_key("GEMINI_API_KEY")
    contents = [
        {"role": "model" if m["role"] == "assistant" else "user", "parts": [{"text": m["content"]}]}
        for m in messages
    ]
    if image is not None:
        mime, data = image
        contents[-1]["parts"].append({"inline_data": {"mime_type": mime, "data": data}})
    body = {
        "system_instruction": {"parts": [{"text": system}]},
        "contents": contents,
        "generationConfig": {"temperature": 0.7, "maxOutputTokens": max_tokens},
    }
    if json_mode:
        body["generationConfig"]["responseMimeType"] = "application/json"
    req = urllib.request.Request(
        GEMINI_URL.format(model=model_name()),
        data=json.dumps(body).encode(),
        headers={"x-goog-api-key": key, "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read())
        cand = data["candidates"][0]
        parts = cand.get("content", {}).get("parts", [])
        text = "".join(p.get("text", "") for p in parts)
    except Exception as exc:
        raise _wrap_vendor_error(exc) from exc
    if not text.strip() and cand.get("finishReason") == "MAX_TOKENS" and max_tokens < PLAN_MAX_TOKENS:
        # Newer Gemini models spend output tokens on internal reasoning first; a tight cap can
        # leave no room for the visible answer. Retry once with the full budget.
        log.warning("Gemini returned no text under a %d-token cap; retrying uncapped", max_tokens)
        return _gemini_generate(messages, system, json_mode, PLAN_MAX_TOKENS, image)
    return text.strip()


def list_models() -> list[dict]:
    """Models the configured key can use (name + supported methods). Handy for picking GEMINI_MODEL/GROQ_MODEL."""
    if provider() == "groq":
        key = _require_key("GROQ_API_KEY")
        req = urllib.request.Request("https://api.groq.com/openai/v1/models", headers={"Authorization": f"Bearer {key}"})
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read())
        except Exception as exc:
            raise _wrap_vendor_error(exc) from exc
        return [{"name": m["id"]} for m in data.get("data", [])]
    key = _require_key("GEMINI_API_KEY")
    req = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", headers={"x-goog-api-key": key}
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read())
    except Exception as exc:
        raise _wrap_vendor_error(exc) from exc
    return [
        {"name": m["name"].removeprefix("models/"), "methods": m.get("supportedGenerationMethods", [])}
        for m in data.get("models", [])
        if "generateContent" in m.get("supportedGenerationMethods", [])
    ]


def _generate(messages: list[dict], system: str, json_mode: bool = False, max_tokens: int = PLAN_MAX_TOKENS,
              image: tuple[str, str] | None = None) -> str:
    """messages: OpenAI-style [{"role": "user"|"assistant", "content": str}, ...]"""
    if provider() == "groq":
        if image is not None:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE,
                                "Photo analysis needs the Gemini provider; describe the meal in words instead")
        return _groq_generate(messages, system, json_mode, max_tokens)
    return _gemini_generate(messages, system, json_mode, max_tokens, image)


# ---------------------------------------------------------------- public API used by routers
def chat(message: str, history: list, context: str) -> str:
    # "model" is Gemini's name for the assistant turn; accept it so older app builds keep working.
    messages = [{"role": "assistant" if t.role in ("assistant", "model") else "user", "content": t.text} for t in history]
    messages.append({"role": "user", "content": message})
    return _generate(messages, SAFETY_RULES + "\n\n" + CHAT_STYLE + "\n\n" + context, max_tokens=CHAT_MAX_TOKENS)


MEAL_SCHEMA = """Return JSON only, shaped like:
{"days":[{"day":1,"total_kcal":0,"meals":[{"name":"","meal_type":"breakfast|lunch|dinner|snack",
"kcal":0,"protein_g":0,"carbs_g":0,"fat_g":0,"ingredients":[""],"instructions":""}]}],
"notes":"","disclaimer":""}"""

WORKOUT_SCHEMA = """Return JSON only, shaped like:
{"days":[{"day":1,"focus":"","duration_min":0,"exercises":[{"name":"","type":"walking|running|cardio|strength|stretching",
"sets":0,"reps":"","duration_min":0,"instructions":""}]}],"notes":"","disclaimer":""}
Exercises must be doable at home with little or no equipment."""


def recommend(kind: str, days: int, extra: str | None, context: str) -> dict:
    schema = MEAL_SCHEMA if kind == "meal_plan" else WORKOUT_SCHEMA
    what = "meal plan" if kind == "meal_plan" else "home training plan"
    prompt = f"Create a {days}-day personalised {what} for this user."
    if extra:
        prompt += f" Extra request from the user: {extra}"
    raw = _generate([{"role": "user", "content": prompt}], SAFETY_RULES + "\n\n" + context + "\n\n" + schema, json_mode=True)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        log.warning("AI returned non-JSON: %.200s", raw)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "AI returned an unreadable plan, please try again")


ESTIMATE_SCHEMA = """Return JSON only, shaped like:
{"name":"","kcal":0,"protein_g":0,"carbs_g":0,"fat_g":0,"confidence":"low|medium|high","assumptions":""}
Estimate a typical serving using mainstream nutrition reference values (e.g. Australian Food Composition Database).
State the portion you assumed in "assumptions". Use integers for kcal and one decimal for grams."""


def estimate_meal(description: str, portion: str | None) -> dict:
    """Rough nutrition estimate for a described meal, so a user can log food without looking up numbers."""
    prompt = f"Estimate the nutrition of this meal: {description}."
    if portion:
        prompt += f" Portion: {portion}."
    raw = _generate([{"role": "user", "content": prompt}], SAFETY_RULES + "\n\n" + ESTIMATE_SCHEMA, json_mode=True)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        log.warning("AI returned non-JSON estimate: %.200s", raw)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "AI returned an unreadable estimate, please try again")


ANALYSE_SCHEMA = """Return JSON only, shaped like:
{"name":"","portion_assumption":"","kcal":0,"protein_g":0,"carbs_g":0,"fat_g":0,
 "confidence":"low|medium|high",
 "verdict":"good_to_go|smaller_portion|balance_it|enjoy_as_treat",
 "reason":"",
 "rest_of_day":"",
 "training_adjustment":{"summary":"","suggested_session":{"workout_type":"walking|running|cardio|strength|stretching|other","duration_min":0}}}
Rules:
- Identify the food in the photo (use the caption if given). Estimate ONE typical serving as shown, using
  mainstream reference values (e.g. Australian Food Composition Database). State the portion you assumed.
- "verdict" and "reason" must use the user's remaining calories for today, their goal and today's training from
  the context. Tone: coaching, never shaming. Food is not "bad"; the question is how it fits the day.
  good_to_go = fits comfortably; smaller_portion = fits if ~2/3 of it; balance_it = heavy for the remaining budget,
  suggest how to balance it (lighter next meal, a walk); enjoy_as_treat = occasional, enjoy it and move on.
- "rest_of_day": one sentence on what the next meal(s) today could look like given this meal (protein/veg focus,
  lighter dinner, etc.).
- "training_adjustment.summary": one sentence; if nothing needs to change say so. "suggested_session" is a single
  optional extra or modified session for today or tomorrow (null if not needed). Never suggest exercise as
  punishment, never more than 60 minutes extra, never below the calorie floor.
- Integers for kcal and duration, one decimal for grams. If the photo is not food, set name to "not food",
  kcal 0, confidence "low", verdict "good_to_go" and explain in reason."""

ANALYSE_MAX_TOKENS = 700


def analyse_meal(image_mime: str, image_b64: str, caption: str | None, context: str) -> dict:
    """Photo of a meal -> what it is, rough nutrition, and a personal verdict against today's budget and
    training (client request 19 Nov: camera feature with 'fine - here's how to balance it' guidance)."""
    prompt = "Analyse the meal in this photo." + (f" The user says: {caption}." if caption else "")
    raw = _generate(
        [{"role": "user", "content": prompt}],
        SAFETY_RULES + "\n\n" + ANALYSE_SCHEMA + "\n\n" + context,
        json_mode=True,
        max_tokens=ANALYSE_MAX_TOKENS,
        image=(image_mime, image_b64),
    )
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        log.warning("AI returned non-JSON meal analysis: %.200s", raw)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "AI returned an unreadable analysis, please try again")
