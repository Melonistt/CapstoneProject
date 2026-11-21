package com.example.capstoneproject.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.LoginActivity
import com.example.capstoneproject.R
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.ProfileIn
import com.example.capstoneproject.network.ProfileOut
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Profile / Settings tab: the wellness profile the AI coach draws on (report FR-3).
 * GET /profile on open, PUT /profile on Save; shows the daily calorie target the backend computes.
 */
class ProfileFragment : Fragment() {

    private val TAG = "ProfileFragment"

    // Display labels ↔ backend values
    private val sexOptions = linkedMapOf("Male" to "male", "Female" to "female")
    private val activityOptions = linkedMapOf(
        "Sedentary (desk job, little exercise)" to "sedentary",
        "Light (1–3 sessions a week)" to "light",
        "Moderate (3–5 sessions a week)" to "moderate",
        "Active (6–7 sessions a week)" to "active",
        "Very active (physical job + training)" to "very_active",
    )
    private val goalOptions = linkedMapOf(
        "Weight loss" to "weight_loss",
        "Maintain weight" to "maintain",
        "Muscle gain" to "muscle_gain",
        "General fitness" to "fitness",
        "Stress management" to "stress_management",
    )

    private lateinit var etAge: TextInputEditText
    private lateinit var etHeight: TextInputEditText
    private lateinit var etWeight: TextInputEditText
    private lateinit var etDiet: TextInputEditText
    private lateinit var etAllergies: TextInputEditText
    private lateinit var acSex: AutoCompleteTextView
    private lateinit var acActivity: AutoCompleteTextView
    private lateinit var acGoal: AutoCompleteTextView
    private lateinit var tvTarget: TextView
    private lateinit var tvTargetHint: TextView
    private lateinit var btnSave: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etAge = view.findViewById(R.id.etAge)
        etHeight = view.findViewById(R.id.etHeight)
        etWeight = view.findViewById(R.id.etWeight)
        etDiet = view.findViewById(R.id.etDiet)
        etAllergies = view.findViewById(R.id.etAllergies)
        acSex = view.findViewById(R.id.acSex)
        acActivity = view.findViewById(R.id.acActivity)
        acGoal = view.findViewById(R.id.acGoal)
        tvTarget = view.findViewById(R.id.tvCalorieTarget)
        tvTargetHint = view.findViewById(R.id.tvTargetHint)
        btnSave = view.findViewById(R.id.btnSaveProfile)

        acSex.setAdapter(dropdown(sexOptions.keys))
        acActivity.setAdapter(dropdown(activityOptions.keys))
        acGoal.setAdapter(dropdown(goalOptions.keys))

        // Header from what Sign Up / Login stored
        val prefs = requireContext().getSharedPreferences("AppPreferences", 0)
        prefs.getString("user_name", null)?.let { view.findViewById<TextView>(R.id.tvUserName).text = it }
        prefs.getString("email", null)?.let { view.findViewById<TextView>(R.id.tvUserEmail).text = it }

        btnSave.setOnClickListener { saveProfile() }
        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { logout() }

        loadProfile()
    }

    private fun dropdown(items: Collection<String>) =
        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items.toList())

    // ------------------------------------------------------------------ load
    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Header from the server account record, so it is right for accounts created before
                // the app started caching the name locally.
                val me = ApiClient.api.me()
                me.body()?.let { u ->
                    val full = listOf(u.first_name, u.last_name).filter { it.isNotBlank() }.joinToString(" ")
                    if (full.isNotBlank()) view?.findViewById<TextView>(R.id.tvUserName)?.text = full
                    view?.findViewById<TextView>(R.id.tvUserEmail)?.text = u.email
                }
                val response = ApiClient.api.getProfile()
                if (response.isSuccessful) response.body()?.let { show(it) }
                else toast(ApiClient.errorMessage(response))
            } catch (e: IOException) {
                toast("Can't reach the server (${e.message})")
            }
        }
    }

    private fun show(p: ProfileOut) {
        etAge.setText(p.age?.toString() ?: "")
        etHeight.setText(p.height_cm?.let { trim(it) } ?: "")
        etWeight.setText(p.weight_kg?.let { trim(it) } ?: "")
        etDiet.setText(p.dietary_preferences ?: "")
        etAllergies.setText(p.allergies ?: "")
        acSex.setText(labelFor(sexOptions, p.sex), false)
        acActivity.setText(labelFor(activityOptions, p.activity_level), false)
        acGoal.setText(labelFor(goalOptions, p.goal), false)
        showTarget(p.daily_calorie_target)
    }

    private fun showTarget(target: Int?) {
        if (target != null) {
            tvTarget.text = "$target kcal"
            tvTargetHint.text = "Mifflin-St Jeor estimate for your goal"
        } else {
            tvTarget.text = "—"
            tvTargetHint.text = "Fill in age, sex, height, weight and activity, then save"
        }
    }

    // ------------------------------------------------------------------ save
    private fun saveProfile() {
        val body = ProfileIn(
            age = etAge.text?.toString()?.trim()?.toIntOrNull(),
            sex = sexOptions[acSex.text.toString()],
            height_cm = etHeight.text?.toString()?.trim()?.toDoubleOrNull(),
            weight_kg = etWeight.text?.toString()?.trim()?.toDoubleOrNull(),
            activity_level = activityOptions[acActivity.text.toString()],
            goal = goalOptions[acGoal.text.toString()],
            dietary_preferences = etDiet.text?.toString()?.trim()?.ifBlank { null },
            allergies = etAllergies.text?.toString()?.trim()?.ifBlank { null },
        )
        btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.api.updateProfile(body)
                if (response.isSuccessful && response.body() != null) {
                    show(response.body()!!)
                    toast("Profile saved")
                } else {
                    toast(ApiClient.errorMessage(response))
                }
            } catch (e: IOException) {
                toast("Can't reach the server (${e.message})")
            } finally {
                btnSave.isEnabled = true
            }
        }
    }

    // ------------------------------------------------------------------ logout
    private fun logout() {
        try {
            // Forget the session: the bearer token (what LoginActivity checks) and the cached user details
            ApiClient.clearToken()
            requireContext().getSharedPreferences("AppPreferences", 0).edit().clear().apply()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout", e)
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun labelFor(options: Map<String, String>, value: String?): String =
        options.entries.firstOrNull { it.value == value }?.key ?: ""

    private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
