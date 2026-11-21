package com.example.capstoneproject.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.R
import com.example.capstoneproject.databinding.FragmentTrainBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.RecommendRequest
import com.example.capstoneproject.network.WorkoutLogIn
import com.example.capstoneproject.network.WorkoutPlan
import com.example.capstoneproject.network.WorkoutPlanDay
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Training tab.
 *  - "This week": sessions / minutes / kcal from GET /log/workouts, with a manual "Log a workout" form.
 *  - Routine builder: POST /ai/recommend (kind=workout_plan) rendered as day cards, each with
 *    "Mark as done" -> POST /log/workouts (source=ai_plan) so training feeds the dashboard and the coach.
 */
class TrainFragment : Fragment() {

    private var _binding: FragmentTrainBinding? = null
    private val binding get() = _binding!!

    private val typeOptions = linkedMapOf(
        "Walking" to "walking", "Running" to "running", "Cardio / HIIT" to "cardio",
        "Strength" to "strength", "Stretching / yoga" to "stretching", "Other" to "other"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.acWorkoutType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, typeOptions.keys.toList())
        )
        binding.acWorkoutType.setText("Strength", false)

        binding.btnLogWorkout.setOnClickListener {
            binding.llLogForm.visibility = if (binding.llLogForm.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.btnSaveWorkout.setOnClickListener { saveManual() }
        binding.btnBuildRoutine.setOnClickListener { buildRoutine() }
        loadWeek()
    }

    override fun onResume() {
        super.onResume()
        // A suggestion handed over from the meal-photo analysis ("plan a 30-min walk to balance dinner").
        val prefs = requireContext().getSharedPreferences("AppPreferences", 0)
        prefs.getString("pending_training_request", null)?.let { req ->
            prefs.edit().remove("pending_training_request").apply()
            binding.etPlanRequest.setText(req)
            binding.chip1.isChecked = true
            toast("Suggestion from your meal photo added — tap Build new routine")
        }
    }

    // ---------- this week ----------

    private fun loadWeek() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api.workoutHistory(7)
                val h = resp.body()
                if (!resp.isSuccessful || h == null) {
                    binding.tvWeekTotals.text = ApiClient.errorMessage(resp)
                    return@launch
                }
                val t = h.totals
                binding.tvWeekTotals.text =
                    if (t.sessions == 0) "No workouts logged in the last 7 days yet."
                    else "${t.sessions} session${if (t.sessions == 1) "" else "s"} • ${t.minutes} min • ≈${t.kcal_burned} kcal burned"

                binding.llRecentWorkouts.removeAllViews()
                for (w in h.entries.take(5)) {
                    val row = layoutInflater.inflate(R.layout.item_meal_row, binding.llRecentWorkouts, false)
                    row.findViewById<TextView>(R.id.tvRowTitle).text = w.name
                    row.findViewById<TextView>(R.id.tvRowSub).text =
                        "${w.date} • ${w.duration_min} min • ${w.workout_type}" + if (w.source == "ai_plan") " • from plan" else ""
                    row.findViewById<TextView>(R.id.tvRowKcal).text = "${w.kcal_burned} kcal"
                    val btn = row.findViewById<MaterialButton>(R.id.btnRowAction)
                    btn.text = "Delete"
                    btn.setOnClickListener {
                        btn.isEnabled = false
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val r = ApiClient.api.deleteWorkout(w.id)
                                if (r.isSuccessful) loadWeek() else toast(ApiClient.errorMessage(r))
                            } catch (e: IOException) { toast("Can't reach the server") }
                            finally { if (isAdded) btn.isEnabled = true }
                        }
                    }
                    binding.llRecentWorkouts.addView(row)
                }
            } catch (e: IOException) {
                binding.tvWeekTotals.text = "Can't reach the server"
            } catch (e: Exception) {
                binding.tvWeekTotals.text = "Error: ${e.message}"
            }
        }
    }

    private fun saveManual() {
        val name = binding.etWorkoutName.text?.toString()?.trim().orEmpty()
        val minutes = binding.etWorkoutMinutes.text?.toString()?.trim()?.toIntOrNull()
        if (name.isEmpty() || minutes == null || minutes <= 0) {
            toast("Enter what you did and how many minutes"); return
        }
        val type = typeOptions[binding.acWorkoutType.text?.toString()] ?: "other"
        val kcal = binding.etWorkoutKcal.text?.toString()?.trim()?.toIntOrNull()
        postWorkout(WorkoutLogIn(name = name, workout_type = type, duration_min = minutes, kcal_burned = kcal), binding.btnSaveWorkout) {
            binding.etWorkoutName.setText(""); binding.etWorkoutMinutes.setText(""); binding.etWorkoutKcal.setText("")
            binding.llLogForm.visibility = View.GONE
        }
    }

    private fun postWorkout(body: WorkoutLogIn, btn: MaterialButton, onOk: () -> Unit) {
        btn.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api.addWorkout(body)
                val w = resp.body()
                if (resp.isSuccessful && w != null) {
                    toast("Logged ${w.name} — ≈${w.kcal_burned} kcal")
                    onOk()
                    loadWeek()
                } else {
                    toast("Save failed: ${ApiClient.errorMessage(resp)}"); btn.isEnabled = true
                }
            } catch (e: IOException) {
                toast("Can't reach the server"); btn.isEnabled = true
            } catch (e: Exception) {
                toast("Error: ${e.message}"); btn.isEnabled = true
            }
        }
    }

    // ---------- routine builder ----------

    private fun selectedDays(): Int = when (binding.cgDays.checkedChipId) {
        R.id.chip3 -> 3
        R.id.chip5 -> 5
        else -> 1
    }

    private fun buildRoutine() {
        val days = selectedDays()
        val extra = binding.etPlanRequest.text?.toString()?.trim()?.ifEmpty { null }
        binding.btnBuildRoutine.isEnabled = false
        binding.llPlan.removeAllViews()
        binding.llPlan.addView(caption("Building a $days-day plan (10–30 s)…"))

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api.recommend(RecommendRequest(kind = "workout_plan", request = extra, days = days))
                binding.llPlan.removeAllViews()
                if (!resp.isSuccessful || resp.body() == null) {
                    binding.llPlan.addView(caption("Plan failed: ${ApiClient.errorMessage(resp)}. Try again."))
                    return@launch
                }
                render(Gson().fromJson(resp.body(), WorkoutPlan::class.java))
            } catch (e: IOException) {
                binding.llPlan.removeAllViews()
                binding.llPlan.addView(caption("Can't reach the server."))
            } catch (e: Exception) {
                binding.llPlan.removeAllViews()
                binding.llPlan.addView(caption("Couldn't read the plan: ${e.message}"))
            } finally {
                if (isAdded) binding.btnBuildRoutine.isEnabled = true
            }
        }
    }

    private fun render(plan: WorkoutPlan) {
        val days = plan.days.orEmpty()
        if (days.isEmpty()) {
            binding.llPlan.addView(caption("The AI returned an empty plan. Try again."))
            return
        }
        for (day in days) {
            val card = CardView(requireContext()).apply {
                radius = 12.dp().toFloat()
                cardElevation = 2.dp().toFloat()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 12.dp()
                layoutParams = lp
            }
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            }
            val title = "Day ${day.day ?: ""}" + (day.focus?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
            col.addView(heading(title))
            day.duration_min?.let { col.addView(caption("≈ $it min")) }

            for (ex in day.exercises.orEmpty()) {
                val line = buildString {
                    append("• ").append(ex.name ?: "Exercise")
                    val bits = mutableListOf<String>()
                    ex.sets?.takeIf { it > 0 }?.let { s -> bits += "$s × ${ex.reps ?: "reps"}" }
                    ex.duration_min?.takeIf { it > 0 }?.let { bits += "$it min" }
                    ex.type?.takeIf { it.isNotBlank() }?.let { bits += it }
                    if (bits.isNotEmpty()) append("  (").append(bits.joinToString(", ")).append(")")
                }
                col.addView(body(line))
                ex.instructions?.takeIf { it.isNotBlank() }?.let { col.addView(caption(it, indent = 16)) }
            }

            // "Mark as done" logs this day as a completed session (source=ai_plan).
            val done = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Mark as done"
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 8.dp()
                layoutParams = lp
            }
            done.setOnClickListener {
                postWorkout(planDayToLog(day), done) { done.text = "Done ✓"; done.isEnabled = false }
            }
            col.addView(done)

            card.addView(col)
            binding.llPlan.addView(card)
        }
        plan.notes?.takeIf { it.isNotBlank() }?.let { binding.llPlan.addView(caption(it)) }
        plan.disclaimer?.takeIf { it.isNotBlank() }?.let { binding.llPlan.addView(caption(it)) }
    }

    /** Turns a plan day into a workout log entry: name from the focus, type from the dominant exercise type. */
    private fun planDayToLog(day: WorkoutPlanDay): WorkoutLogIn {
        val exercises = day.exercises.orEmpty()
        val type = exercises.mapNotNull { it.type?.lowercase() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?.takeIf { it in typeOptions.values } ?: "other"
        val minutes = day.duration_min?.takeIf { it > 0 }
            ?: exercises.sumOf { it.duration_min ?: 0 }.takeIf { it > 0 } ?: 30
        val name = "Day ${day.day ?: ""}".trim() + (day.focus?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: " workout")
        return WorkoutLogIn(name = name, workout_type = type, duration_min = minutes, source = "ai_plan", plan_day = day.day)
    }

    // ---------- helpers ----------

    private fun heading(text: String) = TextView(requireContext()).apply {
        setText(text); setTextAppearance(R.style.TextHeading); setPadding(0, 0, 0, 4.dp())
    }

    private fun body(text: String) = TextView(requireContext()).apply {
        setText(text); setTextAppearance(R.style.TextBody); setPadding(0, 6.dp(), 0, 0)
    }

    private fun caption(text: String, indent: Int = 0) = TextView(requireContext()).apply {
        setText(text); setTextAppearance(R.style.TextCaption); setPadding(indent.dp(), 2.dp(), 0, 4.dp())
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) { if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
