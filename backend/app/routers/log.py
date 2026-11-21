"""Food log, workout log and weigh-ins: the data behind the Dashboard, Food Log, Training and Progress screens.

Nutrition numbers are whatever the user saved: typed by hand, taken from an AI estimate (/ai/estimate),
or copied from an AI meal plan. Every entry carries `source` so the app can show where a number came from.
"""
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import MealLog, Profile, User, WeightLog, WorkoutLog
from ..nutrition import daily_calorie_target, estimate_workout_kcal
from ..schemas import (
    DayLogOut,
    DaySummary,
    DayTotals,
    MealHistoryOut,
    MealHistoryTotals,
    MealLogIn,
    MealLogOut,
    WeightHistoryOut,
    WeightLogIn,
    WeightLogOut,
    WorkoutHistoryOut,
    WorkoutLogIn,
    WorkoutLogOut,
    WorkoutTotals,
)
from ..security import get_current_user

router = APIRouter(prefix="/log", tags=["log"])


def _today() -> str:
    return date.today().isoformat()


# ------------------------------------------------------------------ meals
@router.post("/meals", response_model=MealLogOut, status_code=status.HTTP_201_CREATED)
def add_meal(body: MealLogIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entry = MealLog(user_id=user.id, **body.model_dump(exclude_unset=True))
    entry.date = body.date or _today()
    db.add(entry)
    db.commit()
    db.refresh(entry)
    return entry


def day_view(user: User, db: Session, day: str) -> DayLogOut:
    """Food and training for one day in one object. Shared by GET /log/meals and the AI coach context."""
    meals = db.scalars(
        select(MealLog).where(MealLog.user_id == user.id, MealLog.date == day).order_by(MealLog.created_at)
    ).all()
    workouts = db.scalars(
        select(WorkoutLog).where(WorkoutLog.user_id == user.id, WorkoutLog.date == day).order_by(WorkoutLog.created_at)
    ).all()
    totals = DayTotals(
        kcal=sum(m.kcal for m in meals),
        protein_g=round(sum(m.protein_g or 0 for m in meals), 1),
        carbs_g=round(sum(m.carbs_g or 0 for m in meals), 1),
        fat_g=round(sum(m.fat_g or 0 for m in meals), 1),
    )
    burned = sum(w.kcal_burned for w in workouts)
    target = daily_calorie_target(db.get(Profile, user.id))
    return DayLogOut(
        date=day,
        meals=meals,
        totals=totals,
        workouts=workouts,
        calories_burned=burned,
        net_kcal=totals.kcal - burned,
        calorie_target=target,
        calories_remaining=(target - totals.kcal + burned) if target is not None else None,
    )


@router.get("/meals", response_model=DayLogOut)
def day_log(
    day: str | None = Query(None, alias="date", pattern=r"^\d{4}-\d{2}-\d{2}$", description="YYYY-MM-DD, default today"),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Everything logged on one day (meals and workouts) plus totals against the profile's calorie target."""
    return day_view(user, db, day or _today())


@router.get("/meals/history", response_model=MealHistoryOut)
def meal_history(
    days: int = Query(30, ge=1, le=365),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Meals in the last N days, newest first, with totals: the History screen's Food tab.
    Mirrors GET /log/workouts (same date window, same ordering) so both history lists behave the same."""
    start = (date.today() - timedelta(days=days - 1)).isoformat()
    entries = db.scalars(
        select(MealLog)
        .where(MealLog.user_id == user.id, MealLog.date >= start)
        .order_by(MealLog.date.desc(), MealLog.created_at.desc())
    ).all()
    kcal = sum(m.kcal for m in entries)
    days_logged = len({m.date for m in entries})
    return MealHistoryOut(
        entries=entries,
        totals=MealHistoryTotals(
            meals=len(entries),
            kcal=kcal,
            days_logged=days_logged,
            avg_kcal_per_logged_day=round(kcal / days_logged) if days_logged else 0,
        ),
        days=days,
    )


@router.delete("/meals/{meal_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_meal(meal_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entry = db.get(MealLog, meal_id)
    if entry is None or entry.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Meal not found")
    db.delete(entry)
    db.commit()


@router.get("/summary", response_model=list[DaySummary])
def summary(
    days: int = Query(7, ge=1, le=90),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Calories eaten and burned per day for the last N days (oldest first), zero-filled, for the weekly chart."""
    start = date.today() - timedelta(days=days - 1)
    rows = db.execute(
        select(MealLog.date, func.coalesce(func.sum(MealLog.kcal), 0), func.count(MealLog.id))
        .where(MealLog.user_id == user.id, MealLog.date >= start.isoformat())
        .group_by(MealLog.date)
    ).all()
    by_day = {d: (int(k), int(n)) for d, k, n in rows}
    wrows = db.execute(
        select(WorkoutLog.date, func.coalesce(func.sum(WorkoutLog.kcal_burned), 0), func.count(WorkoutLog.id))
        .where(WorkoutLog.user_id == user.id, WorkoutLog.date >= start.isoformat())
        .group_by(WorkoutLog.date)
    ).all()
    w_by_day = {d: (int(k), int(n)) for d, k, n in wrows}
    out = []
    for i in range(days):
        d = (start + timedelta(days=i)).isoformat()
        kcal, n = by_day.get(d, (0, 0))
        burned, wn = w_by_day.get(d, (0, 0))
        out.append(DaySummary(date=d, kcal=kcal, meals=n, kcal_burned=burned, workouts=wn))
    return out


# ------------------------------------------------------------------ workouts
@router.post("/workouts", response_model=WorkoutLogOut, status_code=status.HTTP_201_CREATED)
def add_workout(body: WorkoutLogIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Record a completed session. If kcal_burned is omitted it is estimated from MET x body weight x time."""
    data = body.model_dump(exclude_unset=True)
    entry = WorkoutLog(user_id=user.id, **data)
    entry.date = body.date or _today()
    if body.kcal_burned is None:
        profile = db.get(Profile, user.id)
        entry.kcal_burned = estimate_workout_kcal(body.workout_type, body.duration_min, profile.weight_kg if profile else None)
    db.add(entry)
    db.commit()
    db.refresh(entry)
    return entry


@router.get("/workouts", response_model=WorkoutHistoryOut)
def workout_history(
    days: int = Query(7, ge=1, le=365),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Sessions in the last N days, newest first, with totals — the Training tab's history and the Progress screen."""
    start = (date.today() - timedelta(days=days - 1)).isoformat()
    entries = db.scalars(
        select(WorkoutLog)
        .where(WorkoutLog.user_id == user.id, WorkoutLog.date >= start)
        .order_by(WorkoutLog.date.desc(), WorkoutLog.created_at.desc())
    ).all()
    return WorkoutHistoryOut(
        entries=entries,
        totals=WorkoutTotals(
            sessions=len(entries),
            minutes=sum(e.duration_min for e in entries),
            kcal_burned=sum(e.kcal_burned for e in entries),
        ),
        days=days,
    )


@router.delete("/workouts/{entry_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_workout(entry_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entry = db.get(WorkoutLog, entry_id)
    if entry is None or entry.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Workout not found")
    db.delete(entry)
    db.commit()


# ------------------------------------------------------------------ weight
@router.post("/weight", response_model=WeightLogOut, status_code=status.HTTP_201_CREATED)
def add_weight(body: WeightLogIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Record a weigh-in. Also updates the profile weight so the calorie target follows the user's progress."""
    entry = WeightLog(user_id=user.id, **body.model_dump(exclude_unset=True))
    entry.date = body.date or _today()
    db.add(entry)
    profile = db.get(Profile, user.id)
    if profile is None:
        profile = Profile(user_id=user.id)
        db.add(profile)
    profile.weight_kg = body.weight_kg
    db.commit()
    db.refresh(entry)
    return entry


@router.get("/weight", response_model=WeightHistoryOut)
def weight_history(
    days: int = Query(90, ge=1, le=730),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Weigh-ins over the last N days, oldest first, with the overall change — the Progress chart's data."""
    start = (date.today() - timedelta(days=days - 1)).isoformat()
    entries = db.scalars(
        select(WeightLog)
        .where(WeightLog.user_id == user.id, WeightLog.date >= start)
        .order_by(WeightLog.date, WeightLog.created_at)
    ).all()
    first = entries[0].weight_kg if entries else None
    last = entries[-1].weight_kg if entries else None
    return WeightHistoryOut(
        entries=entries,
        start_kg=first,
        latest_kg=last,
        change_kg=round(last - first, 1) if entries else None,
    )


@router.delete("/weight/{entry_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_weight(entry_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entry = db.get(WeightLog, entry_id)
    if entry is None or entry.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Entry not found")
    db.delete(entry)
    db.commit()
