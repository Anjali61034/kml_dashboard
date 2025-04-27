package com.example.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.dashboard.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private var userLatitude = 0.0
    private var userLongitude = 0.0
    private var isLocationCanaryConnected = false

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Initialize location manager
        locationManager = LocationManager(this)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up suggestion cards
        setupSuggestionCards()

        // Request location permissions
        requestLocationPermissions()
    }

    private fun setupSuggestionCards() {
        updateSuggestionCards("FIRST FLOOR")

        // Setup click listeners
        binding.suggestionCard1.setOnClickListener {
            Toast.makeText(this, binding.suggestionText1.text, Toast.LENGTH_SHORT).show()
        }
        binding.suggestionCard2.setOnClickListener {
            Toast.makeText(this, binding.suggestionText2.text, Toast.LENGTH_SHORT).show()
        }
        binding.suggestionCard3.setOnClickListener {
            Toast.makeText(this, binding.suggestionText3.text, Toast.LENGTH_SHORT).show()
        }
        binding.suggestionCard4.setOnClickListener {
            Toast.makeText(this, binding.suggestionText4.text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSuggestionCards(sublocationName: String) {
        val venues = locationManager.getVenuesForSublocation(sublocationName)
        Log.d(TAG, "Using venues from sublocation: $sublocationName")

        // Update the suggestion card UI based on venues
        if (venues.isNotEmpty()) {
            binding.suggestionText1.text = venues.getOrElse(0) { "Reception" }
            binding.suggestionText2.text = venues.getOrElse(1) { "Office" }
            binding.suggestionText3.text = venues.getOrElse(2) { "Hall" }
            binding.suggestionText4.text = venues.getOrElse(3) { "Doctor" }
            Log.d(TAG, "Updated ${venues.size} suggestion cards with venues from location.")
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getLastLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Cannot detect if you're in a Canary connected location.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                userLatitude = it.latitude
                userLongitude = it.longitude

                // Update map with user location
                updateMapWithUserLocation()

                // Check if user is within a known location
                checkIfLocationIsCanaryConnected()
            } ?: run {
                Toast.makeText(
                    this,
                    "Unable to get current location. Please make sure location is enabled.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateMapWithUserLocation() {
        googleMap?.let { map ->
            val userLocation = LatLng(userLatitude, userLongitude)

            // Clear previous markers
            map.clear()

            // Add user marker
            map.addMarker(
                MarkerOptions()
                    .position(userLocation)
                    .title("You are here")
            )

            // Add location polygons
            for (location in locationManager.getAllLocations()) {
                val polygonOptions = PolygonOptions()
                    .clickable(true)
                    .fillColor(0x3300FF00) // Semi-transparent green
                    .strokeColor(0xFF00FF00.toInt()) // Green border
                    .strokeWidth(3f)

                location.polygonPoints.forEach { point ->
                    polygonOptions.add(LatLng(point.latitude, point.longitude))
                }

                map.addPolygon(polygonOptions)
            }

            // Move camera to user location
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 17f))

            // Enable location button
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }
    }

    private fun checkIfLocationIsCanaryConnected() {
        isLocationCanaryConnected = false

        // Make sure we have valid user coordinates
        if (userLatitude == 0.0 || userLongitude == 0.0) {
            Log.d(TAG, "Invalid user coordinates")
            updateLocationUI(null)
            return
        }

        // Check if user is within a known location
        val matchedLocation = locationManager.checkUserLocation(userLatitude, userLongitude)

        if (matchedLocation != null) {
            Log.d(TAG, "User is in location: ${matchedLocation.name} (ID: ${matchedLocation.id})")
            isLocationCanaryConnected = matchedLocation.isCanaryConnected
            updateLocationUI(matchedLocation)

            // Update suggestion cards based on location
            if (matchedLocation.isCanaryConnected) {
                updateSuggestionCards("FIRST FLOOR")
            }
        } else {
            val distance = locationManager.getDistanceToBoundary()
            Log.d(TAG, "User is not in any known location. Nearest boundary: $distance meters")
            updateLocationUI(null)
        }
    }

    private fun updateLocationUI(location: LocationManager.LocationInfo?) {
        if (location != null && isLocationCanaryConnected) {
            binding.locationStatusText.text = "You're at"
            binding.locationNameText.text = location.name
            binding.locationInfoText.text = "This location is Canary Connected!"
            binding.locationInfoCardView.setCardBackgroundColor(0xFFE6FFB3.toInt())
            binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_green)
            binding.canaryStatusText.text = "Status: Connected"
            binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
        } else if (location != null) {
            binding.locationStatusText.text = "You're at"
            binding.locationNameText.text = location.name
            binding.locationInfoText.text = "This location is not Canary Connected"
            binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt())
            binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
            binding.canaryStatusText.text = "Status: Disconnected"
            binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
        } else {
            binding.locationStatusText.text = "You're not at a known location"
            binding.locationNameText.text = "Unknown Area"
            binding.locationInfoText.text = "Move to a Canary Connected location"
            binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt())
            binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_gray)
            binding.canaryStatusText.text = "Status: Disconnected"
            binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configure map settings
        with(map) {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true

            try {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    isMyLocationEnabled = true
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error setting location on map", e)
            }
        }

        // If we already have location, update the map
        if (userLatitude != 0.0 && userLongitude != 0.0) {
            updateMapWithUserLocation()
        } else {
            // Center on St. Stephens Hospital as default
            val defaultLocation = LatLng(28.6668404, 77.2147314)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 17f))
        }
    }
}