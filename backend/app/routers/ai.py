from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from .. import ai_service
from ..database import get_db
from ..models import Profile, User
from ..nutrition import daily_calorie_target
from ..schemas import AnalyseMealOut, AnalyseMealRequest, ChatRequest, ChatResponse, EstimateOut, EstimateRequest, ProfileIn, RecommendRequest
from ..security import get_current_user, get_optional_user
from .log import _today, day_view, summary, workout_history

router = APIRouter(prefix="/ai", tags=["ai"])


def _context(body_profile: ProfileIn | None, user: User | None, db: Session) -> str:
    """Profile sent in the request wins (app keeps it on-device); else the logged-in user's server profile.
    Logged-in users also get today's meals and workouts, so answers reflect what they have actually done."""
    if body_profile is not None:
        p = Profile(**body_profile.model_dump())
    elif user is not None:
        p = db.get(Profile, user.id)
    else:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Send a profile in the request or log in first")
    ctx = ai_service.profile_context(p, daily_calorie_target(p) if p else None)
    if user is not None:
        ctx += "\n" + ai_service.today_context(day_view(user, db, _today()))
        ctx += "\n" + ai_service.week_context(summary(days=7, user=user, db=db), workout_history(days=7, user=user, db=db))
    return ctx


@router.get("/models")
def models():
    """Which models the configured provider/key can use, and which one is active."""
    return {"provider": ai_service.provider(), "active_model": ai_service.model_name(), "models": ai_service.list_models()}


@router.post("/chat", response_model=ChatResponse)
def chat(body: ChatRequest, user: User | None = Depends(get_optional_user), db: Session = Depends(get_db)):
    reply = ai_service.chat(body.message, body.history, _context(body.profile, user, db))
    return ChatResponse(reply=reply)


@router.post("/recommend")
def recommend(body: RecommendRequest, user: User | None = Depends(get_optional_user), db: Session = Depends(get_db)):
    return ai_service.recommend(body.kind, body.days, body.request, _context(body.profile, user, db))


@router.post("/analyse-meal", response_model=AnalyseMealOut)
def analyse_meal(body: AnalyseMealRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Photo of a meal -> identification, nutrition estimate and a coaching verdict ('fine - here's how to
    balance it') against the user's remaining calories, goal and training today. Login required because the
    verdict depends on the user's day. The result is an estimate the user confirms before logging (source=photo)."""
    data = ai_service.analyse_meal(body.mime_type, body.image_base64, body.caption, _context(None, user, db))
    # Tolerate a null/empty suggested_session from the model.
    ta = data.get("training_adjustment") or {}
    if not ta.get("suggested_session") or not ta["suggested_session"].get("duration_min"):
        ta["suggested_session"] = None
    data["training_adjustment"] = ta or {"summary": "No change needed."}
    return AnalyseMealOut(**data)


@router.post("/estimate", response_model=EstimateOut)
def estimate(body: EstimateRequest, user: User | None = Depends(get_optional_user)):
    """Estimate kcal and macros for a described meal (the food log's 'fill in for me' button).
    The result is an approximation the user should confirm or edit before saving (report risk R2)."""
    data = ai_service.estimate_meal(body.description, body.portion)
    return EstimateOut(**data)
