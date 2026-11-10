package com.example.capstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.databinding.ActivitySignupBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.RegisterRequest
import kotlinx.coroutines.launch
import java.io.IOException

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignUp.setOnClickListener { handleSignUp() }
        binding.tvBackToLogin.setOnClickListener { navigateToLogin() }
    }

    private fun handleSignUp() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (!validateInputs(name, email, password, confirmPassword)) return

        // Backend wants first_name + last_name; split on the first space.
        val firstName = name.substringBefore(" ")
        val lastName = name.substringAfter(" ", "").ifBlank { "-" }

        binding.btnSignUp.isEnabled = false
        binding.btnSignUp.text = "Creating Account..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.register(
                    RegisterRequest(email = email, password = password, first_name = firstName, last_name = lastName)
                )
                if (response.isSuccessful) {
                    // Remember the display name for the Home screen greeting
                    getSharedPreferences("AppPreferences", MODE_PRIVATE).edit()
                        .putString("user_name", name)
                        .putString("email", email)
                        .apply()
                    showSuccessMessage()
                    navigateToLogin()
                } else {
                    // 409 email already registered, 422 password policy
                    showError(ApiClient.errorMessage(response))
                }
            } catch (e: IOException) {
                showError("Can't reach the server. Is the backend running? (${e.message})")
            } finally {
                binding.btnSignUp.isEnabled = true
                binding.btnSignUp.text = "Sign Up"
            }
        }
    }

    /** Mirrors the backend policy (schemas.py): 8+ chars, 1 number, 1 special character. */
    private fun validateInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        when {
            name.isEmpty() -> { showError("Name is required"); return false }
            email.isEmpty() -> { showError("Email is required"); return false }
            !isValidEmail(email) -> { showError("Please enter a valid email address"); return false }
            password.isEmpty() -> { showError("Password is required"); return false }
            password.length < 8 -> { showError("Password must be at least 8 characters"); return false }
            !password.any { it.isDigit() } -> { showError("Password must contain at least one number"); return false }
            !password.any { !it.isLetterOrDigit() } -> { showError("Password must contain at least one special character"); return false }
            confirmPassword.isEmpty() -> { showError("Please confirm your password"); return false }
            password != confirmPassword -> { showError("Passwords do not match"); return false }
        }
        return true
    }

    private fun isValidEmail(email: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccessMessage() {
        Toast.makeText(this, "Account created! Please log in.", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
