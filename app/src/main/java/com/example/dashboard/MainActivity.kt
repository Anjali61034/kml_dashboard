package com.example.dashboard

import android.Manifest
import com.example.canary.NavigineSdkManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.navigine.idl.java.Location
import com.navigine.idl.java.LocationInfo
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.LocationListListener
import com.navigine.idl.java.Sublocation
import com.navigine.idl.java.Venue
import java.io.IOException
import java.util.Locale
import java.util.HashMap

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequestCode = 1
    private val recordAudioPermissionRequestCode = 2
    private val TAG = "DashboardNavigine"

    // UI elements
    private lateinit var searchView: SearchView
    private lateinit var homeButton: LinearLayout
    private lateinit var settingsButton: LinearLayout
    private lateinit var profileButton: LinearLayout
    private lateinit var locationNameText: TextView
    private lateinit var locationInfoText: TextView
    private lateinit var micButton: ImageView
    private lateinit var suggestionsListView: ListView

    // Current user location
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0

    // Navigine related
    private var navigineLocations = mutableListOf<LocationInfo>()
    private var isLocationCanaryConnected = false
    private var matchedLocation: LocationInfo? = null

    // Speech recognition launcher
    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText: ArrayList<String>? = result.data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            spokenText?.get(0)?.let { text ->
                searchView.setQuery(text, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        searchView = findViewById(R.id.searchView)
        homeButton = findViewById(R.id.homeButton)
        settingsButton = findViewById(R.id.settingsButton)
        profileButton = findViewById(R.id.profileButton)
        locationNameText = findViewById(R.id.locationNameText)
        locationInfoText = findViewById(R.id.locationInfoText)
        micButton = findViewById(R.id.micButton)
        suggestionsListView = findViewById(R.id.suggestionsListView)

        // Initialize the map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Navigine SDK
        initializeNavigine()

        // Setup search view
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Handle search here
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // Setup speech recognition for microphone button
        micButton.setOnClickListener {
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

        // Setup navigation buttons
        homeButton.setOnClickListener {
            // Handle home navigation
        }
        settingsButton.setOnClickListener {
            // Handle settings navigation
        }
        profileButton.setOnClickListener {
            // Handle profile navigation
        }
    }

    private fun initializeNavigine() {
        try {
            // Initialize Navigine SDK Manager
            NavigineSdkManager.initialize(this)
            Log.d(TAG, "Navigine SDK initialized successfully")

            // Load Navigine locations
            loadNavigineLocations()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Navigine SDK: ${e.message}")
        }
    }

    private fun loadNavigineLocations() {
        try {
            // Get the location list from the Navigine SDK
            val locationListManager = NavigineSdkManager.locationListManager

            // Set a listener to get the locations when they're loaded
            val locationListListener = object : LocationListListener() {
                override fun onLocationListLoaded(idToLocationInfoMap: HashMap<Int, LocationInfo>) {
                    Log.d(TAG, "Navigine location list loaded")

                    // Convert the HashMap values to a list
                    val locationInfoList = ArrayList(idToLocationInfoMap.values)

                    // Get all available locations
                    navigineLocations.clear()
                    navigineLocations.addAll(locationInfoList)
                    Log.d(TAG, "Loaded ${navigineLocations.size} Navigine locations")

                    // Check if user's location is already fetched, if yes, check for matches
                    if (userLatitude != 0.0 && userLongitude != 0.0) {
                        checkIfLocationIsCanaryConnected()
                    }
                }

                override fun onLocationListFailed(error: Error) {
                    Log.e(TAG, "Failed to load Navigine location list: ${error.message}")
                }
            }

            locationListManager.addLocationListListener(locationListListener)

            // Start loading the location list
            locationListManager.updateLocationList()

        } catch (e: Exception) {
            Log.e(TAG, "Error loading Navigine locations: ${e.message}")
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
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speech recognition not supported on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // Check for location permissions and get user location
        checkLocationPermissionAndGetLocation()
    }

    private fun checkLocationPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        } else {
            // Permission already granted, get location
            getUserLocation()
        }
    }

    private fun getUserLocation() {
        try {
            googleMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLatitude = it.latitude
                    userLongitude = it.longitude
                    val currentLatLng = LatLng(userLatitude, userLongitude)

                    // Move camera to user location
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    // Get address from coordinates
                    getAddressFromLocation(userLatitude, userLongitude)

                    // Check if this location matches with any Navigine location
                    if (navigineLocations.isNotEmpty()) {
                        checkIfLocationIsCanaryConnected()
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            // Handle API level differences for Android 13 (Tiramisu)
            if (Build.VERSION.SDK_INT >= 33) {
                // For Android 13 and above
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    processAddresses(addresses)
                }
            } else {
                // For Android 12 and below
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
            val locationName = address.featureName ?: address.locality
            ?: address.subLocality ?: address.thoroughfare ?: "Unknown Location"

            // Update UI with location name
            runOnUiThread {
                locationNameText.text = locationName

                // Update location info text based on Canary connection status
                updateLocationInfo()
            }
        }
    }

    private fun checkIfLocationIsCanaryConnected() {
        isLocationCanaryConnected = false
        matchedLocation = null

        // Check if the user's current location matches with any Navigine location
        for (locationInfo in navigineLocations) {
            if (isUserLocationWithinNavigineLocation(locationInfo)) {
                isLocationCanaryConnected = true
                matchedLocation = locationInfo
                break
            }
        }

        // Update UI based on the result
        runOnUiThread {
            updateLocationInfo()
            updateSuggestions()
        }
    }

    private fun isUserLocationWithinNavigineLocation(locationInfo: LocationInfo): Boolean {
        try {
            // We would need the global coordinates of the location to check
            // This is a simplified check since we don't have direct access to location coordinates from LocationInfo
            // In a real implementation, you would need to load the location details to get its coordinates

            // For now, we'll just simulate the check based on a distance threshold
            // In a real implementation, you would get the actual origin point from the location's sublocations

            // Assuming some rough coordinates from the location info (this should be fetched in a real implementation)
            val locationLatitude = 0.0 // Replace with actual location latitude
            val locationLongitude = 0.0 // Replace with actual location longitude

            val distance = calculateDistance(
                userLatitude, userLongitude,
                locationLatitude, locationLongitude
            )

            // If within 200 meters, consider it a match
            return distance <= 200.0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user is within Navigine location: ${e.message}")
        }

        return false
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        // Haversine formula to calculate distance between two points on Earth
        val r = 6371e3 // Earth radius in meters
        val φ1 = lat1 * Math.PI / 180
        val φ2 = lat2 * Math.PI / 180
        val Δφ = (lat2 - lat1) * Math.PI / 180
        val Δλ = (lon2 - lon1) * Math.PI / 180

        val a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c // Distance in meters
    }

    private fun updateLocationInfo() {
        if (isLocationCanaryConnected) {
            locationInfoText.text = "This location is Canary Connected!"
            locationInfoText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            locationInfoText.text = "This location is not Canary Connected"
            locationInfoText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun updateSuggestions() {
        if (isLocationCanaryConnected && matchedLocation != null) {
            // Location is Canary connected, show venues from the matched location
            loadVenuesForLocation(matchedLocation!!.id)
        }
        // If not Canary connected, keep existing suggestions (do nothing)
    }

    private fun loadVenuesForLocation(locationId: Int) {
        try {
            // Set the location ID
            NavigineSdkManager.locationManager.setLocationId(locationId)

            // Set a listener to know when the location is loaded
           // val locationListener = object : LocationListener() {
               // override fun onLocationLoaded(location: Location?) {
                 //   if (location != null) {
                       // val sublocations = location.sublocations
                      //  if (!sublocations.isNullOrEmpty()) {
                           // val venueSuggestions = mutableListOf<String>()
                            //for (sublocation in sublocations) {
                             //   val venues = sublocation.venues
                                //if (!venues.isNullOrEmpty()) {
                                 //   for (venue in venues) {
                                     //   venueSuggestions.add(venue.name)
                                  //  }
                               // }
                           // }

                            //runOnUiThread {
                              //  val suggestionsAdapter = ArrayAdapter(
                                //    this@MainActivity,
                                  //  android.R.layout.simple_list_item_1,
                                    //venueSuggestions
                            //    )
                              //  suggestionsListView.adapter = suggestionsAdapter
                           // }
                       // }
                    //}
              //  }

                //override fun onLocationFailed() {
                   // Log.e(TAG, "Failed to load Navigine location details: ${}")
               // }
            //}



            // Add the listener using the correct method
          //  NavigineSdkManager.locationManager.addLocationListener(locationListener)

            // Based on error message, there is no loadLocation method.
            // Setting the location ID likely triggers the loading process automatically.
            // If you need to trigger loading explicitly, check the SDK documentation
            // for the correct method or approach.

        } catch (e: Exception) {
            Log.e(TAG, "Error loading venues for location: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            locationPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getUserLocation()
                }
            }
            recordAudioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSpeechRecognition()
                } else {
                    Toast.makeText(
                        this,
                        "Permission denied for voice search",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop navigation if needed
        try {
            NavigineSdkManager.navigationManager.stopLogRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping navigation: ${e.message}")
        }
    }
}