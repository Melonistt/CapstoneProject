# Backend API (FastAPI + Groq / Gemini)

The server the Android app calls for the AI coach. Python 3.12.

## Run it

1. Open the `backend` folder in PyCharm (it picks up `.venv` automatically), or in a terminal:
   `py -m venv .venv` then `.venv\Scripts\activate.bat` then `pip install -r requirements.txt`
2. Copy `.env.example` to `.env`. Set `JWT_SECRET` to any long random string and ONE AI key: `GROQ_API_KEY` from https://console.groq.com/keys (free, no card) or `GEMINI_API_KEY` from https://aistudio.google.com. Groq is used automatically when its key is set.
3. Run `run.py` (green arrow in PyCharm, or `python run.py`).
4. Open http://localhost:8000/docs to try every endpoint in the browser.

## Automated tests

`tests/` holds pytest tests (currently the History endpoints). They use their own throwaway database, so
`wellness.db` is never touched. From the `backend` folder:

```
.venv\Scripts\python -m pip install -r requirements-dev.txt
.venv\Scripts\python -m pytest -v
```

## Connecting the Android app

The app's `ApiClient.BASE_URL` is `http://127.0.0.1:8000/`. On the emulator, open a tunnel once per emulator boot
(Android Studio → Terminal, PowerShell):

```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```

Check with `... reverse --list` → `host-N tcp:8000 tcp:8000`. This routes the emulator's port 8000 to the PC over the
debug connection, so it works even when the emulator's own network is broken (VPN clients and Android Studio's
"Auto-detect proxy" setting both caused that on a team laptop). `run.py` binds `0.0.0.0`, so a physical phone on the
same Wi-Fi can use the PC's LAN IP instead — add it to `app/src/main/res/xml/network_security_config.xml`.

## Endpoints

| Method | Path | What it does |
| --- | --- | --- |
| POST | `/auth/register` | Create an account (8+ chars, 1 number, 1 special character) |
| POST | `/auth/login` | Returns a bearer token valid 7 days; 5 wrong passwords locks for 15 min |
| GET | `/auth/me` | The logged-in user |
| GET / PUT | `/profile` | Age, sex, height, weight, activity, goal, diet, allergies; returns a daily calorie target |
| POST | `/ai/chat` | Conversational coach. Body: `message`, `history[]`, optional `profile` |
| POST | `/ai/recommend` | `kind`: `meal_plan` or `workout_plan`, `days` 1–7, optional `request`, optional `profile` |
| POST | `/ai/estimate` | kcal + macros for a described meal (`description`, optional `portion`) — the food log's "fill in for me" |
| GET | `/ai/models` | Models the configured key can use, and which is active |
| POST / GET | `/log/meals` | Log a meal; get one day's meals **and workouts** with totals, calories burned, net kcal, calorie target and calories remaining (`?date=YYYY-MM-DD`) |
| GET | `/log/meals/history` | Food history: meals in the last `days` (default 30, max 365), newest first, with totals (meals, kcal, days logged, average kcal per logged day) — History screen, Food tab |
| DELETE | `/log/meals/{id}` | Remove a logged meal |
| GET | `/log/summary` | Calories eaten and burned per day for the last `days` (default 7), zero-filled — dashboard / weekly chart |
| POST / GET | `/log/workouts` | Record a completed session (kcal estimated from MET × weight × time if not given); history for the last `days` (default 7) with totals — Training history / Progress |
| DELETE | `/log/workouts/{id}` | Remove a logged workout |
| POST / GET | `/log/weight` | Record a weigh-in (also updates the profile weight); history with start/latest/change (`?days=90`) — Progress chart |
| DELETE | `/log/weight/{id}` | Remove a weigh-in |
| GET | `/health` | Liveness check |

The AI endpoints take the profile two ways: send a `profile` object in the request (for the app, which keeps the profile in its on-device Room database), or send a bearer token and the server uses that user's `/profile`.

Example `/ai/chat` body:

```json
{
  "message": "Give me a 15 minute home workout",
  "history": [],
  "profile": {"age": 28, "sex": "male", "height_cm": 180, "weight_kg": 90,
              "activity_level": "moderate", "goal": "weight_loss", "allergies": "peanuts"}
}
```

## Where nutrition numbers come from

Every logged meal carries a `source`: `manual` (typed by the user), `ai_estimate` (from `/ai/estimate`, which the user confirms or edits before saving), `ai_plan` (copied from an `/ai/recommend` meal plan) or `photo` (reserved for image analysis). Daily calorie targets are computed deterministically in `nutrition.py` (Mifflin-St Jeor), not by the AI.

Workouts are logged the same way (`source` = `manual` or `ai_plan` when ticked off from an AI plan). If the user does not type calories burned, `nutrition.py` estimates them as MET × body weight (kg) × hours using one typical MET per workout type from the Compendium of Physical Activities (walking 3.5, running 9.8, cardio 7.0, strength 5.0, stretching 2.5). `GET /log/meals` returns meals and workouts together with `calories_remaining = target − eaten + burned` (the usual "exercise earns calories back" convention), and the AI coach receives a one-line summary of today's meals and workouts with every request so its answers reflect what the user has actually done.

## Safety rules built into every AI call

No medical diagnoses or clinical advice; evidence-based guidance only; never includes a listed allergen; no crash diets or unsafe exercise. See `app/ai_service.py`.

## Layout

```
app/main.py          FastAPI app, routers, table creation
app/routers/auth.py  register / login / me
app/routers/profile.py
app/routers/ai.py    chat / recommend / estimate / models
app/routers/log.py   meal log, daily totals, weekly summary, weigh-ins
app/ai_service.py    Groq/Gemini wrapper + safety prompt (provider chosen from .env)
app/nutrition.py     calorie target (Mifflin-St Jeor), workout kcal estimate (MET)
app/models.py        SQLAlchemy tables: users, profiles, meal_logs, weight_logs (SQLite file wellness.db; set DATABASE_URL for PostgreSQL)
```
