package com.example.capstoneproject.network

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST

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
}
