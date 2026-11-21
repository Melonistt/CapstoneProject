import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


def _now():
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(100))
    first_name: Mapped[str] = mapped_column(String(100))
    last_name: Mapped[str] = mapped_column(String(100))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    # Lockout after 5 failed logins for 15 minutes (report NFR: Security & Privacy)
    failed_logins: Mapped[int] = mapped_column(Integer, default=0)
    locked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class Profile(Base):
    """Wellness profile the AI draws on (report FR: Build/Maintain a User Profile)."""

    __tablename__ = "profiles"

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), primary_key=True)
    age: Mapped[int | None] = mapped_column(Integer, nullable=True)
    sex: Mapped[str | None] = mapped_column(String(10), nullable=True)  # "male" / "female"
    height_cm: Mapped[float | None] = mapped_column(Float, nullable=True)
    weight_kg: Mapped[float | None] = mapped_column(Float, nullable=True)
    activity_level: Mapped[str | None] = mapped_column(String(20), nullable=True)
    goal: Mapped[str | None] = mapped_column(String(30), nullable=True)
    dietary_preferences: Mapped[str | None] = mapped_column(Text, nullable=True)
    allergies: Mapped[str | None] = mapped_column(Text, nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now, onupdate=_now)


class MealLog(Base):
    """One logged meal (report FR: Log Food Intake). Nutrition values come from the user, an AI estimate,
    or an AI meal plan; `source` records which so the dashboard can say where its numbers came from."""

    __tablename__ = "meal_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), index=True)
    date: Mapped[str] = mapped_column(String(10), index=True)  # YYYY-MM-DD, user's local day
    meal_type: Mapped[str] = mapped_column(String(10))  # breakfast / lunch / dinner / snack
    name: Mapped[str] = mapped_column(String(200))
    kcal: Mapped[int] = mapped_column(Integer)
    protein_g: Mapped[float | None] = mapped_column(Float, nullable=True)
    carbs_g: Mapped[float | None] = mapped_column(Float, nullable=True)
    fat_g: Mapped[float | None] = mapped_column(Float, nullable=True)
    source: Mapped[str] = mapped_column(String(10), default="manual")  # manual / ai_estimate / ai_plan / photo
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class WeightLog(Base):
    """A weigh-in (report FR: Track Progress Towards Goals)."""

    __tablename__ = "weight_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), index=True)
    date: Mapped[str] = mapped_column(String(10), index=True)  # YYYY-MM-DD
    weight_kg: Mapped[float] = mapped_column(Float)
    notes: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class WorkoutLog(Base):
    """A completed training session (client request 19 Nov: meals, workouts and the plan should be
    "intertwined"). Logged from a plan day ("Mark as done") or by hand; kcal_burned is a MET estimate
    unless the user typed a value."""

    __tablename__ = "workout_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), index=True)
    date: Mapped[str] = mapped_column(String(10), index=True)  # YYYY-MM-DD
    name: Mapped[str] = mapped_column(String(200))  # e.g. "Day 1 - Full Body Strength" or "Evening walk"
    workout_type: Mapped[str] = mapped_column(String(12))  # walking / running / cardio / strength / stretching / other
    duration_min: Mapped[int] = mapped_column(Integer)
    kcal_burned: Mapped[int] = mapped_column(Integer)
    source: Mapped[str] = mapped_column(String(10), default="manual")  # manual / ai_plan
    plan_day: Mapped[int | None] = mapped_column(Integer, nullable=True)  # which day of the AI plan, if any
    notes: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
