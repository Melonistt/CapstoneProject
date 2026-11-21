package com.example.capstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.databinding.ActivityProfileSetupBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.ProfileIn
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Step 2 of sign-up: the health profile. Runs right after the account is created (and the user is
 * logged in), so a new user lands on the dashboard with a calorie target already set and every
 * feature personalised from the first tap. "Skip for now" goes straight to the app; the Profile
 * tab holds the same fields. Client request 19 Nov: all health data connected from account creation.
 */
class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding

    private val sexOptions = linkedMapOf("Male" to "male", "Female" to "female")
    private val activityOptions = linkedMapOf(
        "Sedentary (desk job, little exercise)" to "sedentary",
        "Light (1–2 sessions a week)" to "light",
        "Moderate (3–5 sessions a week)" to "moderate",
        "Active (6–7 sessions a week)" to "active",
        "Very active (hard training or physical job)" to "very_active"
    )
    private val goalOptions = linkedMapOf(
        "Weight loss" to "weight_loss",
        "Maintain weight" to "maintain",
        "Muscle gain" to "muscle_gain",
        "General fitness" to "fitness",
        "Stress management" to "stress_management"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindDropdown(binding.acSex, sexOptions.keys)
        bindDropdown(binding.acActivity, activityOptions.keys)
        bindDropdown(binding.acGoal, goalOptions.keys)
        binding.acActivity.setText("Moderate (3–5 sessions a week)", false)
        binding.acGoal.setText("General fitness", false)

        // Live preview of the target so the user sees why we're asking.
        listOf(binding.etAge, binding.etHeight, binding.etWeight).forEach { it.addTextChangedListener(SimpleWatcher { updatePreview() }) }
        listOf(binding.acSex, binding.acActivity, binding.acGoal).forEach { it.setOnItemClickListener { _, _, _, _ -> updatePreview() } }

        binding.btnContinue.setOnClickListener { save() }
        binding.btnSkip.setOnClickListener { goToMain() }
    }

    private fun bindDropdown(view: AutoCompleteTextView, labels: Collection<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels.toList()))
    }

    private fun collect(): ProfileIn? {
        val age = binding.etAge.text?.toString()?.trim()?.toIntOrNull()
        val height = binding.etHeight.text?.toString()?.trim()?.toDoubleOrNull()
        val weight = binding.etWeight.text?.toString()?.trim()?.toDoubleOrNull()
        val sex = sexOptions[binding.acSex.text?.toString()]
        val activity = activityOptions[binding.acActivity.text?.toString()]
        val goal = goalOptions[binding.acGoal.text?.toString()]
        if (age == null || height == null || weight == null || sex == null || activity == null) return null
        return ProfileIn(age = age, sex = sex, height_cm = height, weight_kg = weight, activity_level = activity, goal = goal)
    }

    /** Same Mifflin-St Jeor maths as backend/app/nutrition.py, for the on-screen preview only. */
    private fun updatePreview() {
        val p = collect()
        if (p == null) { binding.tvPreview.text = " "; return }
        val bmr = 10 * p.weight_kg!! + 6.25 * p.height_cm!! - 5 * p.age!! + if (p.sex == "male") 5 else -161
        val factor = mapOf("sedentary" to 1.2, "light" to 1.375, "moderate" to 1.55, "active" to 1.725, "very_active" to 1.9)[p.activity_level] ?: 1.2
        val adjust = when (p.goal) { "weight_loss" -> -500; "muscle_gain" -> 300; else -> 0 }
        val floor = if (p.sex == "male") 1500 else 1200
        val target = ((maxOf(bmr * factor + adjust, floor.toDouble()) / 10).roundToInt() * 10)
        binding.tvPreview.text = "Your daily target will be about $target kcal"
    }

    private fun save() {
        val p = collect()
        if (p == null) {
            Toast.makeText(this, "Fill in age, sex, height, weight and activity — or tap Skip for now", Toast.LENGTH_LONG).show()
            return
        }
        binding.btnContinue.isEnabled = false
        lifecycleScope.launch {
            try {
                val resp = ApiClient.api.updateProfile(p)
                if (resp.isSuccessful) {
                    val t = resp.body()?.daily_calorie_target
                    Toast.makeText(this@ProfileSetupActivity, if (t != null) "Profile saved — target $t kcal/day" else "Profile saved", Toast.LENGTH_LONG).show()
                    goToMain()
                } else {
                    Toast.makeText(this@ProfileSetupActivity, "Couldn't save: ${ApiClient.errorMessage(resp)}", Toast.LENGTH_LONG).show()
                    binding.btnContinue.isEnabled = true
                }
            } catch (e: IOException) {
                Toast.makeText(this@ProfileSetupActivity, "Can't reach the server — you can finish this in Profile later", Toast.LENGTH_LONG).show()
                binding.btnContinue.isEnabled = true
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    /** Tiny TextWatcher so the preview updates as the user types. */
    private class SimpleWatcher(val onChange: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = onChange()
    }
}
