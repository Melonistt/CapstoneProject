package com.example.capstoneproject.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.R
import com.example.capstoneproject.databinding.FragmentHistoryBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.MealHistoryOut
import com.example.capstoneproject.network.WorkoutHistoryOut
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * History screen (reached from the Progress tab): everything the user has logged over a period,
 * newest day first, as two lists.
 *
 *  - Food tab     -> GET /log/meals/history?days=N  (meals, grouped by day, kcal per day)
 *  - Workouts tab -> GET /log/workouts?days=N       (sessions, grouped by day, minutes per day)
 *  - Period       -> 7, 30 or 90 days
 *
 * Read-only on purpose: adding and deleting stays on the Food log and Training tabs, so there is one
 * place to edit and this screen cannot change past records by accident.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private enum class Kind { FOOD, WORKOUTS }

    /** What is on screen now. Kept across rotation in onSaveInstanceState. */
    private var kind = Kind.FOOD
    private var rangeDays = 30

    /** The request in flight, so switching tab or period can cancel an older, slower one. */
    private var loadJob: Job? = null

    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val pretty = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    /** Within a day, list meals in eating order rather than the order they were typed in. */
    private val mealOrder = listOf("breakfast", "lunch", "dinner", "snack")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            // Rotating the phone rebuilds the screen: put back the tab and period the user had chosen.
            kind = Kind.valueOf(savedInstanceState.getString(KEY_KIND) ?: Kind.FOOD.name)
            rangeDays = savedInstanceState.getInt(KEY_DAYS, 30)
        } else if (arguments?.getString(ARG_KIND) == KIND_WORKOUTS) {
            // Lets another screen open History straight on the Workouts tab.
            kind = Kind.WORKOUTS
        }

        // Select the right buttons BEFORE adding the listeners, so this doesn't trigger extra loads.
        binding.toggleKind.check(if (kind == Kind.FOOD) R.id.btnFood else R.id.btnWorkouts)
        binding.toggleRange.check(
            when (rangeDays) {
                7 -> R.id.btnRange7
                90 -> R.id.btnRange90
                else -> R.id.btnRange30
            }
        )

        binding.toggleKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener // ignore the button being switched off
            kind = if (checkedId == R.id.btnWorkouts) Kind.WORKOUTS else Kind.FOOD
            load()
        }
        binding.toggleRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            rangeDays = when (checkedId) {
                R.id.btnRange7 -> 7
                R.id.btnRange90 -> 90
                else -> 30
            }
            load()
        }

        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_KIND, kind.name)
        outState.putInt(KEY_DAYS, rangeDays)
    }

    // ---------- loading ----------

    private fun load() {
        binding.tvSummary.text = "Loading…"
        binding.llHistory.removeAllViews()

        // Tapping Food, Workouts, Food quickly fires several requests. Cancel the previous one so a slow
        // reply for the old choice can't arrive late and overwrite what the user is now looking at.
        loadJob?.cancel()
        val days = rangeDays
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (kind) {
                    Kind.FOOD -> {
                        val resp = ApiClient.api.mealHistory(days)
                        val body = resp.body()
                        if (!resp.isSuccessful || body == null) {
                            binding.tvSummary.text = ApiClient.errorMessage(resp)
                            return@launch
                        }
                        showFood(body)
                    }
                    Kind.WORKOUTS -> {
                        val resp = ApiClient.api.workoutHistory(days)
                        val body = resp.body()
                        if (!resp.isSuccessful || body == null) {
                            binding.tvSummary.text = ApiClient.errorMessage(resp)
                            return@launch
                        }
                        showWorkouts(body)
                    }
                }
            } catch (e: CancellationException) {
                throw e // cancelled on purpose (newer choice, or screen closed): not an error
            } catch (e: IOException) {
                binding.tvSummary.text = "Can't reach the server"
            } catch (e: Exception) {
                binding.tvSummary.text = e.message ?: "Something went wrong"
            }
        }
    }

    // ---------- food tab ----------

    private fun showFood(h: MealHistoryOut) {
        if (h.entries.isEmpty()) {
            binding.tvSummary.text = "No meals logged in the last ${h.days} days."
            return
        }
        val t = h.totals
        binding.tvSummary.text =
            "${plural(t.meals, "meal")} over ${plural(t.days_logged, "day")} • ${num(t.kcal)} kcal in total\n" +
                "Average ${num(t.avg_kcal_per_logged_day)} kcal on the days you logged food"

        // The server sends newest first; groupBy keeps that order, so the newest day comes first.
        for ((date, meals) in h.entries.groupBy { it.date }) {
            binding.llHistory.addView(heading("${dayLabel(date)} • ${num(meals.sumOf { it.kcal })} kcal"))
            val inEatingOrder = meals.sortedBy { m -> mealOrder.indexOf(m.meal_type).let { if (it < 0) 99 else it } }
            for (m in inEatingOrder) {
                binding.llHistory.addView(
                    row(
                        title = m.name,
                        sub = m.meal_type.replaceFirstChar(Char::uppercase) + sourceTag(m.source),
                        amount = "${m.kcal} kcal"
                    )
                )
            }
        }
    }

    // ---------- workouts tab ----------

    private fun showWorkouts(h: WorkoutHistoryOut) {
        if (h.entries.isEmpty()) {
            binding.tvSummary.text = "No workouts logged in the last ${h.days} days."
            return
        }
        val t = h.totals
        binding.tvSummary.text =
            "${plural(t.sessions, "session")} • ${num(t.minutes)} min • ${num(t.kcal_burned)} kcal burned"

        for ((date, sessions) in h.entries.groupBy { it.date }) {
            binding.llHistory.addView(heading("${dayLabel(date)} • ${sessions.sumOf { it.duration_min }} min"))
            for (w in sessions) {
                binding.llHistory.addView(
                    row(
                        title = w.name,
                        sub = "${w.duration_min} min • ${w.workout_type}" + sourceTag(w.source),
                        amount = "−${w.kcal_burned} kcal"
                    )
                )
            }
        }
    }

    // ---------- small helpers ----------

    /** "Today", "Yesterday", or e.g. "Tue 17 Nov". */
    private fun dayLabel(isoDate: String): String {
        val today = Calendar.getInstance()
        if (isoDate == iso.format(today.time)) return "Today"
        today.add(Calendar.DAY_OF_YEAR, -1)
        if (isoDate == iso.format(today.time)) return "Yesterday"
        return try {
            iso.parse(isoDate)?.let { pretty.format(it) } ?: isoDate
        } catch (e: Exception) {
            isoDate // show the raw date rather than crash on an unexpected format
        }
    }

    /** Shows where a number came from, so the user can tell typed values from AI estimates. */
    private fun sourceTag(source: String) = when (source) {
        "ai_estimate" -> " • AI estimate"
        "ai_plan" -> " • from plan"
        "photo" -> " • from photo"
        else -> ""
    }

    private fun plural(n: Int, word: String) = "${num(n)} $word" + if (n == 1) "" else "s"

    /** 1780 -> "1,780" */
    private fun num(n: Int) = String.format(Locale.getDefault(), "%,d", n)

    /** Reuses the Food log's row card, with its button hidden because this screen is read-only. */
    private fun row(title: String, sub: String, amount: String): View {
        val row = layoutInflater.inflate(R.layout.item_meal_row, binding.llHistory, false)
        row.findViewById<TextView>(R.id.tvRowTitle).text = title
        row.findViewById<TextView>(R.id.tvRowSub).text = sub
        row.findViewById<TextView>(R.id.tvRowKcal).text = amount
        row.findViewById<MaterialButton>(R.id.btnRowAction).visibility = View.GONE
        return row
    }

    private fun heading(text: String) = TextView(requireContext()).apply {
        setText(text)
        setTextAppearance(R.style.TextHeading)
        setPadding(0, 12.dp(), 0, 4.dp())
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Optional navigation argument: pass ARG_KIND = KIND_WORKOUTS to open on the Workouts tab. */
        const val ARG_KIND = "kind"
        const val KIND_WORKOUTS = "workouts"

        private const val KEY_KIND = "history_kind"
        private const val KEY_DAYS = "history_days"
    }
}
