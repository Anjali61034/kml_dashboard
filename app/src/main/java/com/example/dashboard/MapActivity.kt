package com.example.dashboard

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.canary.NavigineSdkManager
import com.navigine.idl.java.Location
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.Position
import com.navigine.idl.java.PositionListener
import com.navigine.view.LocationView

class MapActivity : AppCompatActivity() {

    private lateinit var locationView: LocationView
    private var locationId: Int = -1
    private var sublocationId: Int = -1
    private var venueId: Int = -1
    private var venueName: String = ""

    // Store listeners for proper cleanup
    private var locationListener: LocationListener? = null
    private var positionListener: PositionListener? = null

    companion object {
        private const val TAG = "MapActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Map View"

        // Get parameters from intent extras
        intent.extras?.let {
            locationId = it.getInt("locationId", -1)
            sublocationId = it.getInt("sublocationId", -1)
            venueId = it.getInt("venueId", -1)
            venueName = it.getString("venueName", "")

            // Update the action bar title if venue name is available
            if (venueName.isNotEmpty()) {
                supportActionBar?.title = venueName
            }
        }

        if (locationId == -1) {
            Toast.makeText(this, "Invalid location ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        locationView = findViewById(R.id.location_view)

        // Set up back button
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }

        // Load the selected location
        loadLocation()
    }

    private fun loadLocation() {
        try {
            // Clean up any existing listeners
            cleanupListeners()

            val locationManager = NavigineSdkManager.locationManager

            // Add location listener
            locationListener = object : LocationListener() {
                override fun onLocationLoaded(location: Location) {
                    runOnUiThread {
                        Log.d(TAG, "Location loaded: ${location.getName()}")

                        // Get location view controller
                        val controller = locationView.locationWindow

                        // Set sublocation ID if specified, otherwise default to first sublocation
                        if (sublocationId != -1) {
                            controller.setSublocationId(sublocationId)
                            Log.d(TAG, "Setting sublocation ID: $sublocationId")
                        } else if (location.getSublocations().isNotEmpty()) {
                            controller.setSublocationId(location.getSublocations()[0].getId())
                            Log.d(TAG, "Setting default sublocation ID: ${location.getSublocations()[0].getId()}")
                        }

                        // If we have a specific venue ID, try to highlight or zoom to it
                        if (venueId != -1) {
                            // Attempt to zoom to the venue (implementation depends on SDK capabilities)
                            // This is just a placeholder - actual implementation depends on what the SDK allows
                            Log.d(TAG, "Would highlight venue ID: $venueId")
                            // In a real implementation, you'd use SDK-specific methods here
                        }
                    }
                }

                override fun onLocationFailed(locationId: Int, error: Error) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MapActivity,
                            "Failed to load location: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "Failed to load location $locationId: ${error.message}")
                    }
                }

                override fun onLocationUploaded(locationId: Int) {
                    Log.d(TAG, "Location uploaded: $locationId")
                }
            }

            // Add the listener
            locationManager.addLocationListener(locationListener)

            // Set location ID to load - this will trigger a fresh location load
            Log.d(TAG, "Loading location with ID: $locationId")
            locationManager.setLocationId(locationId)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading location: ${e.message}", e)
            Toast.makeText(this, "Error loading map: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun cleanupListeners() {
        try {
            // Remove position listener
            positionListener?.let {
                NavigineSdkManager.navigationManager.removePositionListener(it)
                positionListener = null
            }

            // Remove location listener
            locationListener?.let {
                NavigineSdkManager.locationManager.removeLocationListener(it)
                locationListener = null
            }

            // Stop navigation
            NavigineSdkManager.navigationManager.stopLogRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up: ${e.message}", e)
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