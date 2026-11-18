package com.example.capstoneproject.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    // 1. LOGIN TESTS

    @Test
    fun loginRejectsBlankEmail() {
        assertEquals(
            "Email is required",
            AuthValidator.validateLogin(" ", "Password1!")
        )
    }

    @Test
    fun loginRejectsInvalidEmail() {
        assertEquals(
            "Please enter a valid email",
            AuthValidator.validateLogin("not-an-email", "Password1!")
        )
    }

    @Test
    fun loginRejectsBlankPassword() {
        assertEquals(
            "Password is required",
            AuthValidator.validateLogin("person@example.com", " ")
        )
    }

    @Test
    fun loginRejectsShortPassword() {
        assertEquals(
            "Password must be at least 8 characters",
            AuthValidator.validateLogin("person@example.com", "short7!")
        )
    }

    @Test
    fun loginAcceptsValidDetails() {
        assertNull(
            AuthValidator.validateLogin("person@example.com", "Password1!")
        )
    }

    // 2. SIGN-UP TESTS

    @Test
    fun signupRejectsBlankName() {
        assertEquals(
            "Name is required",
            AuthValidator.validateSignup("", "person@example.com", "Password1!", "Password1!")
        )
    }

    @Test
    fun signupRejectsBlankEmail() {
        assertEquals(
            "Email is required",
            AuthValidator.validateSignup("Alex Smith", "", "Password1!", "Password1!")
        )
    }

    @Test
    fun signupRejectsInvalidEmail() {
        assertEquals(
            "Please enter a valid email address",
            AuthValidator.validateSignup("Alex Smith", "invalid", "Password1!", "Password1!")
        )
    }

    @Test
    fun signupRejectsBlankPassword() {
        assertEquals(
            "Password is required",
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "", "")
        )
    }

    @Test
    fun signupRejectsShortPassword() {
        assertEquals(
            "Password must be at least 8 characters",
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "Ab1!", "Ab1!")
        )
    }

    @Test
    fun signupRejectsPasswordWithoutNumber() {
        assertEquals(
            "Password must contain at least one number",
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "Password!", "Password!")
        )
    }

    @Test
    fun signupRejectsPasswordWithoutSpecialCharacter() {
        assertEquals(
            "Password must contain at least one special character",
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "Password1", "Password1")
        )
    }

    @Test
    fun signupRejectsMissingConfirmation() {
        assertEquals(
            "Please confirm your password",
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "Password1!", "")
        )
    }

    @Test
    fun signupRejectsMismatchedPasswords() {
        assertEquals(
            "Passwords do not match",
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "Password1!", "Password2!")
        )
    }

    @Test
    fun signupAcceptsValidDetails() {
        assertNull(
            AuthValidator.validateSignup("Alex Smith", "person@example.com", "Password1!", "Password1!")
        )
    }

    // 3. NAME PROCESSING TESTS

    @Test
    fun splitNameSeparatesFirstAndLastName() {
        assertEquals(
            NameParts("Alex", "Smith"),
            AuthValidator.splitName("Alex Smith")
        )
    }

    @Test
    fun splitNameUsesHyphenWhenLastNameIsMissing() {
        assertEquals(
            NameParts("Alex", "-"),
            AuthValidator.splitName("Alex")
        )
    }
}
