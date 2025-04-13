package com.example.canary

import com.navigine.idl.java.LocationManager
import com.navigine.idl.java.NavigationManager
import com.navigine.idl.java.MeasurementManager
import com.navigine.idl.java.LocationListManager
import com.navigine.sdk.Navigine
import android.content.Context
import com.navigine.idl.java.NavigineSdk


object NavigineSdkManager {
    // Configuration
    private const val mUserHash = "A9A7-BAAC-6B7F-313D" // Your hash
    private const val mLocationServer = "https://ips.navigine.com"

    private lateinit var mNavigineSdk: NavigineSdk

    // SDK components - use lazy initialization to ensure they're created after SDK is initialized
    val locationListManager: LocationListManager by lazy { mNavigineSdk.getLocationListManager() }
    val locationManager: LocationManager by lazy { mNavigineSdk.getLocationManager() }
    val navigationManager: NavigationManager by lazy { mNavigineSdk.getNavigationManager(locationManager) }
    val measurementManager: MeasurementManager by lazy { mNavigineSdk.getMeasurementManager(locationManager) }

    fun initialize(context: Context) {
        // Initialize Navigine SDK
        Navigine.initialize(context)

        // Get SDK instance
        mNavigineSdk = NavigineSdk.getInstance()

        // Configure SDK
        mNavigineSdk.setUserHash(mUserHash); // your user hash from the server
        mNavigineSdk.setServer(mLocationServer);


    }
}