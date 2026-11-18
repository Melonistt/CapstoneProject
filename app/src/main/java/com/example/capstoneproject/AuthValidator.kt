package com.example.capstoneproject.testing

object AuthValidator {

    // LOGIN VALIDATION
    fun validateLogin(email: String, password: String): String? {

        val cleanEmail = email.trim()

        return when {
            cleanEmail.isBlank() -> "Email is required"

            !isValidEmail(cleanEmail) ->
                "Please enter a valid email"

            password.isBlank() ->
                "Password is required"

            password.length < 8 ->
                "Password must be at least 8 characters"

            else -> null
        }
    }

    // SIGN-UP VALIDATION
    fun validateSignup(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): String? {

        val cleanName = name.trim()
        val cleanEmail = email.trim()

        return when {
            cleanName.isEmpty() ->
                "Name is required"

            cleanEmail.isEmpty() ->
                "Email is required"

            !isValidEmail(cleanEmail) ->
                "Please enter a valid email address"

            password.isEmpty() ->
                "Password is required"

            password.length < 8 ->
                "Password must be at least 8 characters"

            !password.any { it.isDigit() } ->
                "Password must contain at least one number"

            !password.any { !it.isLetterOrDigit() } ->
                "Password must contain at least one special character"

            confirmPassword.isEmpty() ->
                "Please confirm your password"

            password != confirmPassword ->
                "Passwords do not match"

            else -> null
        }
    }

    // NAME PROCESSING
    fun splitName(name: String): NameParts {

        val trimmed = name.trim()

        val firstName = trimmed.substringBefore(" ")

        val lastName = trimmed.substringAfter(" ", "").ifBlank { "-" }

        return NameParts(firstName, lastName)
    }

    // EMAIL VALIDATION
    private fun isValidEmail(email: String): Boolean {

        val expression = Regex(
            "^[A-Za-z0-9.!#\\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$"
        )

        return expression.matches(email)
    }
}

// NAME DATA CLASS
data class NameParts(
    val firstName: String,
    val lastName: String
)
