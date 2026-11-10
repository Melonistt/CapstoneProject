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
    role: Literal["user", "assistant"]
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
