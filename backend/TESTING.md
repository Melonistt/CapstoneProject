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

## Food log & progress endpoint tests — 15 Nov 2026 (Dylan)
Fresh SQLite database, no AI key. Profile: 27 M, 173 cm, 80 kg, light, muscle_gain → target 2710 kcal.

| # | Test | Request | Result | Status |
|---|---|---|---|---|
| 16 | Log a meal | POST /log/meals breakfast "Oats with banana" 420 kcal | entry returned with id, date defaulted to today, source manual | 201 ✅ |
| 17 | Log an AI-estimated meal | POST /log/meals lunch 650 kcal source=ai_estimate | stored with source recorded | 201 ✅ |
| 18 | Invalid meal type | meal_type "brunch" | "Input should be breakfast, lunch, dinner or snack" | 422 ✅ |
| 19 | Day view | GET /log/meals | 2 meals, totals 1070 kcal / 59 g protein, target 2710, remaining 1640 | 200 ✅ |
| 20 | Weigh-ins | POST /log/weight ×3 (82.0 on 1 Nov, 81.2 on 8 Nov, 80.6 today) | all stored | 201 ✅ |
| 21 | Weight history | GET /log/weight?days=30 | 3 entries oldest-first, start 82.0, latest 80.6, change −1.4 kg | 200 ✅ |
| 22 | Profile follows weigh-in | GET /profile | weight 80.6, calorie target recalculated to 2720 | 200 ✅ |
| 23 | Weekly summary | GET /log/summary?days=7 | 7 days oldest-first, zero-filled, today 1070 kcal | 200 ✅ |
| 24 | Delete own meal / unauthenticated read | DELETE /log/meals/{id}; GET /log/meals with no token | 204; 401 | ✅ |
| 25 | Estimate with no AI key | POST /ai/estimate | clean 503 "AI service not configured" | ✅ |

