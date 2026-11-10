package com.example.capstoneproject.network

import android.content.Context
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single Retrofit instance for the whole app.
 *
 * BASE_URL: 10.0.2.2 is the Android emulator's alias for the host PC's localhost.
 * On a physical phone replace with the PC's LAN IP, e.g. "http://192.168.1.20:8000/".
 */
object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8000/"
    private const val PREFS = "AppPreferences"
    private const val KEY_TOKEN = "access_token"

    private lateinit var appContext: Context

    /** Call once from CapstoneProject.onCreate() (the Application class). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---- token storage ----
    fun saveToken(token: String) =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).apply()

    fun getToken(): String? =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun clearToken() =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_TOKEN).apply()

    // ---- OkHttp / Retrofit ----
    private val authInterceptor = Interceptor { chain ->
        val token = getToken()
        val request = if (token != null)
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        else chain.request()
        chain.proceed(request)
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // Gemini calls can take a few seconds
            .build()
    }

    val api: WellnessApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WellnessApi::class.java)
    }

    /** Pulls a human-readable message out of a FastAPI error response. */
    fun errorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string() ?: return "HTTP ${response.code()}"
        return try {
            val detail = Gson().fromJson(raw, ApiError::class.java).detail
            when (detail) {
                is String -> detail
                is List<*> -> (detail.firstOrNull() as? Map<*, *>)?.get("msg")?.toString() ?: raw
                else -> raw
            }
        } catch (e: Exception) {
            raw
        }
    }
}
