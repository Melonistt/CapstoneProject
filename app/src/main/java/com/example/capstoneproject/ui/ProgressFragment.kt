package com.example.capstoneproject.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.capstoneproject.R
import com.example.capstoneproject.databinding.FragmentProgressBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.WeightLogIn
import com.example.capstoneproject.ui.charts.WeekBarChartView
import com.example.capstoneproject.ui.charts.WeightLineChartView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Progress tab (instructor focus item "Tracking progress — graph or timeline").
 *  - 7-day calories eaten vs burned bar chart from GET /log/summary, with today's target line.
 *  - Weight trend from GET /log/weight, weigh-in entry via POST /log/weight (also updates the profile
 *    weight on the server so the calorie target follows the user).
 */
class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayLabel = SimpleDateFormat("EEE", Locale.getDefault())
    private val shortDate = SimpleDateFormat("d MMM", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogWeight.setOnClickListener { logWeight() }
        binding.btnOpenHistory.setOnClickListener { findNavController().navigate(R.id.historyFragment) }
        loadAll()
    }

    override fun onResume() {
        super.onResume()
        loadAll()
    }

    private fun loadAll() {
        loadWeek()
        loadWeight()
    }

    // ---------- week ----------

    private fun loadWeek() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = ApiClient.api.summary(7)
                val today = ApiClient.api.dayLog(null)
                val days = summary.body()
                if (!summary.isSuccessful || days == null) {
                    binding.tvWeekNote.text = ApiClient.errorMessage(summary); return@launch
                }
                val target = today.body()?.calorie_target
                binding.chartWeek.setData(
                    days.map { d ->
                        val label = try { dayLabel.format(iso.parse(d.date)!!) } catch (e: Exception) { d.date.takeLast(2) }
                        WeekBarChartView.Day(label, d.kcal, d.kcal_burned)
                    },
                    target
                )
                val loggedDays = days.count { it.meals > 0 }
                val eatenTotal = days.sumOf { it.kcal }
                val burnedTotal = days.sumOf { it.kcal_burned }
                val workouts = days.sumOf { it.workouts }
                binding.tvWorkoutsWeek.text = workouts.toString()
                binding.tvBurnedWeek.text = "%,d".format(burnedTotal)
                binding.tvAvgEaten.text = if (loggedDays > 0) "%,d".format(eatenTotal / loggedDays) else "—"
                binding.tvWeekNote.text = when {
                    loggedDays == 0 -> "No meals logged this week yet — log a meal to start the chart."
                    target == null -> "$loggedDays day${if (loggedDays == 1) "" else "s"} logged. Set your profile to see your target line."
                    else -> {
                        val avgNet = (eatenTotal - burnedTotal) / loggedDays
                        val diff = avgNet - target
                        "$loggedDays day${if (loggedDays == 1) "" else "s"} logged • average net ${"%,d".format(avgNet)} kcal, " +
                            (if (diff <= 0) "${"%,d".format(-diff)} under" else "${"%,d".format(diff)} over") + " your target."
                    }
                }
            } catch (e: IOException) {
                binding.tvWeekNote.text = "Can't reach the server"
            } catch (e: Exception) {
                binding.tvWeekNote.text = "Error: ${e.message}"
            }
        }
    }

    // ---------- weight ----------

    private fun loadWeight() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api.weightHistory(90)
                val h = resp.body()
                if (!resp.isSuccessful || h == null) { toast(ApiClient.errorMessage(resp)); return@launch }

                binding.tvWeightStart.text = h.start_kg?.let { fmt(it) } ?: "—"
                binding.tvWeightCurrent.text = h.latest_kg?.let { fmt(it) } ?: "—"
                binding.tvWeightChange.text = h.change_kg?.let { c ->
                    (if (c > 0) "+" else "") + fmt(c)
                } ?: "—"

                binding.chartWeight.setData(h.entries.map { e ->
                    val label = try { shortDate.format(iso.parse(e.date)!!) } catch (ex: Exception) { e.date }
                    WeightLineChartView.Point(label, e.weight_kg)
                })

                binding.llWeighIns.removeAllViews()
                for (e in h.entries.asReversed().take(5)) {
                    val row = layoutInflater.inflate(R.layout.item_meal_row, binding.llWeighIns, false)
                    row.findViewById<TextView>(R.id.tvRowTitle).text = fmt(e.weight_kg)
                    row.findViewById<TextView>(R.id.tvRowSub).text = e.date + (e.notes?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "")
                    row.findViewById<TextView>(R.id.tvRowKcal).text = ""
                    val btn = row.findViewById<MaterialButton>(R.id.btnRowAction)
                    btn.text = "Delete"
                    btn.setOnClickListener {
                        btn.isEnabled = false
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val r = ApiClient.api.deleteWeight(e.id)
                                if (r.isSuccessful) loadWeight() else toast(ApiClient.errorMessage(r))
                            } catch (ex: IOException) { toast("Can't reach the server") }
                            finally { if (isAdded) btn.isEnabled = true }
                        }
                    }
                    binding.llWeighIns.addView(row)
                }
            } catch (e: IOException) {
                toast("Can't reach the server")
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            }
        }
    }

    private fun logWeight() {
        val kg = binding.etWeightToday.text?.toString()?.trim()?.toDoubleOrNull()
        if (kg == null || kg < 30 || kg > 300) { toast("Enter a weight between 30 and 300 kg"); return }
        binding.btnLogWeight.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = ApiClient.api.addWeight(WeightLogIn(weight_kg = kg))
                if (resp.isSuccessful) {
                    binding.etWeightToday.setText("")
                    toast("Logged ${fmt(kg)} — profile weight updated")
                    loadAll()
                } else toast("Save failed: ${ApiClient.errorMessage(resp)}")
            } catch (e: IOException) {
                toast("Can't reach the server")
            } finally {
                if (isAdded) binding.btnLogWeight.isEnabled = true
            }
        }
    }

    private fun fmt(kg: Double) = if (kg == kg.toLong().toDouble()) "${kg.toLong()} kg" else String.format("%.1f kg", kg)
    private fun toast(msg: String) { if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
