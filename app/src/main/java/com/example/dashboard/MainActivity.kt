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
import com.navigine.idl.java.GlobalPoint
import com.navigine.idl.java.Location
import com.navigine.idl.java.Position
import com.navigine.idl.java.LocationInfo
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.LocationListListener
import com.navigine.idl.java.LocationPoint
import com.navigine.idl.java.Point
import com.navigine.idl.java.Polygon
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

    // Store loaded locations
    private val loadedLocations = HashMap<Int, Location>()

    // Store location data with coordinates
    private val locationCoordinatesMap = HashMap<Int, LocationCoordinates>()

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

                    // Load details for each location
                    for (locationInfo in navigineLocations) {
                        loadLocationDetails(locationInfo.id)
                    }

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

    // Class to store location coordinates and boundaries
    data class LocationCoordinates(
        val id: Int,
        val name: String,
        val originPoint: GlobalPoint? = null,  // Origin point in global coordinates
        val width: Float = 0f,
        val height: Float = 0f,
        val azimuth: Float = 0f,   // Orientation angle in degrees
        val venues: List<VenueCoordinates> = emptyList(),
        val zones: List<ZoneCoordinates> = emptyList()
    )

    data class VenueCoordinates(
        val id: Int,
        val name: String,
        val point: Point? = null   // Local coordinates within sublocation
    )

    data class ZoneCoordinates(
        val id: Int,
        val name: String,
        val polygon: Polygon? = null  // Local coordinates of zone boundary
    )

    private fun loadLocationDetails(locationId: Int) {
        try {
            val locationManager = NavigineSdkManager.locationManager.setLocationId(locationId)

            // Replace the locationListener implementation at line ~246

            val locationListener =object:LocationListener() {
                override fun onLocationLoaded(location: Location) {
                    // Store the loaded location for later use
                    loadedLocations[locationId] = location

                    // Get all sublocations for this location
                    val sublocations = location.getSublocations()

                    // Use iterator properly to avoid ambiguity
                    val sublocationsIterator = sublocations.iterator() as Iterator<Sublocation>
                    for (sublocation in sublocationsIterator) {
                        // Get the origin point in global coordinates (lat, lon)
                        val originPoint = sublocation.getOriginPoint()

                        if (originPoint != null) {
                            // Extract venue information - use iterator to avoid ambiguity
                            val venuesIterator = sublocation.getVenues().iterator() as Iterator<Venue>
                            val venues = mutableListOf<VenueCoordinates>()
                            for (venue in venuesIterator) {
                                venues.add(
                                    VenueCoordinates(
                                        id = venue.getId(),
                                        name = venue.getName(),
                                        point = venue.getPoint()
                                    )
                                )
                            }

                            // Extract zone information using iterator to avoid ambiguity
                            val zonesIterator = sublocation.getZones().iterator() as Iterator<com.navigine.idl.java.Zone>
                            val zones = mutableListOf<ZoneCoordinates>()
                            for (zone in zonesIterator) {
                                zones.add(
                                    ZoneCoordinates(
                                        id = zone.getId(),
                                        name = zone.getName(),
                                        polygon = zone.getPolygon()
                                    )
                                )
                            }

                            Log.d(TAG, "Location ${location.getName()} sublocation ${sublocation.getName()} has origin point: ${originPoint.latitude}, ${originPoint.longitude}")
                            Log.d(TAG, "Found ${venues.size} venues and ${zones.size} zones")

                            locationCoordinatesMap[locationId] = LocationCoordinates(
                                id = locationId,
                                name = location.getName(),
                                originPoint = originPoint,
                                width = sublocation.getWidth(),
                                height = sublocation.getHeight(),
                                azimuth = sublocation.getAzimuth(),
                                venues = venues,
                                zones = zones
                            )

                            // We found a sublocation with coordinates, so break the loop
                            break
                        }
                    }

                    // After loading location details, check if user is in any Canary location
                    if (userLatitude != 0.0 && userLongitude != 0.0) {
                        checkIfLocationIsCanaryConnected()
                    }
                }

                override fun onLocationUploaded(p0: Int) {
                    TODO("Not yet implemented")
                }

                override fun onLocationFailed(p0: Int, p1: java.lang.Error?) {
                    TODO("Not yet implemented")
                }

                // Try this signature for the method that handles failures
                //override fun onFailed(error: Error) {
                   // Log.e(TAG, "Failed to load location details for ID: $locationId, error: ${error.message}")
               // }
            }

            NavigineSdkManager.locationManager.addLocationListener(locationListener)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading location details: ${e.message}")
        }
    }

    // Helper method to get a loaded location
    private fun getLoadedLocation(locationId: Int): Location? {
        return loadedLocations[locationId]
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
                    if (locationCoordinatesMap.isNotEmpty()) {
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

        val userGlobalPoint = GlobalPoint(userLatitude, userLongitude)
        Log.d(TAG, "Checking if user is in a Canary location at: $userLatitude, $userLongitude")

        // First try to find an exact match - if the user is within a building
        for (entry in locationCoordinatesMap) {
            val locationCoords = entry.value

            if (locationCoords.originPoint != null) {
                // Check if the user is within any zone of this location
                if (isPointWithinAnyZone(userGlobalPoint, locationCoords)) {
                    isLocationCanaryConnected = true
                    matchedLocation = navigineLocations.find { it.id == locationCoords.id }
                    Log.d(TAG, "User is within a zone in Canary location: ${locationCoords.name}")
                    break
                }

                // Or check if the user is within the building boundaries
                if (isPointWithinLocation(userGlobalPoint, locationCoords)) {
                    isLocationCanaryConnected = true
                    matchedLocation = navigineLocations.find { it.id == locationCoords.id }
                    Log.d(TAG, "User is within Canary location: ${locationCoords.name}")
                    break
                }
            }
        }

        // If no exact match, check proximity to any venue or the building itself
        if (!isLocationCanaryConnected) {
            // Use a proximity threshold - for example, 50 meters
            val proximityThreshold = 50.0

            for (entry in locationCoordinatesMap) {
                val locationCoords = entry.value

                if (locationCoords.originPoint != null) {
                    // Check proximity to building origin
                    val distanceToOrigin = calculateDistance(
                        userLatitude, userLongitude,
                        locationCoords.originPoint.latitude, locationCoords.originPoint.longitude
                    )

                    // Include the building's size in the threshold
                    val buildingDiagonal = Math.sqrt(
                        (locationCoords.width * locationCoords.width +
                                locationCoords.height * locationCoords.height).toDouble()
                    )
                    val thresholdWithSize = buildingDiagonal + proximityThreshold

                    if (distanceToOrigin <= thresholdWithSize) {
                        isLocationCanaryConnected = true
                        matchedLocation = navigineLocations.find { it.id == locationCoords.id }
                        Log.d(TAG, "User is near Canary location: ${locationCoords.name} (${distanceToOrigin}m)")
                        break
                    }

                    // Check proximity to any venue in the location
                    val nearestVenueDistance = findDistanceToNearestVenue(userGlobalPoint, locationCoords)
                    if (nearestVenueDistance <= proximityThreshold) {
                        isLocationCanaryConnected = true
                        matchedLocation = navigineLocations.find { it.id == locationCoords.id }
                        Log.d(TAG, "User is near a venue in Canary location: ${locationCoords.name} (${nearestVenueDistance}m)")
                        break
                    }
                }
            }
        }

        // If still no match, try comparing by name (as a last resort)
        if (!isLocationCanaryConnected && locationNameText.text.isNotEmpty()) {
            val currentLocationName = locationNameText.text.toString().lowercase()

            for (locationInfo in navigineLocations) {
                if (locationInfo.name.lowercase().contains(currentLocationName) ||
                    currentLocationName.contains(locationInfo.name.lowercase())) {

                    isLocationCanaryConnected = true
                    matchedLocation = locationInfo
                    Log.d(TAG, "User is at Canary location by name match: ${locationInfo.name}")
                    break
                }
            }
        }

        // Update UI based on the result
        runOnUiThread {
            updateLocationInfo()
            updateSuggestions()
        }
    }

    private fun isPointWithinLocation(globalPoint: GlobalPoint, locationCoords: LocationCoordinates): Boolean {
        try {
            // Get the loaded location for this locationId
            val locationId = locationCoords.id
            val location = getLoadedLocation(locationId)

            if (location != null) {
                val sublocations = location.getSublocations()
                // Use iterator to avoid ambiguity
                val sublocationsIterator = sublocations.iterator() as Iterator<Sublocation>
                for (sublocation in sublocationsIterator) {
                    if (sublocation.getOriginPoint() == locationCoords.originPoint) {
                        // Convert the global point to local coordinates
                        val localPoint = sublocation.globalToLocal(globalPoint)

                        if (localPoint != null) {
                            // Check if the point is within the rectangle
                            return localPoint.getPoint().x >= 0 && localPoint.getPoint().x <= sublocation.getWidth() &&
                                    localPoint.getPoint().y >= 0 && localPoint.getPoint().y <= sublocation.getHeight()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if point is within location: ${e.message}")
        }

        return false
    }

    private fun isPointWithinAnyZone(globalPoint: GlobalPoint, locationCoords: LocationCoordinates): Boolean {
        try {
            // Get the loaded location for this locationId
            val locationId = locationCoords.id
            val location = getLoadedLocation(locationId)

            if (location != null) {
                val sublocations = location.getSublocations()
                // Use iterator to avoid ambiguity
                val sublocationsIterator = sublocations.iterator() as Iterator<Sublocation>
                for (sublocation in sublocationsIterator) {
                    if (sublocation.getOriginPoint() == locationCoords.originPoint) {
                        // Convert the global point to local coordinates
                        val localPoint = sublocation.globalToLocal(globalPoint)

                        if (localPoint != null) {
                            // Check if the point is within any zone
                            for (zone in locationCoords.zones) {
                                if (zone.polygon != null && isPointInPolygon(localPoint.getPoint(), zone.polygon)) {
                                    return true
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if point is within any zone: ${e.message}")
        }

        return false
    }

    private fun findDistanceToNearestVenue(globalPoint: GlobalPoint, locationCoords: LocationCoordinates): Double {
        var minDistance = Double.MAX_VALUE

        try {
            // Get the loaded location for this locationId
            val locationId = locationCoords.id
            val location = getLoadedLocation(locationId)

            if (location != null) {
                val sublocations = location.getSublocations()
                // Use iterator to avoid ambiguity
                val sublocationsIterator = sublocations.iterator() as Iterator<Sublocation>
                for (sublocation in sublocationsIterator) {
                    if (sublocation.getOriginPoint() == locationCoords.originPoint) {
                        // Convert the global point to local coordinates
                        val localPoint = sublocation.globalToLocal(globalPoint)

                        if (localPoint != null) {
                            // Check distance to each venue
                            for (venue in locationCoords.venues) {
                                if (venue.point != null) {
                                    val distance = Math.sqrt(
                                        Math.pow(localPoint.getPoint().x - venue.point.x.toDouble(), 2.0) +
                                                Math.pow(localPoint.getPoint().y - venue.point.y.toDouble(), 2.0)
                                    )

                                    if (distance < minDistance) {
                                        minDistance = distance
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding distance to nearest venue: ${e.message}")
        }

        return minDistance
    }

    // Helper function to determine if a point is inside a polygon
    private fun isPointInPolygon(point: Point, polygon: Polygon): Boolean {
        // Implementation of ray casting algorithm
        val x = point.x
        val y = point.y
        val polyPoints = polygon.getPoints()

        if (polyPoints.size < 3) return false

        var inside = false
        var j = polyPoints.size - 1

        for (i in polyPoints.indices) {
            val xi = polyPoints[i].x
            val yi = polyPoints[i].y
            val xj = polyPoints[j].x
            val yj = polyPoints[j].y

            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)

            if (intersect) inside = !inside
            j = i
        }

        return inside
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
            // Get the matched location coordinates
            val locationCoords = locationCoordinatesMap[matchedLocation!!.id]

            if (locationCoords != null) {
                // Extract venue names from the matched location
                val venueNames = locationCoords.venues.map { it.name }

                if (venueNames.isNotEmpty()) {
                    runOnUiThread {
                        val suggestionsAdapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            venueNames
                        )
                        suggestionsListView.adapter = suggestionsAdapter
                        Log.d(TAG, "Updated suggestions with ${venueNames.size} venues from ${locationCoords.name}")
                    }
                } else {
                    Log.d(TAG, "No venues found for location ${locationCoords.name}")
                    // Use default suggestions
                    setDefaultSuggestions()
                }
            } else {
                Log.d(TAG, "No coordinates data for matched location: ${matchedLocation!!.name}")
                // Use default suggestions
                setDefaultSuggestions()
            }
        } else {
            // Not in a Canary location, use default suggestions
            setDefaultSuggestions()
        }
    }

    private fun setDefaultSuggestions() {
        // Set some default suggestions when not in a Canary location
        val defaultSuggestions = listOf(
            "Nearby Places",
            "Restaurants",
            "Cafes",
            "Shopping",
            "Hotels"
        )

        runOnUiThread {
            val suggestionsAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                defaultSuggestions
            )
            suggestionsListView.adapter = suggestionsAdapter
            Log.d(TAG, "Set default suggestions")
        }
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