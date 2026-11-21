import re

from pydantic import BaseModel, EmailStr, field_validator


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str
    first_name: str
    last_name: str

    @field_validator("password")
    @classmethod
    def password_policy(cls, v: str) -> str:
        # Report FR: min 8 chars, at least 1 number and 1 special character
        if len(v) < 8:
            raise ValueError("Password must be at least 8 characters")
        if not re.search(r"\d", v):
            raise ValueError("Password must contain at least one number")
        if not re.search(r"[^A-Za-z0-9]", v):
            raise ValueError("Password must contain at least one special character")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    id: str
    email: EmailStr
    first_name: str
    last_name: str

    model_config = {"from_attributes": True}


# ---------- Profile ----------
from typing import Literal  # noqa: E402

from pydantic import Field  # noqa: E402

ActivityLevel = Literal["sedentary", "light", "moderate", "active", "very_active"]
Goal = Literal["weight_loss", "maintain", "muscle_gain", "fitness", "stress_management"]


class ProfileIn(BaseModel):
    age: int | None = Field(None, ge=13, le=100)
    sex: Literal["male", "female"] | None = None
    height_cm: float | None = Field(None, ge=100, le=250)
    weight_kg: float | None = Field(None, ge=30, le=300)
    activity_level: ActivityLevel | None = None
    goal: Goal | None = None
    dietary_preferences: str | None = Field(None, max_length=500)
    allergies: str | None = Field(None, max_length=500)


class ProfileOut(ProfileIn):
    daily_calorie_target: int | None = None

    model_config = {"from_attributes": True}


# ---------- AI ----------
class ChatTurn(BaseModel):
    role: Literal["user", "assistant", "model"]   # "model" = Gemini's name for assistant; normalised in ai_service
    text: str = Field(..., max_length=4000)


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=2000)
    history: list[ChatTurn] = Field(default_factory=list, max_length=20)
    profile: ProfileIn | None = Field(None, description="Send the user's profile here if the app keeps it on-device")


class ChatResponse(BaseModel):
    reply: str


class RecommendRequest(BaseModel):
    kind: Literal["meal_plan", "workout_plan"]
    request: str | None = Field(None, max_length=500, description='Optional extra ask, e.g. "more vegan recipes"')
    days: int = Field(1, ge=1, le=7)
    profile: ProfileIn | None = Field(None, description="Send the user's profile here if the app keeps it on-device")


# ---------- Food log & progress ----------
import re as _re  # noqa: E402

MealType = Literal["breakfast", "lunch", "dinner", "snack"]
LogSource = Literal["manual", "ai_estimate", "ai_plan", "photo"]
WorkoutType = Literal["walking", "running", "cardio", "strength", "stretching", "other"]
_DATE = _re.compile(r"^\d{4}-\d{2}-\d{2}$")


def _check_date(v: str | None) -> str | None:
    if v is not None and not _DATE.match(v):
        raise ValueError("date must be YYYY-MM-DD")
    return v


class MealLogIn(BaseModel):
    date: str | None = Field(None, description="YYYY-MM-DD; defaults to today (server date)")
    meal_type: MealType
    name: str = Field(..., min_length=1, max_length=200)
    kcal: int = Field(..., ge=0, le=5000)
    protein_g: float | None = Field(None, ge=0, le=500)
    carbs_g: float | None = Field(None, ge=0, le=1000)
    fat_g: float | None = Field(None, ge=0, le=500)
    source: LogSource = "manual"

    _d = field_validator("date")(classmethod(lambda cls, v: _check_date(v)))


class MealLogOut(MealLogIn):
    id: str
    date: str

    model_config = {"from_attributes": True}


class DayTotals(BaseModel):
    kcal: int
    protein_g: float
    carbs_g: float
    fat_g: float


