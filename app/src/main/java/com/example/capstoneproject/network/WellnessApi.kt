package com.example.capstoneproject.network

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface WellnessApi {

    @GET("health")
    suspend fun health(): Response<Map<String, String>>

    // ---- auth ----
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<UserOut>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @GET("auth/me")
    suspend fun me(): Response<UserOut>

    // ---- profile (token added by AuthInterceptor) ----
    @GET("profile")
    suspend fun getProfile(): Response<ProfileOut>

    @PUT("profile")
    suspend fun updateProfile(@Body body: ProfileIn): Response<ProfileOut>

    // ---- AI ----
    @POST("ai/chat")
    suspend fun chat(@Body body: ChatRequest): Response<ChatResponse>

    @POST("ai/recommend")
    suspend fun recommend(@Body body: RecommendRequest): Response<JsonElement>

    /** kcal + macros for a described meal — the food log's "estimate with AI" button. */
    @POST("ai/estimate")
    suspend fun estimate(@Body body: EstimateRequest): Response<EstimateOut>

    /** Meal photo -> identification, nutrition estimate and a coaching verdict (login required). */
    @POST("ai/analyse-meal")
    suspend fun analyseMeal(@Body body: AnalyseMealRequest): Response<AnalyseMealOut>

    // ---- food log (token required) ----
    @POST("log/meals")
    suspend fun addMeal(@Body body: MealLogIn): Response<MealLogOut>

    /** One day's meals with totals, calorie target and calories remaining. date = YYYY-MM-DD or null for today. */
    @GET("log/meals")
    suspend fun dayLog(@Query("date") date: String? = null): Response<DayLogOut>

    /** Meals over the last N days, newest first, with totals — History screen, Food tab. */
    @GET("log/meals/history")
    suspend fun mealHistory(@Query("days") days: Int = 30): Response<MealHistoryOut>

    @DELETE("log/meals/{id}")
    suspend fun deleteMeal(@Path("id") id: String): Response<Unit>

    /** kcal per day for the last N days, oldest first, zero-filled — dashboard / weekly chart. */
    @GET("log/summary")
    suspend fun summary(@Query("days") days: Int = 7): Response<List<DaySummary>>

    // ---- workouts ----
    @POST("log/workouts")
    suspend fun addWorkout(@Body body: WorkoutLogIn): Response<WorkoutLogOut>

    /** Sessions in the last N days, newest first, with totals. */
    @GET("log/workouts")
    suspend fun workoutHistory(@Query("days") days: Int = 7): Response<WorkoutHistoryOut>

    @DELETE("log/workouts/{id}")
    suspend fun deleteWorkout(@Path("id") id: String): Response<Unit>

    // ---- weight / progress ----
    @POST("log/weight")
    suspend fun addWeight(@Body body: WeightLogIn): Response<WeightLogOut>

    /** Weigh-ins over the last N days with start/latest/change — the Progress chart's data. */
    @GET("log/weight")
    suspend fun weightHistory(@Query("days") days: Int = 90): Response<WeightHistoryOut>

    @DELETE("log/weight/{id}")
    suspend fun deleteWeight(@Path("id") id: String): Response<Unit>
}
