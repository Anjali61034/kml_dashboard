package com.example.canary

import android.app.Application
import android.util.DisplayMetrics
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.navigine.sdk.Navigine

class NavigineApplication : Application(), LifecycleObserver {

    companion object {
        lateinit var appContext: Application
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        // Initialize display metrics if needed
        val displayMetrics = resources.displayMetrics

        // Initialize Navigine
        Navigine.initialize(applicationContext)

        // Add lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onEnterForeground() {
        try {
            Navigine.setMode(Navigine.Mode.NORMAL)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet")
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onResume() {
        try {
            Navigine.setMode(Navigine.Mode.NORMAL)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet")
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun onPause() {
        try {
            Navigine.setMode(Navigine.Mode.BACKGROUND)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet")
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onEnterBackground() {
        try {
            Navigine.setMode(Navigine.Mode.BACKGROUND)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet")
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onDestroy() {
        try {
            Navigine.setMode(Navigine.Mode.BACKGROUND)
        } catch (e: Throwable) {
            Log.e("NavigineSDK", "Navigine SDK is not initialized yet")
        }
    }
}