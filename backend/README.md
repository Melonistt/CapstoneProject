# Backend API (FastAPI + Gemini)

The server the Android app calls for the AI coach. Python 3.12.

## Run it

1. Open the `backend` folder in PyCharm (it picks up `.venv` automatically), or in a terminal:
   `py -m venv .venv` then `.venv\Scripts\activate.bat` then `pip install -r requirements.txt`
2. Copy `.env.example` to `.env`. Set `JWT_SECRET` to any long random string and `GEMINI_API_KEY` to a key from https://aistudio.google.com (free tier).
3. Run `run.py` (green arrow in PyCharm, or `python run.py`).
4. Open http://localhost:8000/docs to try every endpoint in the browser.

From the Android emulator the server address is `http://10.0.2.2:8000`.

## Endpoints

| Method | Path | What it does |
| --- | --- | --- |
| POST | `/auth/register` | Create an account (8+ chars, 1 number, 1 special character) |
| POST | `/auth/login` | Returns a bearer token valid 7 days; 5 wrong passwords locks for 15 min |
| GET | `/auth/me` | The logged-in user |
| GET / PUT | `/profile` | Age, sex, height, weight, activity, goal, diet, allergies; returns a daily calorie target |
| POST | `/ai/chat` | Conversational coach. Body: `message`, `history[]`, optional `profile` |
| POST | `/ai/recommend` | `kind`: `meal_plan` or `workout_plan`, `days` 1–7, optional `request`, optional `profile` |
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

## Safety rules built into every AI call

No medical diagnoses or clinical advice; evidence-based guidance only; never includes a listed allergen; no crash diets or unsafe exercise. See `app/ai_service.py`.

## Layout

```
app/main.py          FastAPI app, routers, table creation
app/routers/auth.py  register / login / me
app/routers/profile.py
app/routers/ai.py    chat / recommend
app/ai_service.py    Gemini wrapper + safety prompt
app/nutrition.py     calorie target (Mifflin-St Jeor)
app/models.py        SQLAlchemy tables (SQLite file wellness.db; set DATABASE_URL for PostgreSQL)
```