| 26 | AI estimate, specific | POST /ai/estimate "large chicken caesar salad" (Gemini, Dylan's laptop) | 580 kcal, 42 g protein, 18 g carbs, 38 g fat, confidence high, portion assumption stated | 1.4 s, 200 ✅ |
| 27 | AI estimate, with portion | "oats with banana and honey", portion "1 bowl" | 380 kcal, 9.5 / 76 / 4.2 g, confidence high. First attempt hit a Gemini "high demand" 503 → returned as clean 502 with the vendor message; retry succeeded | 0.9 s, 200 ✅ |
| 28 | AI estimate, vague | "some food" | generic 450 kcal estimate, confidence **low** — app should prompt the user to refine | 1.0 s, 200 ✅ |

Known risk: Gemini free tier occasionally returns 503 "high demand"; the app should offer a retry on 502 responses.


## App ↔ backend on the emulator — 15 Nov 2026 (Dylan)
Android Studio, "Medium Phone API 37" emulator, backend in PyCharm on the same laptop, `adb reverse tcp:8000 tcp:8000`.

| # | Test | Result | Status |
|---|---|---|---|
| 29 | Sign Up from the app (DylanDemo / Dylan_Demo@hotmail.com) | "Account created! Please log in."; backend `POST /auth/register 201` | ✅ |
| 30 | Log in from the app | `[TODO: confirm]` "Welcome back!" → Home; `POST /auth/login 200` | |
| 31 | Wrong password from the app | `[TODO: confirm]` red "Incorrect email or password" under the field | |

**Network troubleshooting log (for the user manual / Challenges):** the emulator could not reach the host at `10.0.2.2` and could not load any website — `SocketTimeoutException` after 15 s. Not fixed by: binding the backend to `0.0.0.0`, quitting NordVPN, turning Windows Firewall off, cold-booting the emulator. Root cause was that Android Studio's HTTP proxy was on **Auto-detect**, which handed the emulator a dead system proxy; even after switching to No proxy the guest network stayed down. Resolution: point the app at `127.0.0.1` and tunnel with `adb reverse` — independent of guest networking. Also found and fixed during this: the OkHttp logging interceptor at `Level.BODY` was printing the sign-up password to Logcat (now `BASIC`).


## Workout log endpoint tests — 19 Nov 2026 (Dylan)
Run against a scratch SQLite DB with a fresh user (profile: 27 M, 180 cm, 85 kg, moderate, weight loss → target 2360). Client request 19 Nov: meals, workouts and plans "intertwined".

| # | Test | Result | Status |
|---|---|---|---|
| 32 | `POST /log/workouts` from a plan day, no kcal given (strength, 30 min, source `ai_plan`, plan_day 1) | 201; `kcal_burned` 212 = 5.0 MET × 85 kg × 0.5 h | ✅ |
| 33 | `POST /log/workouts` manual with `kcal_burned: 150` (walking, 40 min) | 201; user's value kept (150), not overwritten by the estimate | ✅ |
| 34 | `POST /log/workouts` with `workout_type: "swimming"` | 422 (not in the allowed list) | ✅ |
| 35 | `GET /log/meals` after one 650 kcal meal + the two workouts | 200; `calories_burned` 362, `net_kcal` 288, `calories_remaining` 2072 (= 2360 − 650 + 362); `workouts` has 2 entries | ✅ |
| 36 | `GET /log/workouts?days=7` | 200; newest first; totals sessions 2, minutes 70, kcal_burned 362 | ✅ |
| 37 | `GET /log/summary?days=2` | 200; today's row has `kcal_burned` 362 and `workouts` 2 alongside meals | ✅ |
| 38 | `DELETE /log/workouts/{id}` twice | 204 then 404 | ✅ |
| 39 | `GET /log/workouts` without a token | 401 | ✅ |
| 40 | AI coach context for a logged-in user | includes "Today so far: eaten 650 kcal [Chicken wrap]; workouts: Day 1 - Full Body Strength (30 min, ~212 kcal); 1922 kcal remaining" | ✅ |
| 41 | `POST /ai/chat` follow-up with history role `model` (the 422 reported 19 Nov) | 200 with the fix; replies now 50–90 words | ✅ |
| 42 | AI coach context for a logged-in user includes the last 7 days | "Last 7 days: average N kcal eaten per day over D logged day(s); S workout(s), M min, ~K kcal burned [dated list]" — coach answered "Did I train enough this week?" with "4 workouts totaling 150 minutes over the last 7 days" | ✅ |

## Meal photo analysis — 19 Nov 2026 evening (Dylan / Claude)
`POST /ai/analyse-meal`, logged in as `sam@d.com` (650 kcal eaten today, 1350 left, 4 workouts this week). Test image: NCI "Good Food Display" (public domain), resized to 1024×683 JPEG (185 KB) in the browser — the same resize the app does.

| # | Test | Result | Status |
|---|---|---|---|
| 43 | Photo + caption "dinner" | 200 in 3.5 s. name "Assorted whole foods spread (turkey, fish, vegetables and grains)", 850 kcal / 65 P / 75 C / 32 F, medium confidence, verdict `balance_it`, reason cites "leaving you with 500 kcal … 2000 kcal weight-loss target", training: "No changes are needed" | ✅ |
| 44 | Same photo, caption "I'm going to eat all of this myself tonight, big birthday dinner" | 2800 kcal, verdict `enjoy_as_treat`, reason coaching tone ("Happy birthday… enjoy a sensible portion"), training summary "No extra training is needed to compensate… return to your normal routine tomorrow", optional suggested session walking 30 min | ✅ |
| 45 | Non-food image (emulator virtual-camera scene) via the app's Take photo | name "not food", 0 kcal, low confidence, reason "This image doesn't appear to show any food, so we can't log it" | ✅ |
| 46 | Camera permission flow on device | System prompt shown on first tap; "While using the app" → camera opens; capture → confirm → analysis | ✅ |
| 47 | `analyse-meal` without a token | 401 (login required: the verdict depends on the user's day) | ✅ (by design, Depends(get_current_user)) |

## Automated tests — 21 Nov 2026
| Suite | Where | Result |
|---|---|---|
| Backend pytest (Ali): `/log/meals/history` and `/log/workouts` — ordering, totals, date window, empty user, auth | `backend/tests/test_history.py` (run: `.venv\Scripts\python -m pytest -v`) | 9 passed |
| Android JUnit (Jacqueline): `AuthValidator` login / sign-up / name-split rules | `app/src/test/.../AuthValidatorTest.kt` (run from Android Studio gutter or `gradlew testDebugUnitTest`) | 17 passed (57 s), screenshot in `docs/screenshots/2026-11-21-integration/` |
