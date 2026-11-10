package com.example.capstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.databinding.ActivityLoginBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.LoginRequest
import kotlinx.coroutines.launch
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Already have a token from a previous login? Skip straight to the app.
        if (ApiClient.getToken() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding.forgotPasswordLink.setOnClickListener { onForgotPasswordClick() }
        binding.loginButton.setOnClickListener { validateAndLogin() }
        binding.createAccountButton.setOnClickListener { onCreateAccountClick() }
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.emailInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.emailInputLayout.error = null
        }
        binding.passwordInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.passwordInputLayout.error = null
        }
    }

    private fun validateAndLogin() {
        var isValid = true

        val email = binding.emailInput.text.toString().trim()
        if (email.isBlank()) {
            binding.emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            binding.emailInputLayout.error = "Please enter a valid email"
            isValid = false
        }

        val password = binding.passwordInput.text.toString()
        if (password.isBlank()) {
            binding.passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 8) {
            binding.passwordInputLayout.error = "Password must be at least 8 characters"
            isValid = false
        }

        if (isValid) performLogin(email, password)
    }

    private fun isValidEmail(email: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Calls POST /auth/login on the FastAPI backend and stores the bearer token. */
    private fun performLogin(email: String, password: String) {
        binding.loginButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    ApiClient.saveToken(response.body()!!.access_token)

                    // Keep the email for the Home/Profile screens
                    getSharedPreferences("AppPreferences", MODE_PRIVATE).edit()
                        .putBoolean("isLoggedIn", true)
                        .putString("email", email)
                        .apply()

                    Toast.makeText(this@LoginActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    // 401 wrong password, 423 locked out, 422 validation
                    binding.passwordInputLayout.error = ApiClient.errorMessage(response)
                }
            } catch (e: IOException) {
                Toast.makeText(
                    this@LoginActivity,
                    "Can't reach the server. Is the backend running? (${e.message})",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.loginButton.isEnabled = true
            }
        }
    }

    private fun onForgotPasswordClick() {
        Toast.makeText(this, "Password reset is not available in this prototype", Toast.LENGTH_SHORT).show()
    }

    private fun onCreateAccountClick() {
        startActivity(Intent(this, SignupActivity::class.java))
    }
}
