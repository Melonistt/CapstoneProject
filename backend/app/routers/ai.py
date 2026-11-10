from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from .. import ai_service
from ..database import get_db
from ..models import Profile, User
from ..nutrition import daily_calorie_target
from ..schemas import ChatRequest, ChatResponse, ProfileIn, RecommendRequest
from ..security import get_optional_user

router = APIRouter(prefix="/ai", tags=["ai"])


def _context(body_profile: ProfileIn | None, user: User | None, db: Session) -> str:
    """Profile sent in the request wins (app keeps it on-device); else the logged-in user's server profile."""
    if body_profile is not None:
        p = Profile(**body_profile.model_dump())
    elif user is not None:
        p = db.get(Profile, user.id)
    else:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Send a profile in the request or log in first")
    return ai_service.profile_context(p, daily_calorie_target(p) if p else None)


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
