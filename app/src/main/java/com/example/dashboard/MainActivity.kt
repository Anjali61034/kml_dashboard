package com.example.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.canary.NavigineSdkManager
import com.example.dashboard.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.navigine.idl.java.Location as NavigineLocation // Alias to avoid conflict
import com.navigine.idl.java.LocationInfo
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.LocationListListener
import com.navigine.idl.java.Venue
import java.io.IOException
import java.util.Locale
import java.util.HashMap

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager // Assuming you have a LocationManager class
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private lateinit var speechRecognitionLauncher: ActivityResultLauncher<Intent>

    // Initialize location request
    private lateinit var locationRequest: LocationRequest

    private var userLatitude = 0.0
    private var userLongitude = 0.0
    private var isLocationCanaryConnected = false
    private var matchedNavigineLocationInfo: LocationInfo? = null // Store the matched Navigine LocationInfo
    private val loadedNavigineLocations = HashMap<Int, NavigineLocation>() // Store loaded Navigine Location details

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val recordAudioPermissionRequestCode = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Initialize Navigine SDK Manager at the very start of onCreate
        initializeNavigineAndLoadLocations()

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

        // Initialize location manager (replace with your implementation if different)
        locationManager = LocationManager(this)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create LocationRequest using the Builder
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(10f)  // Only update if the user has moved 10 meters
            .setMaxUpdateDelayMillis(5000)  // Fastest interval for updates (5 seconds)
            .build()

        // Set up map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up suggestion cards (initial state)
        setDefaultSuggestions()

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

        // Setup click listeners for suggestion cards
        binding.suggestionCard1.setOnClickListener { handleSuggestionCardClick(binding.suggestionText1.text.toString()) }
        binding.suggestionCard2.setOnClickListener { handleSuggestionCardClick(binding.suggestionText2.text.toString()) }
        binding.suggestionCard3.setOnClickListener { handleSuggestionCardClick(binding.suggestionText3.text.toString()) }
        binding.suggestionCard4.setOnClickListener { handleSuggestionCardClick(binding.suggestionText4.text.toString()) }
    }

    private fun handleSuggestionCardClick(suggestionText: String) {
        // Handle the click on a suggestion card, e.g., initiate navigation or display information
        Toast.makeText(this, "Tapped on: $suggestionText", Toast.LENGTH_SHORT).show()
        // You would typically add logic here to perform an action based on the suggestion
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

    private fun updateSuggestionCards(venues: List<Venue>) {
        runOnUiThread {
            // Update up to 4 suggestion cards with venue names
            val maxCards = minOf(venues.size, 4)
            val defaultTexts = arrayOf("Reception", "Office", "Hall", "Doctor") // Fallback texts

            // Safely access venue names and update text views
            binding.suggestionText1.text = venues.getOrNull(0)?.getName() ?: defaultTexts[0]
            binding.suggestionText2.text = venues.getOrNull(1)?.getName() ?: defaultTexts[1]
            binding.suggestionText3.text = venues.getOrNull(2)?.getName() ?: defaultTexts[2]
            binding.suggestionText4.text = venues.getOrNull(3)?.getName() ?: defaultTexts[3]

            Log.d(TAG, "Updated ${maxCards} suggestion cards with venues.")
        }
    }

    private fun setDefaultSuggestions() {
        runOnUiThread {
            // Set default suggestions for the cards
            val defaultTexts = arrayOf("Reception", "Office", "Hall", "Doctor")
            binding.suggestionText1.text = defaultTexts[0]
            binding.suggestionText2.text = defaultTexts[1]
            binding.suggestionText3.text = defaultTexts[2]
            binding.suggestionText4.text = defaultTexts[3]

            // Assuming you have default icons set in your XML
            // binding.suggestionIcon1.setImageResource(R.drawable.default_icon) // Example
            Log.d(TAG, "Set default suggestions")
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
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
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
            recordAudioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSpeechRecognition()
                } else {
                    Toast.makeText(this, "Audio recording permission denied.", Toast.LENGTH_SHORT).show()
                }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Use Build.VERSION_CODES for Android 13+
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    processAddresses(addresses)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (addresses != null) {
                    processAddresses(addresses)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
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

            // Add location polygons (using data from your LocationManager if available)
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
        matchedNavigineLocationInfo = null // Reset matched location info

        // Make sure we have valid user coordinates
        if (userLatitude == 0.0 && userLongitude == 0.0) {
            Log.d(TAG, "Invalid user coordinates")
            updateLocationUI(null)
            setDefaultSuggestions() // Use default suggestions if location is unknown
            return
        }

        // Check if user is within a known location (using your LocationManager's logic)
        val matchedLocation = locationManager.checkUserLocation(userLatitude, userLongitude)

        if (matchedLocation != null) {
            Log.d(TAG, "User is in location: ${matchedLocation.name} (ID: ${matchedLocation.id})")
            isLocationCanaryConnected = matchedLocation.isCanaryConnected
            updateLocationUI(matchedLocation)

            // If connected, try to load Navigine location details and update suggestions
            if (isLocationCanaryConnected) {
                val navigineLocationInfo = NavigineSdkManager.locationListManager.locationList[matchedLocation.id]
                if (navigineLocationInfo != null) {
                    matchedNavigineLocationInfo = navigineLocationInfo
                    loadNavigineLocationDetails(navigineLocationInfo.id)
                } else {
                    Log.w(TAG, "Navigine LocationInfo not found for ID: ${matchedLocation.id}")
                    setDefaultSuggestions() // Fallback to default if Navigine info is missing
                }
            } else {
                setDefaultSuggestions() // Fallback to default if not Canary connected
            }
        } else {
            // User is not in a known location
            val currentLocationString = "Your current location: Latitude: $userLatitude, Longitude: $userLongitude"
            Log.d(TAG, "User is not in any known location. $currentLocationString")
            updateLocationUI(currentLocationString)
            setDefaultSuggestions() // Use default suggestions if location is unknown
        }
    }


    private fun updateLocationUI(locationInfo: Any?) {
        if (locationInfo is LocationManager.LocationInfo) {
            if (isLocationCanaryConnected) {
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = locationInfo.name
                binding.locationInfoText.text = "This location is Canary Connected!"
                binding.locationInfoCardView.setCardBackgroundColor(0xFFE6FFB3.toInt())// Use a color resource
                binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_green)
                binding.canaryStatusText.text = "Status: Connected"
                binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
            } else {
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = locationInfo.name
                binding.locationInfoText.text = "This location is not Canary Connected"
                binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt()) // Use a color resource
                binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
                binding.canaryStatusText.text = "Status: Disconnected"
                binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
            }
        } else if (locationInfo is String) {
            binding.locationStatusText.text = "You're at" // or "Current Location:"
            binding.locationNameText.text = locationInfo
            binding.locationInfoText.text = "This location is not Canary Connected"
            binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt()) // Use a color resource
            binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
            binding.canaryStatusText.text = "Status: Disconnected"
            binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
        } else {
            binding.locationStatusText.text = "You're not at a known location"
            binding.locationNameText.text = "Unknown Area"
            binding.locationInfoText.text = "Move to a Canary Connected location"
            binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt()) // Use a color resource
            binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_gray)
            binding.canaryStatusText.text = "Status: Disconnected"
            binding.canaryStatusIndicator.visibility = android.view.View.VISIBLE
        }
    }


    private fun initializeNavigineAndLoadLocations() {
        try {
            // Initialize SDK Manager
            NavigineSdkManager.initialize(this)
            Log.d("Navigine", "SDK initialized successfully")

            // Load Navigine locations
            loadNavigineLocationList()

        } catch (e: Exception) {
            Log.e("Navigine", "Error initializing SDK: ${e.message}")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Failed to initialize location services.", Toast.LENGTH_LONG).show() // Use LONG for initialization errors
            }
        }
    }

    private fun loadNavigineLocationList() {
        try {
            val locationListManager = NavigineSdkManager.locationListManager

            val locationListListener = object : LocationListListener() {
                override fun onLocationListLoaded(idToLocationInfoMap: HashMap<Int, LocationInfo>) {
                    Log.d(TAG, "Navigine location list loaded. Found ${idToLocationInfoMap.size} locations.")
                    // The location list is now available in NavigineSdkManager.locationListManager.locationList
                    // You can access location information here if needed, but location details are loaded on demand.

                    // Check if user location is already available and re-check Canary connection
                    if (userLatitude != 0.0 && userLongitude != 0.0) {
                        checkIfLocationIsCanaryConnected()
                    }
                    // Consider removing the listener after the first load if you don't need continuous updates
                    locationListManager.removeLocationListListener(this)
                }

                override fun onLocationListFailed(error: Error) {
                    Log.e(TAG, "Failed to load Navigine location list: ${error.message}")
                    // Handle error, e.g., show a message to the user
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to load location data.", Toast.LENGTH_SHORT).show()
                    }
                    // Remove the listener after failure
                    locationListManager.removeLocationListListener(this)
                }
            }

            locationListManager.addLocationListListener(locationListListener)
            locationListManager.updateLocationList() // Start the loading process

        } catch (e: Exception) {
            Log.e(TAG, "Error loading Navigine location list: ${e.message}")
        }
    }

    private fun loadNavigineLocationDetails(locationId: Int) {
        // Check if location details are already loaded
        if (loadedNavigineLocations.containsKey(locationId)) {
            Log.d(TAG, "Navigine location details for ID $locationId already loaded.")
            val location = loadedNavigineLocations[locationId]
            location?.let { processLoadedLocation(it) }
            return
        }

        try {
            // Create a new LocationListener for this specific load request
            val locationListener = object : LocationListener() {
                override fun onLocationLoaded(location: NavigineLocation) {
                    Log.d(TAG, "Navigine location details loaded for ID ${location.getId()}: ${location.getName()}")
                    loadedNavigineLocations[location.getId()] = location
                    processLoadedLocation(location)
                    // Remove this specific listener after successful loading
                    NavigineSdkManager.locationManager.removeLocationListener(this)
                }

                override fun onLocationUploaded(p0: Int) {
                    // Not needed for this use case
                }

                override fun onLocationFailed(locationId: Int, error: java.lang.Error?) {
                    Log.e(TAG, "Failed to load Navigine location details for ID $locationId: ${error?.message}")
                    // Handle error, e.g., show a message or use default suggestions
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to load location details.", Toast.LENGTH_SHORT).show()
                    }
                    setDefaultSuggestions() // Fallback to default suggestions on failure
                    // Remove this specific listener after failure
                    NavigineSdkManager.locationManager.removeLocationListener(this)
                }
            }

            // Add the listener and then set the location ID to trigger the load
            NavigineSdkManager.locationManager.addLocationListener(locationListener)
            NavigineSdkManager.locationManager.setLocationId(locationId)


        } catch (e: Exception) {
            Log.e(TAG, "Error setting Navigine location ID: ${e.message}")
            setDefaultSuggestions() // Fallback to default suggestions on error
        }
    }

    private fun processLoadedLocation(location: NavigineLocation) {
        // Get all sublocations for this location
        val sublocations = location.getSublocations()

        // Find the sublocation relevant to the user's current position (this might need more sophisticated logic)
        // For simplicity, let's assume we want venues from the first sublocation with venues.
        val relevantSublocation = sublocations.firstOrNull { it.getVenues().isNotEmpty() }

        if (relevantSublocation != null) {
            val venues = relevantSublocation.getVenues().toList() as List<Venue> // Cast to List<Venue>
            Log.d(TAG, "Found ${venues.size} venues in sublocation: ${relevantSublocation.getName()}")
            updateSuggestionCards(venues)
        } else {
            Log.d(TAG, "No sublocation with venues found for location: ${location.getName()}")
            setDefaultSuggestions() // Use default suggestions if no venues are found
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

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Navigine SDK resources if necessary
        // NavigineSdkManager.release() // Uncomment if you need to release SDK resources
    }
}