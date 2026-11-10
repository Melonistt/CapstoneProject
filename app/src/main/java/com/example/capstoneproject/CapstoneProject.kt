package com.example.capstoneproject

import android.app.Application
import com.example.capstoneproject.network.ApiClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CapstoneProject : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}
