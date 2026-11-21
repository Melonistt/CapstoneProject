package com.example.capstoneproject.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.example.capstoneproject.R
import com.example.capstoneproject.network.ApiClient
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var btnQuickWorkout: MaterialButton? = null
    private var btnLogMeal: MaterialButton? = null
    private var tvGreeting: TextView? = null
    private var tvTodayStats: TextView? = null
    private var btnOpenCoach: MaterialButton? = null
    private var tvCaloriesRemaining: TextView? = null
    private var tvCaloriesEaten: TextView? = null
    private var tvProteinToday: TextView? = null
    private var pbCalories: ProgressBar? = null
    private var tvWorkoutsToday: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setClickListeners()
        loadUserData()
    }

    private fun initializeViews(view: View) {
        btnQuickWorkout = view.findViewById(R.id.btnQuickWorkout)
        btnLogMeal = view.findViewById(R.id.btnLogMeal)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvTodayStats = view.findViewById(R.id.tvTodayStats)
        btnOpenCoach = view.findViewById(R.id.btnOpenCoach)
        tvCaloriesRemaining = view.findViewById(R.id.tvCaloriesRemaining)
        tvCaloriesEaten = view.findViewById(R.id.tvCaloriesEaten)
        tvProteinToday = view.findViewById(R.id.tvProteinToday)
        pbCalories = view.findViewById(R.id.pbCalories)
        tvWorkoutsToday = view.findViewById(R.id.tvWorkoutsToday)
    }

    private fun setClickListeners() {
        // Quick Workout Button
        btnQuickWorkout?.setOnClickListener {
            navigateToTraining()
        }

        // Log Meal Button
        btnLogMeal?.setOnClickListener {
            navigateToLogMeal()
        }

        // AI Coach card
        btnOpenCoach?.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_homeFragment_to_coachFragment)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserData() {
        // Same prefs file LoginActivity/SignupActivity write to.
        val sharedPref = requireContext().getSharedPreferences("AppPreferences", 0)
        val userName = sharedPref.getString("user_name", null)
            ?: sharedPref.getString("email", "User")?.substringBefore("@") ?: "User"

        tvGreeting?.text = "Good ${partOfDay()}, ${userName.replaceFirstChar { it.uppercase() }}!"
        tvTodayStats?.text = "Today: loading…"

        // Real numbers from GET /log/meals (today's view).
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api.dayLog(null)
                val day = resp.body()
                if (resp.isSuccessful && day != null) {
                    val eaten = day.totals.kcal
                    val burned = day.calories_burned
                    val target = day.calorie_target
                    tvTodayStats?.text = if (target != null)
                        "Today: $eaten eaten • $burned burned • ${day.calories_remaining ?: 0} kcal left"
                    else
                        "No calorie target yet — tap here to finish your profile"
                    tvTodayStats?.setOnClickListener(
                        if (target == null) View.OnClickListener {
                            try { findNavController().navigate(R.id.profileFragment) } catch (e: Exception) { }
                        } else null
                    )

                    tvCaloriesEaten?.text = if (burned > 0) "$eaten kcal (−$burned)" else "$eaten kcal"
                    tvProteinToday?.text = "${day.totals.protein_g.toInt()} g"
                    tvWorkoutsToday?.text = when (day.workouts.size) {
                        0 -> "Rest day"
                        1 -> "${day.workouts[0].name} • ${day.workouts[0].duration_min} min"
                        else -> "${day.workouts.size} sessions • ${day.workouts.sumOf { it.duration_min }} min"
                    }
                    if (target != null) {
                        // Remaining includes calories earned back from exercise (server rule).
                        val remaining = (day.calories_remaining ?: (target - eaten)).coerceAtLeast(0)
                        tvCaloriesRemaining?.text = "%,d".format(remaining)
                        pbCalories?.progress = (day.net_kcal.coerceAtLeast(0) * 100 / target).coerceIn(0, 100)
                    } else {
                        tvCaloriesRemaining?.text = "—"
                        pbCalories?.progress = 0
                    }
                } else {
                    tvTodayStats?.text = "Today: no data yet"
                }
            } catch (e: Exception) {
                tvTodayStats?.text = "Today: offline"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun partOfDay(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "morning"
            h < 17 -> "afternoon"
            else -> "evening"
        }
    }

    private fun navigateToTraining() {
        try {
            findNavController().navigate(R.id.action_homeFragment_to_trainingFragment)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToLogMeal() {
        try {
            findNavController().navigate(R.id.action_homeFragment_to_logMealFragment)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
