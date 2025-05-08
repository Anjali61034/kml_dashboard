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
import com.google.gson.Gson
import com.navigine.idl.java.Location as NavigineLocation
import com.navigine.idl.java.LocationInfo
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.LocationListListener
import com.navigine.idl.java.Venue
import java.io.IOException
import java.util.Locale
import java.util.HashMap

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private lateinit var speechRecognitionLauncher: ActivityResultLauncher<Intent>

    private lateinit var locationRequest: LocationRequest

    private var userLatitude = 0.0
    private var userLongitude = 0.0
    private var isLocationCanaryConnected = false
    private var matchedNavigineLocationInfo: LocationInfo? = null
    private val loadedNavigineLocations = HashMap<Int, NavigineLocation>()
    private var currentVenues: List<Venue> = emptyList()

    private var currentGeocoderAddress: String = "Unknown Area"

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val recordAudioPermissionRequestCode = 1002
        const val EXTRA_VENUE_LIST_JSON = "venue_list_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // 1. Initialize Navigine SDK FIRST
        initializeNavigineAndLoadLocations()

        // 2. Proceed with rest of setup after SDK initialization
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
                    navigateToSearchActivity(text, currentVenues)
                } ?: run {
                    Log.w(TAG, "Speech recognition result is empty or null.")
                    Toast.makeText(this, "Speech not recognized.", Toast.LENGTH_SHORT).show()
                }
            } else if (result.resultCode == RESULT_CANCELED) {
                Log.d(TAG, "Speech recognition cancelled.")
            } else {
                Log.e(TAG, "Speech recognition failed with result code: ${result.resultCode}")
                Toast.makeText(this, "Speech recognition failed.", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize location manager
        locationManager = LocationManager(this)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create LocationRequest
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(10f)
            .setMaxUpdateDelayMillis(5000)
            .build()

        // Set up map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set default suggestions initially
        setDefaultSuggestions()

        // Request location permissions
        requestLocationPermissions()

        binding.micButton.setOnClickListener {
            Log.d(TAG, "Mic button clicked.")
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    recordAudioPermissionRequestCode
                )
            } else {
                startSpeechRecognition()
            }
        }

        binding.searchLayout.setOnClickListener {
            Log.d(TAG, "Search layout clicked.")
            navigateToSearchActivity(null, currentVenues)
        }

        // Make entire search view clickable
        binding.searchView.setOnClickListener {
            Log.d(TAG, "Search view clicked.")
            navigateToSearchActivity(null, currentVenues)
        }

        // Setup suggestion card click listeners
        binding.suggestionCard1.setOnClickListener { handleSuggestionCardClick(binding.suggestionText1.text.toString()) }
        binding.suggestionCard2.setOnClickListener { handleSuggestionCardClick(binding.suggestionText2.text.toString()) }
        binding.suggestionCard3.setOnClickListener { handleSuggestionCardClick(binding.suggestionText3.text.toString()) }
        binding.suggestionCard4.setOnClickListener { handleSuggestionCardClick(binding.suggestionText4.text.toString()) }
    }

    private fun handleSuggestionCardClick(suggestionText: String) {
        Toast.makeText(this, "Navigating to: $suggestionText", Toast.LENGTH_SHORT).show()
        if (isLocationCanaryConnected && matchedNavigineLocationInfo != null) {
            val locationId = matchedNavigineLocationInfo!!.id
            var selectedVenueId = -1
            var selectedSublocationId = -1
            val loadedLocation = loadedNavigineLocations[locationId]
            if (loadedLocation != null) {
                for (sublocation in loadedLocation.getSublocations()) {
                    for (venue in sublocation.getVenues()) {
                        if (venue.getName() == suggestionText) {
                            selectedVenueId = venue.getId()
                            selectedSublocationId = sublocation.getId()
                            break
                        }
                    }
                    if (selectedVenueId != -1) break
                }
                try {
                    val bundle = Bundle().apply {
                        putInt("locationId", locationId)
                        putInt("sublocationId", selectedSublocationId)
                        putInt("venueId", selectedVenueId)
                        putString("venueName", suggestionText)
                    }
                    val intent = Intent(this, MapActivity::class.java)
                    intent.putExtras(bundle)
                    startActivity(intent)
                    Log.d(TAG, "Launched MapActivity for location: $locationId, sublocation: $selectedSublocationId, venue: $selectedVenueId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching map: ${e.message}", e)
                    Toast.makeText(this, "Could not open map view.", Toast.LENGTH_SHORT).show()
                }
            } else {
                loadNavigineLocationDetails(locationId)
                Toast.makeText(this, "Please try again in a moment.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Map not available - not in a Canary connected location", Toast.LENGTH_LONG).show()
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try {
            Log.d(TAG, "Launching speech recognition intent.")
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching speech recognition: ${e.message}", e)
            Toast.makeText(this, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSuggestionCards(venues: List<Venue>) {
        runOnUiThread {
            val maxCards = minOf(venues.size, 4)
            val defaultTexts = arrayOf("Reception", "Office", "Hall", "Doctor")
            binding.suggestionText1.text = venues.getOrNull(0)?.getName() ?: defaultTexts[0]
            binding.suggestionText2.text = venues.getOrNull(1)?.getName() ?: defaultTexts[1]
            binding.suggestionText3.text = venues.getOrNull(2)?.getName() ?: defaultTexts[2]
            binding.suggestionText4.text = venues.getOrNull(3)?.getName() ?: defaultTexts[3]
            Log.d(TAG, "Updated ${maxCards} suggestion cards with venues.")
        }
    }

    private fun setDefaultSuggestions() {
        runOnUiThread {
            val defaultTexts = arrayOf("Reception", "Office", "Hall", "Doctor")
            binding.suggestionText1.text = defaultTexts[0]
            binding.suggestionText2.text = defaultTexts[1]
            binding.suggestionText3.text = defaultTexts[2]
            binding.suggestionText4.text = defaultTexts[3]
            Log.d(TAG, "Set default suggestions")
            currentVenues = emptyList()
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getLastLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLastLocation()
                } else {
                    Toast.makeText(this, "Location permission denied. Cannot detect if you're in a Canary connected location.", Toast.LENGTH_LONG).show()
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                userLatitude = it.latitude
                userLongitude = it.longitude
                updateMapWithUserLocation()
                fetchAddressDetails(userLatitude, userLongitude)
                checkIfLocationIsCanaryConnected()
            } ?: run {
                Toast.makeText(this, "Unable to get current location. Please make sure location is enabled.", Toast.LENGTH_LONG).show()
                updateLocationUI(null)
                setDefaultSuggestions()
            }
        }
    }

    private fun fetchAddressDetails(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                updateLocationUI(null)
            }
        }
    }

    private fun processGeocoderAddresses(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            currentGeocoderAddress = address.featureName ?: address.locality ?: address.subLocality ?: address.thoroughfare ?: "Unknown Area"
            runOnUiThread {
                binding.locationNameText.text = currentGeocoderAddress
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
            map.clear()
            map.addMarker(MarkerOptions().position(userLocation).title("You are here"))
            for (location in locationManager.getAllLocations()) {
                val polygonOptions = PolygonOptions()
                    .clickable(true)
                    .fillColor(if (location.isCanaryConnected) 0x3300FF00 else 0x33FF0000)
                    .strokeColor(if (location.isCanaryConnected) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
                    .strokeWidth(3f)
                location.polygonPoints.forEach { point ->
                    polygonOptions.add(LatLng(point.latitude, point.longitude))
                }
                map.addPolygon(polygonOptions)
            }
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 17f))
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }
    }

    private fun checkIfLocationIsCanaryConnected() {
        isLocationCanaryConnected = false
        matchedNavigineLocationInfo = null
        currentVenues = emptyList()

        if (userLatitude == 0.0 && userLongitude == 0.0) {
            Log.d(TAG, "Invalid user coordinates")
            updateLocationUI(null)
            setDefaultSuggestions()
            return
        }

        val matchedLocation = locationManager.checkUserLocation(userLatitude, userLongitude)

        if (matchedLocation != null) {
            Log.d(TAG, "User is in location: ${matchedLocation.name} (ID: ${matchedLocation.id})")
            isLocationCanaryConnected = matchedLocation.isCanaryConnected
            updateLocationUI(matchedLocation)

            if (isLocationCanaryConnected) {
                val navigineLocationInfo = NavigineSdkManager.locationListManager.locationList[matchedLocation.id]
                if (navigineLocationInfo != null) {
                    matchedNavigineLocationInfo = navigineLocationInfo
                    loadNavigineLocationDetails(navigineLocationInfo.id)
                } else {
                    Log.w(TAG, "Navigine LocationInfo not found for ID: ${matchedLocation.id}")
                    setDefaultSuggestions()
                }
            } else {
                setDefaultSuggestions()
            }
        } else {
            val currentLocationString = "Your current location: Latitude: $userLatitude, Longitude: $userLongitude"
            Log.d(TAG, "User is not in any known location. $currentLocationString")
            updateLocationUI(null)
            setDefaultSuggestions()
        }
    }

    private fun updateLocationUI(matchedLocation: LocationManager.LocationInfo?) {
        runOnUiThread {
            if (matchedLocation != null) {
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = matchedLocation.name
                if (matchedLocation.isCanaryConnected) {
                    binding.locationInfoText.text = "This location is Canary Connected!"
                    binding.locationInfoCardView.setCardBackgroundColor(0xFFE6FFB3.toInt())
                    binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_green)
                    binding.canaryStatusText.text = "Status: Connected"
                    binding.canaryStatusIndicator.visibility = View.VISIBLE
                } else {
                    binding.locationInfoText.text = "This location is not Canary Connected"
                    binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt())
                    binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
                    binding.canaryStatusText.text = "Status: Disconnected"
                    binding.canaryStatusIndicator.visibility = View.VISIBLE
                }
            } else {
                binding.locationStatusText.text = "You're at"
                binding.locationNameText.text = currentGeocoderAddress
                binding.locationInfoText.text = "This location is not Canary Connected"
                binding.locationInfoCardView.setCardBackgroundColor(0xFFF0F0F0.toInt())
                binding.canaryStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_red)
                binding.canaryStatusText.text = "Status: Disconnected"
                binding.canaryStatusIndicator.visibility = View.VISIBLE
            }
        }
    }

    // Inside your MainActivity.kt, around SDK calls:
    private fun initializeNavigineAndLoadLocations() {
        try {
            // Call initialize() without checking isInitialized
            NavigineSdkManager.initialize(this)
            Log.d("Navigine", "SDK initialized successfully")
            loadNavigineLocationList()
        } catch (e: Exception) {
            Log.e("Navigine", "Error initializing SDK: ${e.message}")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Failed to initialize location services.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadNavigineLocationList() {
        try {
            val locationListManager = NavigineSdkManager.locationListManager
            val locationListListener = object : LocationListListener() {
                override fun onLocationListLoaded(idToLocationInfoMap: HashMap<Int, LocationInfo>) {
                    Log.d(TAG, "Location list loaded.")
                    if (userLatitude != 0.0 && userLongitude != 0.0) {
                        checkIfLocationIsCanaryConnected()
                    }
                }

                override fun onLocationListFailed(error: Error) {
                    Log.e(TAG, "Failed to load location list: ${error.message}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to load location data.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            locationListManager.addLocationListListener(locationListListener)
            locationListManager.updateLocationList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading location list: ${e.message}")
        }
    }

    private fun loadNavigineLocationDetails(locationId: Int) {
        if (loadedNavigineLocations.containsKey(locationId)) {
            // Already loaded
            val location = loadedNavigineLocations[locationId]
            location?.let { processLoadedLocation(it) }
            return
        }
        try {
            val locationListener = object : LocationListener() {
                override fun onLocationLoaded(location: NavigineLocation) {
                    Log.d(TAG, "Loaded location details: ${location.name}")
                    loadedNavigineLocations[location.getId()] = location
                    processLoadedLocation(location)
                    NavigineSdkManager.locationManager.removeLocationListener(this)
                }
                override fun onLocationFailed(locationId: Int, error: java.lang.Error?) {
                    Log.e(TAG, "Failed to load location $locationId: ${error?.message}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to load location details.", Toast.LENGTH_SHORT).show()
                    }
                    setDefaultSuggestions()
                    NavigineSdkManager.locationManager.removeLocationListener(this)
                }
                override fun onLocationUploaded(p0: Int) {}
            }
            NavigineSdkManager.locationManager.addLocationListener(locationListener)
            NavigineSdkManager.locationManager.setLocationId(locationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting location ID: ${e.message}")
            setDefaultSuggestions()
        }
    }

    private fun processLoadedLocation(location: NavigineLocation) {
        val sublocations = location.getSublocations()
        val allVenues = mutableListOf<Venue>()
        for (sublocation in sublocations) {
            allVenues.addAll(sublocation.getVenues().toList())
        }
        Log.d(TAG, "Found a total of ${allVenues.size} venues across all sublocations.")
        currentVenues = allVenues
        if (allVenues.isNotEmpty()) {
            updateSuggestionCards(allVenues)
        } else {
            Log.d(TAG, "No venues found in any sublocation for location: ${location.getName()}")
            setDefaultSuggestions()
        }
    }

    private fun navigateToSearchActivity(initialQuery: String?, venues: List<Venue>) {
        Log.d(TAG, "Attempting to navigate to SearchActivity with query: $initialQuery and ${venues.size} venues.")
        val intent = Intent(this, SearchActivity::class.java)
        initialQuery?.let {
            intent.putExtra("initial_query", it)
        }
        val venueDataList = venues.map {
            VenueData(
                name = it.getName(),
            )
        }
        val gson = Gson()
        try {
            val venueListJson = gson.toJson(venueDataList)
            intent.putExtra(EXTRA_VENUE_LIST_JSON, venueListJson)
            Log.d(TAG, "VenueData serialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing venues: ${e.message}", e)
            intent.putExtra(EXTRA_VENUE_LIST_JSON, "[]")
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
        with(map) {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            try {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    isMyLocationEnabled = true
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error setting location on map", e)
            }
        }
        if (userLatitude != 0.0 && userLongitude != 0.0) {
            updateMapWithUserLocation()
        } else {
            val defaultLocation = LatLng(28.6668404, 77.2147314)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 17f))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup if needed
        // NavigineSdkManager.release()
    }
}