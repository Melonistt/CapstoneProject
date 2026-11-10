from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Profile, User
from ..nutrition import daily_calorie_target
from ..schemas import ProfileIn, ProfileOut
from ..security import get_current_user

router = APIRouter(prefix="/profile", tags=["profile"])


def _out(p: Profile) -> ProfileOut:
    out = ProfileOut.model_validate(p)
    out.daily_calorie_target = daily_calorie_target(p)
    return out


@router.get("", response_model=ProfileOut)
def get_profile(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p = db.get(Profile, user.id) or Profile(user_id=user.id)
    return _out(p)


@router.put("", response_model=ProfileOut)
def update_profile(body: ProfileIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p = db.get(Profile, user.id)
    if p is None:
        p = Profile(user_id=user.id)
        db.add(p)
    for field, value in body.model_dump(exclude_unset=True).items():
        setattr(p, field, value)
    db.commit()
    db.refresh(p)
    return _out(p)
