"""Thin wrapper around Google Gemini. Swap provider here without touching the routes."""
import json
import logging
import os

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


def _client():
    key = os.getenv("GEMINI_API_KEY")
    if not key:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "AI service not configured (GEMINI_API_KEY missing)")
    from google import genai

    return genai.Client(api_key=key)


def _model() -> str:
    return os.getenv("GEMINI_MODEL", "gemini-2.5-flash")


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


def _generate(contents, system: str, json_mode: bool = False) -> str:
    from google.genai import types

    config = types.GenerateContentConfig(
        system_instruction=system,
        temperature=0.7,
        response_mime_type="application/json" if json_mode else None,
    )
    try:
        resp = _client().models.generate_content(model=_model(), contents=contents, config=config)
    except HTTPException:
        raise
    except Exception as exc:  # network, quota, bad key, etc. (report risk R4)
        log.exception("Gemini call failed")
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"AI service error: {type(exc).__name__}") from exc
    return (resp.text or "").strip()


def chat(message: str, history: list, context: str) -> str:
    contents = [
        {"role": "model" if t.role == "assistant" else "user", "parts": [{"text": t.text}]} for t in history
    ]
    contents.append({"role": "user", "parts": [{"text": message}]})
    return _generate(contents, SAFETY_RULES + "\n\n" + context)


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
    raw = _generate(prompt, SAFETY_RULES + "\n\n" + context + "\n\n" + schema, json_mode=True)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        log.warning("AI returned non-JSON: %.200s", raw)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "AI returned an unreadable plan, please try again")
