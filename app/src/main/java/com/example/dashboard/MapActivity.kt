package com.example.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.canary.NavigineSdkManager
import com.navigine.idl.java.Location
import com.navigine.idl.java.LocationListener
import com.navigine.view.LocationView
import com.example.canary.NavigineSdkManager.locationManager

class MapActivity : AppCompatActivity() {

    private lateinit var locationView: LocationView
    private var locationId: Int = -1
    private var sublocationId: Int = -1
    private var venueId: Int = -1
    private var venueName: String = ""

    // Store listener for cleanup
    private var locationListener: LocationListener? = null

    companion object {
        private const val TAG = "MapActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Get parameters
        intent.extras?.let {
            locationId = it.getInt("locationId", -1)
            sublocationId = it.getInt("sublocationId", -1)
            venueId = it.getInt("venueId", -1)
            venueName = it.getString("venueName", "")

            if (venueName.isNotEmpty()) {
                supportActionBar?.title = venueName
            }
        }

        // Initialize views
        locationView = findViewById(R.id.location_view)
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }

        // Check permission
        if (checkLocationPermission()) {
            loadMapAndSdk()
        } else {
            requestLocationPermission()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMapAndSdk()
            } else {
                Toast.makeText(this, "Permission required to load map", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun loadMapAndSdk() {
        try {
            // Initialize Navigine SDK
            NavigineSdkManager.initialize(this)
            Log.d(TAG, "Navigine SDK initialized")
            // Load location after SDK init
            loadLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SDK: ${e.message}")
            Toast.makeText(this, "Error initializing SDK", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadLocation() {
        try {
            val locationManager = NavigineSdkManager.locationManager

            // Remove previous listener if any
            cleanupListeners()

            // Set up location listener
            locationListener = object : LocationListener() {
                override fun onLocationLoaded(location: Location) {
                    runOnUiThread {
                        Log.d(TAG, "Location loaded: ${location.getName()}")
                        // Set location ID to load the specific location
                        locationManager.setLocationId(locationId)
                        // Load location view
                        setupLocationView(location)
                    }
                }

                override fun onLocationFailed(locationId: Int, error: Error) {
                    runOnUiThread {
                        Toast.makeText(this@MapActivity, "Failed to load location: ${error.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to load location: ${error.message}")
                    }
                }

                override fun onLocationUploaded(locationId: Int) {
                    Log.d(TAG, "Location uploaded: $locationId")
                }
            }

            // Add listener
            locationManager.addLocationListener(locationListener)

            // Trigger load location
            Log.d(TAG, "Requesting location with ID: $locationId")
            locationManager.locationId

        } catch (e: Exception) {
            Log.e(TAG, "Error loading location: ${e.message}")
            Toast.makeText(this, "Error loading location", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupLocationView(location: Location) {
        // Set sublocation if provided
        if (sublocationId != -1 && location.getSublocations().isNotEmpty()) {
            val sublocation = location.getSublocations().find { it.getId() == sublocationId }
            if (sublocation != null) {
                // Assuming your LocationView supports setting sublocation
                locationView.locationWindow.setSublocationId(sublocation.getId())
            }
        } else if (location.getSublocations().isNotEmpty()) {
            // Default to first sublocation
            locationView.locationWindow.setSublocationId(location.getSublocations()[0].getId())
        }

        // Additional setup: e.g., zoom, highlight venue, etc.
        // For example, if SDK supports zoom to venue, implement here
    }

    private fun cleanupListeners() {
        try {
            val locationManager = NavigineSdkManager.locationManager
            locationListener?.let {
                locationManager.removeLocationListener(it)
                locationListener = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}