class WorkoutLogIn(BaseModel):
    date: str | None = Field(None, description="YYYY-MM-DD; defaults to today")
    name: str = Field(..., min_length=1, max_length=200)
    workout_type: WorkoutType = "other"
    duration_min: int = Field(..., ge=1, le=600)
    kcal_burned: int | None = Field(None, ge=0, le=5000, description="Leave empty to get a MET-based estimate")
    source: Literal["manual", "ai_plan"] = "manual"
    plan_day: int | None = Field(None, ge=1, le=14)
    notes: str | None = Field(None, max_length=300)

    _d = field_validator("date")(classmethod(lambda cls, v: _check_date(v)))


class WorkoutLogOut(WorkoutLogIn):
    id: str
    date: str
    kcal_burned: int

    model_config = {"from_attributes": True}


class WorkoutTotals(BaseModel):
    sessions: int
    minutes: int
    kcal_burned: int


class WorkoutHistoryOut(BaseModel):
    entries: list[WorkoutLogOut]   # newest first
    totals: WorkoutTotals
    days: int


class MealHistoryTotals(BaseModel):
    meals: int
    kcal: int
    days_logged: int               # days with at least one meal
    avg_kcal_per_logged_day: int   # kcal / days_logged; days with nothing logged are left out


class MealHistoryOut(BaseModel):
    """Food history for the History screen. Same shape as WorkoutHistoryOut so the app treats both alike."""
    entries: list[MealLogOut]      # newest first
    totals: MealHistoryTotals
    days: int


class DayLogOut(BaseModel):
    """One day, food and training together (the dashboard's single source of truth).
    calories_remaining = target - eaten + burned, the usual 'exercise earns calories back' convention."""

    date: str
    meals: list[MealLogOut]
    totals: DayTotals
    workouts: list[WorkoutLogOut]
    calories_burned: int
    net_kcal: int                   # eaten - burned
    calorie_target: int | None
    calories_remaining: int | None


class DaySummary(BaseModel):
    date: str
    kcal: int
    meals: int
    kcal_burned: int = 0
    workouts: int = 0


class WeightLogIn(BaseModel):
    date: str | None = Field(None, description="YYYY-MM-DD; defaults to today")
    weight_kg: float = Field(..., ge=30, le=300)
    notes: str | None = Field(None, max_length=300)

    _d = field_validator("date")(classmethod(lambda cls, v: _check_date(v)))


class WeightLogOut(WeightLogIn):
    id: str
    date: str

    model_config = {"from_attributes": True}


class WeightHistoryOut(BaseModel):
    entries: list[WeightLogOut]
    start_kg: float | None
    latest_kg: float | None
    change_kg: float | None


class EstimateRequest(BaseModel):
    description: str = Field(..., min_length=2, max_length=500, description='e.g. "large chicken caesar salad"')
    portion: str | None = Field(None, max_length=100, description='optional, e.g. "1 bowl", "250 g"')


class AnalyseMealRequest(BaseModel):
    image_base64: str = Field(..., min_length=100, max_length=6_000_000, description="JPEG/PNG, base64 (app resizes to ~1024 px)")
    mime_type: Literal["image/jpeg", "image/png", "image/webp"] = "image/jpeg"
    caption: str | None = Field(None, max_length=200, description='optional, e.g. "my lunch, large bowl"')


class SuggestedSession(BaseModel):
    workout_type: WorkoutType = "walking"
    duration_min: int = Field(..., ge=5, le=60)


class TrainingAdjustment(BaseModel):
    summary: str
    suggested_session: SuggestedSession | None = None


class AnalyseMealOut(BaseModel):
    name: str
    portion_assumption: str = ""
    kcal: int
    protein_g: float
    carbs_g: float
    fat_g: float
    confidence: Literal["low", "medium", "high"]
    verdict: Literal["good_to_go", "smaller_portion", "balance_it", "enjoy_as_treat"]
    reason: str
    rest_of_day: str = ""
    training_adjustment: TrainingAdjustment
    source: str = "photo"


class EstimateOut(BaseModel):
    name: str
    kcal: int
    protein_g: float
    carbs_g: float
    fat_g: float
    confidence: Literal["low", "medium", "high"]
    assumptions: str
    source: str = "ai_estimate"
