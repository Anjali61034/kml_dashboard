package com.example.dashboard

import android.app.Application
import android.util.DisplayMetrics
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.navigine.sdk.Navigine

class NavigineApplication : Application(), DefaultLifecycleObserver {

    companion object {
        lateinit var appContext: Application
    }

    override fun onCreate() {
        super<Application>.onCreate()
        appContext = this

        // Initialize display metrics if needed
        val displayMetrics = resources.displayMetrics

        // Initialize Navigine
        Navigine.initialize(applicationContext)

        // Add lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        try {
            Navigine.setMode(Navigine.Mode.NORMAL)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet", e)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        try {
            Navigine.setMode(Navigine.Mode.NORMAL)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet", e)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        try {
            Navigine.setMode(Navigine.Mode.BACKGROUND)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet", e)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        try {
            Navigine.setMode(Navigine.Mode.BACKGROUND)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet", e)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        try {
            Navigine.setMode(Navigine.Mode.BACKGROUND)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet", e)
        }
    }
}