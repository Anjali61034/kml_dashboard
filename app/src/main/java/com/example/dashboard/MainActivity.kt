// MainActivity.kt
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
import android.view.View
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
import com.example.dashboard.VenueData
import com.google.gson.Gson // Keep Gson for serialization
import com.navigine.idl.java.Location as NavigineLocation
import com.navigine.idl.java.LocationInfo
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.LocationListListener
import com.navigine.idl.java.Venue // Keep Navigine Venue for processing in MainActivity
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
    private var currentVenues: List<Venue> = emptyList() // Store the list of Navigine venues from the current location

    // Store the current Geocoder address for fallback
    private var currentGeocoderAddress: String = "Unknown Area"

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val recordAudioPermissionRequestCode = 1002
        const val EXTRA_VENUE_LIST_JSON = "venue_list_json" // Define a constant for the Intent extra key
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
                    Log.d(TAG, "Speech recognition result: $text")
                    // Now, navigate to SearchActivity with the spoken text
                    // and the *current* venues (which should be loaded if in a connected location)
                    navigateToSearchActivity(text, currentVenues)
                } ?: run {
                    Log.w(TAG, "Speech recognition result is empty or null.")
                    // Handle the case where no speech was recognized
                    Toast.makeText(this, "Speech not recognized.", Toast.LENGTH_SHORT).show()
                }
            } else if (result.resultCode == RESULT_CANCELED) {
                Log.d(TAG, "Speech recognition cancelled.")
                // Handle cancelled speech recognition if needed
            } else {
                Log.e(TAG, "Speech recognition failed with result code: ${result.resultCode}")
                // Handle other potential errors
                Toast.makeText(this, "Speech recognition failed.", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize location manager (replace with your implementation if different)
        // Make sure your LocationManager is initialized here
        locationManager = LocationManager(this) // Assuming LocationManager has a context constructor

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
            Log.d(TAG, "Mic button clicked.")
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
        binding.searchLayout.setOnClickListener {
            Log.d(TAG, "Search layout clicked.")
            // When the search area is clicked, immediately navigate to SearchActivity
            // with the current venues list.
            navigateToSearchActivity(null, currentVenues) // Pass null for initial query if clicked directly
        }

// Make sure the entire search bar is clickable, not just the icon
        binding.searchView.setOnClickListener {
            Log.d(TAG, "Search view clicked.")
            // Navigate to search activity when search view is clicked
            navigateToSearchActivity(null, currentVenues)
        }

// Optional: Disable the actual search functionality on the SearchView in MainActivity
// since we're handling it in SearchActivity
        binding.searchView.isClickable = true
        binding.searchView.isSubmitButtonEnabled = false
        binding.searchView.setOnQueryTextListener(null) // Remove any existing listeners


        // Setup click listeners for suggestion cards
        binding.suggestionCard1.setOnClickListener { handleSuggestionCardClick(binding.suggestionText1.text.toString()) }
        binding.suggestionCard2.setOnClickListener { handleSuggestionCardClick(binding.suggestionText2.text.toString()) }
        binding.suggestionCard3.setOnClickListener { handleSuggestionCardClick(binding.suggestionText3.text.toString()) }
        binding.suggestionCard4.setOnClickListener { handleSuggestionCardClick(binding.suggestionText4.text.toString()) }
    }

    private fun handleSuggestionCardClick(suggestionText: String) {
        // Show a toast to indicate which suggestion was tapped
        Toast.makeText(this, "Navigating to: $suggestionText", Toast.LENGTH_SHORT).show()

        // Check if we have a matched Navigine location
        if (isLocationCanaryConnected && matchedNavigineLocationInfo != null) {
            // Get the location ID from the matched location info
            val locationId = matchedNavigineLocationInfo!!.id

            // Find the venue that matches the suggestion text (if any)
            var selectedVenueId = -1
            var selectedSublocationId = -1

            // Check if we have loaded location details
            val loadedLocation = loadedNavigineLocations[locationId]
            if (loadedLocation != null) {
                // Iterate through sublocations to find the venue
                for (sublocation in loadedLocation.getSublocations()) {
                    for (venue in sublocation.getVenues()) {
                        if (venue.getName() == suggestionText) {
                            // We found a matching venue
                            selectedVenueId = venue.getId()
                            selectedSublocationId = sublocation.getId()
                            break
                        }
                    }
                    if (selectedVenueId != -1) break // Stop if we found the venue
                }

                // Create an Intent to start LocationMapActivity
                try {
                    // Create a bundle to pass arguments
                    val bundle = Bundle().apply {
                        putInt("locationId", locationId)
                        putInt("sublocationId", selectedSublocationId)
                        putInt("venueId", selectedVenueId) // Optional, for future use
                        putString("venueName", suggestionText) // Pass the venue name
                    }

                    // Create the Intent for the map fragment
                    val intent = Intent(this, MapActivity::class.java)
                    intent.putExtras(bundle)
                    startActivity(intent)

                    Log.d(TAG, "Launched MapActivity for location: $locationId, sublocation: $selectedSublocationId, venue: $selectedVenueId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching map: ${e.message}", e)
                    Toast.makeText(this, "Could not open map view.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Location details not loaded yet, try to load them
                Log.d(TAG, "Location details not loaded yet, attempting to load")
                loadNavigineLocationDetails(locationId)
                Toast.makeText(this, "Please try again in a moment.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Not in a Canary connected location
            Toast.makeText(this, "Map not available - not in a Canary connected location", Toast.LENGTH_LONG).show()
        }
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
            Log.d(TAG, "Launching speech recognition intent.")
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching speech recognition: ${e.message}", e)
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

            // Safely access venue names from the provided list
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

            // When setting default suggestions, also clear the current venues list
            currentVenues = emptyList()
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

                // Fetch address details based on user location (Geocoder)
                fetchAddressDetails(userLatitude, userLongitude)

                // Check if user is within a known location (KML polygon)
                checkIfLocationIsCanaryConnected()
            } ?: run {
                Toast.makeText(
                    this,
                    "Unable to get current location. Please make sure location is enabled.",
                    Toast.LENGTH_LONG
                ).show()
                // Update UI to reflect unknown location
                updateLocationUI(null)
                setDefaultSuggestions()
            }
        }
    }

    private fun fetchAddressDetails(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Use Build.VERSION_CODES for Android 13+
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    processGeocoderAddresses(addresses)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (addresses != null) {
                    processGeocoderAddresses(addresses)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                currentGeocoderAddress = "Unknown Area (Geocoder Error)"
                // If no KML location is found, this will be the fallback
                updateLocationUI(null)
            }
        }
    }


    private fun processGeocoderAddresses(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            // Get location name (could be locality, subLocality, or thoroughfare)
            currentGeocoderAddress = address.featureName ?: address.locality ?: address.subLocality ?: address.thoroughfare ?: "Unknown Area"

            // Update UI with Geocoder address initially.
            // This will be overwritten if a KML location is found.
            runOnUiThread {
                binding.locationNameText.text = currentGeocoderAddress
                // Update location info text based on Canary connection status (will be updated again by checkIfLocationIsCanaryConnected)
                updateLocationInfo()
            }
        } else {
            runOnUiThread {
                currentGeocoderAddress = "Unknown Area (No Geocoder Result)"
                binding.locationNameText.text = currentGeocoderAddress
                updateLocationInfo()
            }
        }
    }

    private fun updateLocationInfo() {
        // This function is primarily for updating the "Canary Connected" status text.
        // The location name is handled by updateLocationUI.
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

            // Clear previous markers and polygons
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
                    .fillColor(if (location.isCanaryConnected) 0x3300FF00 else 0x33FF0000) // Green if connected, Red if not
                    .strokeColor(if (location.isCanaryConnected) 0xFF00FF00.toInt() else 0xFFFF0000.toInt()) // Green border, Red border
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
        currentVenues = emptyList() // Clear previous venues

        // Make sure we have valid user coordinates
        if (userLatitude == 0.0 && userLongitude == 0.0) {
            Log.d(TAG, "Invalid user coordinates")
            // Keep previous location name or set a default if no location was detected
            updateLocationUI(null) // Pass null to indicate no known location
            setDefaultSuggestions() // Use default suggestions if location is unknown
            return
        }

        // Check if user is within a known location (using your LocationManager's logic)
        val matchedLocation = locationManager.checkUserLocation(userLatitude, userLongitude)

        if (matchedLocation != null) {
            Log.d(TAG, "User is in location: ${matchedLocation.name} (ID: ${matchedLocation.id})")
            isLocationCanaryConnected = matchedLocation.isCanaryConnected

            // Pass the matched location object to updateLocationUI
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
                // User is in a known KML location, but it's not Canary Connected
                setDefaultSuggestions() // Fallback to default if not Canary connected
            }
        } else {
            // User is not in any known location - rely on Geocoder address
            val currentLocationString = "Your current location: Latitude: $userLatitude, Longitude: $userLongitude"
            Log.d(TAG, "User is not in any known location. $currentLocationString")
            // We will rely on the Geocoder address set by fetchAddressDetails
            updateLocationUI(null) // Pass null to indicate no known location
            setDefaultSuggestions() // Use default suggestions if location is unknown
        }
    }


    private fun updateLocationUI(matchedLocation: LocationManager.LocationInfo?) {
        runOnUiThread {
            if (matchedLocation != null) {
                // User is in a known KML location (either connected or not)
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = matchedLocation.name // Use the name from your LocationInfo

                if (matchedLocation.isCanaryConnected) {
                    binding.locationInfoText.text = "This location is Canary Connected!"
                    binding.locationInfoCardView.setCardBackgroundColor(0xFFE6FFB3.toInt()) // Use a color resource
                    binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_green)
                    binding.canaryStatusText.text = "Status: Connected"
                    binding.canaryStatusIndicator.visibility = View.VISIBLE
                } else {
                    binding.locationInfoText.text = "This location is not Canary Connected"
                    binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt()) // Use a color resource
                    binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
                    binding.canaryStatusText.text = "Status: Disconnected"
                    binding.canaryStatusIndicator.visibility = View.VISIBLE
                }
            } else {
                // User is not in any known KML location - use the Geocoder address
                binding.locationStatusText.text = "You're at" // or "Current Location:"
                binding.locationNameText.text = currentGeocoderAddress // Use the stored Geocoder address
                binding.locationInfoText.text = "This location is not Canary Connected"
                binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt()) // Use a color resource
                binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
                binding.canaryStatusText.text = "Status: Disconnected"
                binding.canaryStatusIndicator.visibility = View.VISIBLE
            }
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
                    // locationListManager.removeLocationListListener(this) // Keep the listener if you need updates on location list changes
                }

                override fun onLocationListFailed(error: Error) {
                    Log.e(TAG, "Failed to load Navigine location list: ${error.message}")
                    // Handle error, e.g., show a message to the user
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to load location data.", Toast.LENGTH_SHORT).show()
                    }
                    // Remove the listener after failure
                    // locationListManager.removeLocationListListener(this) // Keep the listener if you want retries
                }
            }

            // Add the listener and then update the location list
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
        val sublocations = location.getSublocations()
        val allVenues = mutableListOf<Venue>() // Create a mutable list to hold all Navigine venues

        // Iterate through all sublocations
        for (sublocation in sublocations) {
            // Get venues from the current sublocation and add them to the list
            allVenues.addAll(sublocation.getVenues().toList())
        }

        Log.d(TAG, "Found a total of ${allVenues.size} venues across all sublocations.")

        // Store the list of Navigine venues
        currentVenues = allVenues

        if (allVenues.isNotEmpty()) {
            // Now you have a list of all venues from all sublocations.
            // You might want to sort or filter this list before updating suggestion cards.
            // For example, you could sort alphabetically or by some other criteria.
            // For now, let's just use the first few from the combined list.
            updateSuggestionCards(allVenues) // Pass the combined list of venues
        } else {
            Log.d(TAG, "No venues found in any sublocation for location: ${location.getName()}")
            setDefaultSuggestions() // Use default suggestions if no venues are found in any sublocation
        }
    }

    // Function to handle navigation to SearchActivity
    private fun navigateToSearchActivity(initialQuery: String?, venues: List<Venue>) {
        Log.d(TAG, "Attempting to navigate to SearchActivity with query: $initialQuery and ${venues.size} venues.")
        val intent = Intent(this, SearchActivity::class.java)
        if (initialQuery != null) {
            intent.putExtra("initial_query", initialQuery) // Pass the initial query if available
        }

        // Extract necessary data (only name) from Navigine Venue objects and create a list of VenueData
        val venueDataList = venues.map {
            VenueData(
                name = it.getName(),
            )
        }

        // Serialize the list of VenueData to JSON and pass it
        val gson = Gson()
        try {
            val venueListJson = gson.toJson(venueDataList)
            intent.putExtra(EXTRA_VENUE_LIST_JSON, venueListJson)
            Log.d(TAG, "VenueData serialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing venues: ${e.message}", e)
            // Handle serialization error, maybe pass an empty list or show an error
            intent.putExtra(EXTRA_VENUE_LIST_JSON, "[]") // Pass an empty JSON array
            Toast.makeText(this, "Error preparing venue data for search.", Toast.LENGTH_SHORT).show()
        }


        try {
            startActivity(intent)
            Log.d(TAG, "SearchActivity launched.")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching SearchActivity: ${e.message}", e)
            Toast.makeText(this, "Could not open search screen.", Toast.LENGTH_SHORT).show()
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
        // NavigineSdkManager.release() // Uncomment if you need to release SDK resources
    }
}