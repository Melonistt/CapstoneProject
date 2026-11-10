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
