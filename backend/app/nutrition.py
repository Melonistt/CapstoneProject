"""Deterministic nutrition maths, so calorie targets don't depend on the AI model."""

ACTIVITY_FACTORS = {
    "sedentary": 1.2,
    "light": 1.375,
    "moderate": 1.55,
    "active": 1.725,
    "very_active": 1.9,
}
GOAL_ADJUST = {"weight_loss": -500, "muscle_gain": 300}
MIN_CALORIES = {"female": 1200, "male": 1500}


def daily_calorie_target(profile) -> int | None:
    """Mifflin-St Jeor BMR x activity factor, adjusted for goal, with a safe floor."""
    if profile is None:
        return None
    needed = (profile.age, profile.sex, profile.height_cm, profile.weight_kg, profile.activity_level)
    if any(v is None for v in needed):
        return None
    bmr = 10 * profile.weight_kg + 6.25 * profile.height_cm - 5 * profile.age
    bmr += 5 if profile.sex == "male" else -161
    tdee = bmr * ACTIVITY_FACTORS[profile.activity_level]
    target = tdee + GOAL_ADJUST.get(profile.goal or "", 0)
    return int(round(max(target, MIN_CALORIES[profile.sex]) / 10) * 10)


# Metabolic equivalents (Compendium of Physical Activities, Ainsworth et al. 2011), one typical value per
# workout type the app offers. kcal = MET x body weight (kg) x hours.
MET = {
    "walking": 3.5,      # brisk walk ~5 km/h
    "running": 9.8,      # ~9.5 km/h
    "cardio": 7.0,       # general aerobic / HIIT-style circuit
    "strength": 5.0,     # resistance training, vigorous effort
    "stretching": 2.5,   # stretching / yoga
    "other": 4.0,
}
DEFAULT_WEIGHT_KG = 75.0


def estimate_workout_kcal(workout_type: str, duration_min: int, weight_kg: float | None) -> int:
    """Rough energy cost of a session. An estimate, shown to the user as such."""
    met = MET.get(workout_type, MET["other"])
    kg = weight_kg or DEFAULT_WEIGHT_KG
    return int(round(met * kg * (duration_min / 60.0)))
