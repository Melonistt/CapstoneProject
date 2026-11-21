package com.example.capstoneproject.network

// Request/response bodies matching backend/app/schemas.py exactly (snake_case field names).

data class RegisterRequest(
    val email: String,
    val password: String,
    val first_name: String,
    val last_name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class TokenResponse(
    val access_token: String,
    val token_type: String
)

data class UserOut(
    val id: String,
    val email: String,
    val first_name: String,
    val last_name: String
)

data class ProfileIn(
    val age: Int? = null,
    val sex: String? = null,                 // "male" | "female"
    val height_cm: Double? = null,
    val weight_kg: Double? = null,
    val activity_level: String? = null,      // sedentary | light | moderate | active | very_active
    val goal: String? = null,                // weight_loss | maintain | muscle_gain | fitness | stress_management
    val dietary_preferences: String? = null,
    val allergies: String? = null
)

data class ProfileOut(
    val age: Int?,
    val sex: String?,
    val height_cm: Double?,
    val weight_kg: Double?,
    val activity_level: String?,
    val goal: String?,
    val dietary_preferences: String?,
    val allergies: String?,
    val daily_calorie_target: Int?
)

data class ChatTurn(val role: String, val text: String)   // role: "user" | "assistant"

data class ChatRequest(
    val message: String,
    val history: List<ChatTurn> = emptyList(),
    val profile: ProfileIn? = null
)

data class ChatResponse(val reply: String)

data class RecommendRequest(
    val kind: String,                        // "meal_plan" | "workout_plan"
    val request: String? = null,
    val days: Int = 1,
    val profile: ProfileIn? = null
)

/** FastAPI error body: {"detail": "..."} or {"detail": [ {...validation...} ]} */
data class ApiError(val detail: Any?)

// ---- Food log & progress (backend/app/routers/log.py) ----

data class MealLogIn(
    val meal_type: String,                   // breakfast | lunch | dinner | snack
    val name: String,
    val kcal: Int,
    val protein_g: Double? = null,
    val carbs_g: Double? = null,
    val fat_g: Double? = null,
    val source: String = "manual",           // manual | ai_estimate | ai_plan | photo
    val date: String? = null                 // YYYY-MM-DD, null = today
)

data class MealLogOut(
    val id: String,
    val date: String,
    val meal_type: String,
    val name: String,
    val kcal: Int,
    val protein_g: Double?,
    val carbs_g: Double?,
    val fat_g: Double?,
    val source: String
)

data class DayTotals(val kcal: Int, val protein_g: Double, val carbs_g: Double, val fat_g: Double)

data class WorkoutLogIn(
    val name: String,
    val workout_type: String = "other",      // walking | running | cardio | strength | stretching | other
    val duration_min: Int,
    val kcal_burned: Int? = null,            // null = server estimates from MET x weight x time
    val source: String = "manual",           // manual | ai_plan
    val plan_day: Int? = null,
    val notes: String? = null,
    val date: String? = null
)

data class WorkoutLogOut(
    val id: String,
    val date: String,
    val name: String,
    val workout_type: String,
    val duration_min: Int,
    val kcal_burned: Int,
    val source: String,
    val plan_day: Int?,
    val notes: String?
)

data class WorkoutTotals(val sessions: Int, val minutes: Int, val kcal_burned: Int)

data class WorkoutHistoryOut(val entries: List<WorkoutLogOut>, val totals: WorkoutTotals, val days: Int)

/** GET /log/meals/history — food history, same shape as WorkoutHistoryOut. */
data class MealHistoryTotals(val meals: Int, val kcal: Int, val days_logged: Int, val avg_kcal_per_logged_day: Int)

data class MealHistoryOut(val entries: List<MealLogOut>, val totals: MealHistoryTotals, val days: Int)

/** One day, food and training together. calories_remaining = target - eaten + burned. */
data class DayLogOut(
    val date: String,
    val meals: List<MealLogOut>,
    val totals: DayTotals,
    val workouts: List<WorkoutLogOut> = emptyList(),
    val calories_burned: Int = 0,
    val net_kcal: Int = 0,
    val calorie_target: Int?,
    val calories_remaining: Int?
)

data class DaySummary(val date: String, val kcal: Int, val meals: Int, val kcal_burned: Int = 0, val workouts: Int = 0)

data class WeightLogIn(val weight_kg: Double, val notes: String? = null, val date: String? = null)

data class WeightLogOut(val id: String, val date: String, val weight_kg: Double, val notes: String?)

data class WeightHistoryOut(
    val entries: List<WeightLogOut>,
    val start_kg: Double?,
    val latest_kg: Double?,
    val change_kg: Double?
)

data class AnalyseMealRequest(val image_base64: String, val mime_type: String = "image/jpeg", val caption: String? = null)

data class SuggestedSession(val workout_type: String, val duration_min: Int)
data class TrainingAdjustment(val summary: String?, val suggested_session: SuggestedSession?)

/** Photo analysis result: estimate + coaching verdict against today's budget and training. */
data class AnalyseMealOut(
    val name: String,
    val portion_assumption: String?,
    val kcal: Int,
    val protein_g: Double,
    val carbs_g: Double,
    val fat_g: Double,
    val confidence: String,
    val verdict: String,            // good_to_go | smaller_portion | balance_it | enjoy_as_treat
    val reason: String,
    val rest_of_day: String?,
    val training_adjustment: TrainingAdjustment?,
    val source: String = "photo"
)

data class EstimateRequest(val description: String, val portion: String? = null)

data class EstimateOut(
    val name: String,
    val kcal: Int,
    val protein_g: Double,
    val carbs_g: Double,
    val fat_g: Double,
    val confidence: String,                  // low | medium | high
    val assumptions: String,
    val source: String
)

// ---- AI plan shapes (what /ai/recommend returns inside the JsonElement) ----

data class PlanMeal(
    val name: String?,
    val meal_type: String?,
    val kcal: Int?,
    val protein_g: Double?,
    val carbs_g: Double?,
    val fat_g: Double?,
    val ingredients: List<String>?,
    val instructions: String?
)

data class MealPlanDay(val day: Int?, val total_kcal: Int?, val meals: List<PlanMeal>?)

data class MealPlan(val days: List<MealPlanDay>?, val notes: String?, val disclaimer: String?)

data class PlanExercise(
    val name: String?,
    val type: String?,
    val sets: Int?,
    val reps: String?,
    val duration_min: Int?,
    val instructions: String?
)

data class WorkoutPlanDay(val day: Int?, val focus: String?, val duration_min: Int?, val exercises: List<PlanExercise>?)

data class WorkoutPlan(val days: List<WorkoutPlanDay>?, val notes: String?, val disclaimer: String?)
