"""Automated tests for the History screen's two data sources:
GET /log/meals/history (food tab) and GET /log/workouts (workouts tab).

Run from the backend folder:  .venv\\Scripts\\python -m pytest -v
"""
from datetime import date, timedelta


def _day(offset: int) -> str:
    """Date string `offset` days before today, e.g. _day(1) = yesterday."""
    return (date.today() - timedelta(days=offset)).isoformat()


def _meal(client, auth, name, kcal, offset=0, meal_type="lunch"):
    r = client.post("/log/meals", headers=auth,
                    json={"name": name, "kcal": kcal, "meal_type": meal_type, "date": _day(offset)})
    assert r.status_code == 201, r.text


def _workout(client, auth, name, minutes, kcal, offset=0):
    r = client.post("/log/workouts", headers=auth,
                    json={"name": name, "duration_min": minutes, "kcal_burned": kcal,
                          "workout_type": "strength", "date": _day(offset)})
    assert r.status_code == 201, r.text


# ---------------------------------------------------------------- food history

def test_food_history_newest_first_with_totals(client, auth):
    _meal(client, auth, "Oats", 350, offset=2, meal_type="breakfast")
    _meal(client, auth, "Chicken wrap", 600, offset=0)
    _meal(client, auth, "Pasta", 750, offset=1, meal_type="dinner")
    _meal(client, auth, "Apple", 80, offset=0, meal_type="snack")

    r = client.get("/log/meals/history?days=7", headers=auth)
    assert r.status_code == 200
    body = r.json()

    dates = [e["date"] for e in body["entries"]]
    assert dates == sorted(dates, reverse=True), "entries must be newest day first"
    assert {e["name"] for e in body["entries"]} == {"Oats", "Chicken wrap", "Pasta", "Apple"}
    assert body["totals"] == {"meals": 4, "kcal": 1780, "days_logged": 3, "avg_kcal_per_logged_day": 593}
    assert body["days"] == 7


def test_food_history_only_includes_the_chosen_window(client, auth):
    _meal(client, auth, "Inside window", 500, offset=6)    # 7-day window = today + 6 days back
    _meal(client, auth, "Outside window", 900, offset=7)

    names = [e["name"] for e in client.get("/log/meals/history?days=7", headers=auth).json()["entries"]]
    assert names == ["Inside window"]

    names_30 = [e["name"] for e in client.get("/log/meals/history?days=30", headers=auth).json()["entries"]]
    assert set(names_30) == {"Inside window", "Outside window"}


def test_food_history_empty_for_new_user(client, auth):
    body = client.get("/log/meals/history", headers=auth).json()
    assert body["entries"] == []
    assert body["totals"] == {"meals": 0, "kcal": 0, "days_logged": 0, "avg_kcal_per_logged_day": 0}
    assert body["days"] == 30, "default window is 30 days"


def test_food_history_never_shows_another_users_meals(client, auth):
    _meal(client, auth, "Mine", 400)

    # second user logs a meal of their own
    client.post("/auth/register", json={"email": "other.person@test.com", "password": "Passw0rd!",
                                        "first_name": "Other", "last_name": "Person"})
    token = client.post("/auth/login", json={"email": "other.person@test.com", "password": "Passw0rd!"}).json()["access_token"]
    other = {"Authorization": f"Bearer {token}"}
    _meal(client, other, "Theirs", 999)

    mine = [e["name"] for e in client.get("/log/meals/history", headers=auth).json()["entries"]]
    theirs = [e["name"] for e in client.get("/log/meals/history", headers=other).json()["entries"]]
    assert mine == ["Mine"]
    assert theirs == ["Theirs"]


def test_food_history_requires_login(client):
    assert client.get("/log/meals/history").status_code in (401, 403)


def test_food_history_rejects_bad_day_counts(client, auth):
    assert client.get("/log/meals/history?days=0", headers=auth).status_code == 422
    assert client.get("/log/meals/history?days=366", headers=auth).status_code == 422


def test_existing_day_view_still_works(client, auth):
    """Adding /log/meals/history must not break GET /log/meals (the Food log tab)."""
    _meal(client, auth, "Toast", 250, meal_type="breakfast")
    r = client.get("/log/meals", headers=auth)
    assert r.status_code == 200
    assert [m["name"] for m in r.json()["meals"]] == ["Toast"]


# ---------------------------------------------------------------- workout history

def test_workout_history_newest_first_with_totals(client, auth):
    _workout(client, auth, "Leg day", 45, 300, offset=3)
    _workout(client, auth, "Push-ups circuit", 20, 150, offset=0)

    body = client.get("/log/workouts?days=7", headers=auth).json()
    assert [e["name"] for e in body["entries"]] == ["Push-ups circuit", "Leg day"]
    assert body["totals"] == {"sessions": 2, "minutes": 65, "kcal_burned": 450}


def test_workout_history_only_includes_the_chosen_window(client, auth):
    _workout(client, auth, "Recent", 30, 200, offset=6)
    _workout(client, auth, "Old", 30, 200, offset=7)
    names = [e["name"] for e in client.get("/log/workouts?days=7", headers=auth).json()["entries"]]
    assert names == ["Recent"]
