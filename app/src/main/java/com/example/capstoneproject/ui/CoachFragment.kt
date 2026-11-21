package com.example.capstoneproject.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.R
import com.example.capstoneproject.databinding.FragmentCoachBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.ChatRequest
import com.example.capstoneproject.network.ChatTurn
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * AI Coach chat screen. Talks to POST /ai/chat and keeps the conversation
 * history in memory for the lifetime of the fragment so the coach can
 * answer follow-up questions in context.
 */
class CoachFragment : Fragment() {

    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!

    private val history = mutableListOf<ChatTurn>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addBubble(
            "Hi! I'm your wellness coach. Ask me about meals, training or " +
                "habits and I'll tailor the answer to your profile.",
            fromUser = false
        )
        binding.btnSend.setOnClickListener { send() }
    }

    private fun send() {
        val text = binding.etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        binding.etMessage.setText("")
        addBubble(text, fromUser = true)
        val thinking = addBubble("…", fromUser = false)
        binding.btnSend.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // History is everything *before* this message.
                val resp = ApiClient.api.chat(ChatRequest(message = text, history = history.toList()))
                if (resp.isSuccessful) {
                    val reply = (resp.body()?.reply ?: "(no reply)").stripMarkdown()
                    thinking.text = reply
                    history += ChatTurn("user", text)
                    history += ChatTurn("assistant", reply)
                } else {
                    val msg = ApiClient.errorMessage(resp)
                    thinking.text = if (resp.code() == 502 || resp.code() == 503)
                        "The coach is unavailable right now ($msg). Try again in a moment."
                    else "Error ${resp.code()}: $msg"
                }
            } catch (e: IOException) {
                thinking.text = "Can't reach the server. Is the backend running (adb reverse)?"
            } catch (e: Exception) {
                thinking.text = "Unexpected error: ${e.message}"
            } finally {
                if (isAdded) {
                    binding.btnSend.isEnabled = true
                    scrollToBottom()
                }
            }
        }
        scrollToBottom()
    }

    /** Inflates item_chat_message.xml, aligns it left/right and returns its TextView. */
    private fun addBubble(text: String, fromUser: Boolean): TextView {
        val row = layoutInflater.inflate(R.layout.item_chat_message, binding.llMessages, false) as LinearLayout
        val card = row.findViewById<MaterialCardView>(R.id.cardBubble)
        val tv = row.findViewById<TextView>(R.id.tvBubble)
        tv.text = text

        val lp = card.layoutParams as LinearLayout.LayoutParams
        if (fromUser) {
            row.gravity = Gravity.END
            lp.marginStart = 48.dp(); lp.marginEnd = 0
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        } else {
            row.gravity = Gravity.START
            lp.marginStart = 0; lp.marginEnd = 48.dp()
        }
        card.layoutParams = lp
        binding.llMessages.addView(row)
        scrollToBottom()
        return tv
    }

    private fun scrollToBottom() {
        binding.svMessages.post { binding.svMessages.fullScroll(View.FOCUS_DOWN) }
    }

    /** Gemini answers in light markdown; TextView shows it raw, so drop the emphasis markers. */
    private fun String.stripMarkdown(): String =
        replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("(?m)^\\s*[\\*-]\\s+"), "• ")
            .replace(Regex("(?m)^#+\\s*"), "")

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
