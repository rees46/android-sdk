package com.personalization.demo

import android.content.Context
import androidx.multidex.MultiDexApplication
import com.google.firebase.FirebaseApp

class DemoApplication : MultiDexApplication() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase if not already initialized
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}

