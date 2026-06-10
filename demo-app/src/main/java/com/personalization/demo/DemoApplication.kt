package com.personalization.demo

import androidx.multidex.MultiDexApplication
import com.google.firebase.FirebaseApp
import com.personalization.SDK
import com.personalization.demo.httplogger.HttpLogStore

class DemoApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        // Capture all SDK HTTP traffic for the in-app HTTP log screen (debug aid).
        SDK.networkLogger = HttpLogStore
        // Initialize Firebase if not already initialized
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}

