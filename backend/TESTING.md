# Backend API — test log

## Auth & profile endpoint tests — 9 Nov 2026 (Dylan)
Backend run locally (`python run.py`), SQLite, no GEMINI_API_KEY set. Requests sent exactly as the Android Login/Sign Up screens send them.

| # | Test | Request | Result | Status |
|---|---|---|---|---|
| 1 | Register new user | POST /auth/register test@example.com / Passw0rd! | user id + names returned | 201 ✅ |
| 2 | Register duplicate email | same as 1 | "Email already registered" | 409 ✅ |
| 3 | Register weak password | password = "password" | "Password must contain at least one number" | 422 ✅ |
| 4 | Login correct password | POST /auth/login | JWT bearer token (165 chars) | 200 ✅ |
| 5 | Current user with token | GET /auth/me | same user as test 1 | 200 ✅ |
| 6 | Login wrong password | POST /auth/login | "Incorrect email or password"; failed attempt logged with timestamp + IP | 401 ✅ |
| 7 | 5th consecutive failure, then correct password | POST /auth/login ×5 | "Account locked. Try again in 15 minutes." | 423 ✅ (NFR: lock after 5) |
| 8 | Save profile | PUT /profile age 28, male, 180 cm, 90 kg, moderate, weight_loss, allergy peanuts | profile echoed with daily_calorie_target = 2430 kcal | 200 ✅ |
| 9 | AI chat with no API key | POST /ai/chat | "AI service not configured (GEMINI_API_KEY missing)" — clean failure, no crash | 503 ✅ |

Still to test: /ai/chat and /ai/recommend with a real GEMINI_API_KEY (response time for NFR ≤ 5 s; allergen never present in meal plan); end-to-end from the Android emulator.

## AI coach endpoint tests — 10 Nov 2026 (Dylan)
Backend run from PyCharm on Windows (`python run.py`), provider **Gemini** via REST, model `gemini-3.5-flash-lite`. Requests sent from the browser (same shape the Android app will send). Test profile: 28 M, 180 cm, 90 kg, moderate activity, goal weight_loss, allergy **peanuts**, preference **no pork**.

| # | Test | Request | Result | Time | Status |
|---|---|---|---|---|---|
| 10 | Model discovery | GET /ai/models | 47 models usable with this key; active model reported | — | 200 ✅ |
| 11 | Chat, simple | "Give me a 15 minute home workout" + profile | 40 s on / 20 s off bodyweight circuit, 3 rounds, incline push-ups etc. | 2.6 s | 200 ✅ |
| 12 | Chat, with history | "Make it easier on my knees" + previous turn | Rewrote circuit with low-impact swaps (glute bridges, good mornings); kept format | 2.6 s | 200 ✅ |
| 13 | Chat, safety rule | "I have had chest pain when I run… what should I take?" | Declined to suggest medication/diagnosis; see GP, pause running, call 000 if severe | 1.5 s | 200 ✅ |
| 14 | Meal plan, JSON mode | kind=meal_plan, days=2, "more vegetarian options" | Valid JSON: 2 days × 4 meals, 2250/2300 kcal vs 2430 target, vegetarian, **no peanuts, no pork** (only mentions are "non-peanut variety" / "peanut-free"), disclaimer present | 4.4 s | 200 ✅ |
| 15 | Workout plan, JSON mode | kind=workout_plan, days=3, "no equipment at all" | Valid JSON: 3 days × 30 min (Full body / Cardio & core / Lower body & mobility), bodyweight only, disclaimer present | 2.7 s | 200 ✅ |

**NFR check:** all AI responses ≤ 5 s (target from Planning Report); chat 1.5–2.6 s, plans 2.7–4.4 s.

**Issues found and fixed during this session**
- `google-genai` SDK raised `RuntimeError: Cannot send a request, as the client has been closed` on every call on Windows → replaced SDK with direct REST calls (standard library); dependency removed.
- `gemini-2.5-flash` returns 404 "no longer available to new users" → model made configurable, default updated.
- `gemini-3.8-flash` hangs past the 60 s timeout even on a one-word prompt (thinking model) → use `gemini-3.5-flash-lite` for the coach.
- `.env` edits are not picked up by uvicorn's reloader (it watches `app/` only) → restart the run configuration after changing `.env`.
- Groq added as an alternative provider (`GROQ_API_KEY`), auto-selected when set; untested so far (no key obtained).
