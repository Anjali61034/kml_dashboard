package com.example.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
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
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.example.canary.NavigineSdkManager
import com.google.android.gms.location.Priority
import android.speech.RecognizerIntent
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.SearchView
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private lateinit var speechRecognitionLauncher: ActivityResultLauncher<Intent>

    // Initialize location request
    private lateinit var locationRequest: LocationRequest

    private var userLatitude = 0.0
    private var userLongitude = 0.0
    private var isLocationCanaryConnected = false

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val recordAudioPermissionRequestCode = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Initialize speech recognition launcher
        speechRecognitionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val spokenText: ArrayList<String>? = result.data?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                spokenText?.get(0)?.let { text ->
                    binding.searchView.setQuery(text, false) // Assuming searchView is in the binding
                }
            }
        }

        // Initialize location manager
        locationManager = LocationManager(this)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create LocationRequest using the Builder
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(10f)  // Only update if the user has moved 10 meters
            .setMaxUpdateDelayMillis(5000)  // Fastest interval for updates (5 seconds)
            .build()

        // Initialize Navigine SDK Manager
        initializeNavigine()

        // Set up map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up suggestion cards
        setupSuggestionCards()

        // Request location permissions
        requestLocationPermissions()

        binding.micButton.setOnClickListener {
            // Check for audio recording permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    recordAudioPermissionRequestCode
                )
            } else {
                // Permission already granted, start speech recognition
                startSpeechRecognition()
            }
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Handle search here
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }

        try {
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speech recognition not supported on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
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
        } else if (requestCode == recordAudioPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Audio recording permission denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
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

                // Fetch address details based on user location
                fetchAddressDetails(userLatitude, userLongitude)

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

    private fun fetchAddressDetails(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        Thread {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.let { processAddresses(it) } // Safe call to process addresses if not null
        }.start()
    }

    private fun processAddresses(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            // Get location name (could be locality, subLocality, or thoroughfare)
            val locationName = address.featureName ?: address.locality ?: address.subLocality ?: address.thoroughfare ?: "Unknown Location"

            // Update UI with location name
            runOnUiThread {
                binding.locationNameText.text = locationName

                // Update location info text based on Canary connection status
                updateLocationInfo()
            }
        }
    }

    private fun updateLocationInfo() {
        // Example implementation for updating location info. Adjust as necessary.
        binding.locationInfoText.text = if (isLocationCanaryConnected) {
            "This location is Canary Connected!"
        } else {
            "This location is not Canary Connected"
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
        if (userLatitude == 0.0 && userLongitude == 0.0) {
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
            // User is not in a known location
            val currentLocationString = "Your current location: Latitude: $userLatitude, Longitude: $userLongitude"
            Log.d(TAG, "User is not in any known location. $currentLocationString")
            updateLocationUI(currentLocationString)
        }
    }

    private fun updateLocationUI(locationInfo: Any?) {
        if (locationInfo is LocationManager.LocationInfo) {
            if (isLocationCanaryConnected) {
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = locationInfo.name
                binding.locationInfoText.text = "This location is Canary Connected!"
                binding.locationInfoCardView.setCardBackgroundColor(0xFFE6FFB3.toInt())
                binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_green)
                binding.canaryStatusText.text = "Status: Connected"
                binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
            } else {
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = locationInfo.name
                binding.locationInfoText.text = "This location is not Canary Connected"
                binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt())
                binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
                binding.canaryStatusText.text = "Status: Disconnected"
                binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
            }
        } else if (locationInfo is String) {
            binding.locationNameText.text = locationInfo
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

    private fun initializeNavigine() {
        try {
            // Initialize SDK Manager
            NavigineSdkManager.initialize(this)
            Log.d("Navigine", "SDK initialized successfully")
        } catch (e: Exception) {
            Log.e("Navigine", "Error initializing SDK: ${e.message}")
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