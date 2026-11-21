package com.example.capstoneproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstoneproject.databinding.ActivitySignupBinding
import com.example.capstoneproject.network.ApiClient
import com.example.capstoneproject.network.LoginRequest
import com.example.capstoneproject.network.RegisterRequest
import com.example.capstoneproject.testing.AuthValidator
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

        // Validation rules live in AuthValidator (unit-tested in app/src/test, 17 cases).
        AuthValidator.validateSignup(name, email, password, confirmPassword)?.let { showError(it); return }

        // Backend wants first_name + last_name; split on the first space.
        val (firstName, lastName) = AuthValidator.splitName(name)

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
                    // Step 2 of sign-up: log in straight away and collect the health profile,
                    // so the account and its health data are created together.
                    val login = ApiClient.api.login(LoginRequest(email = email, password = password))
                    val token = login.body()?.access_token
                    if (login.isSuccessful && token != null) {
                        ApiClient.saveToken(token)
                        getSharedPreferences("AppPreferences", MODE_PRIVATE).edit().putBoolean("isLoggedIn", true).apply()
                        Toast.makeText(this@SignupActivity, "Account created! One more step.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignupActivity, ProfileSetupActivity::class.java))
                        finish()
                    } else {
                        showSuccessMessage()
                        navigateToLogin()
                    }
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
