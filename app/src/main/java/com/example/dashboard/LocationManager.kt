package com.example.dashboard

import android.content.Context
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationManager(private val context: Context) {

    companion object {
        private const val TAG = "LocationManager"
        private const val EARTH_RADIUS = 6371000.0 // Earth radius in meters
    }

    // Data class to store location information
    data class LocationInfo(
        val id: String,
        val name: String,
        val polygonPoints: List<LatLng>,
        val isCanaryConnected: Boolean = false
    )

    // Custom LatLng class to avoid confusion with Google Maps LatLng
    data class LatLng(val latitude: Double, val longitude: Double)

    // Store the parsed locations
    private val locations = mutableListOf<LocationInfo>()

    // Track matched location
    private var matchedLocation: LocationInfo? = null
    private var distanceToBoundary: Double = -1.0

    init {
        // Load KML data from raw resources
        loadLocationsFromKml("st_stephens_boundary") // First polygon
        loadLocationsFromKml("delhi_high_court") // Second polygon
    }

    private fun loadLocationsFromKml(resourceName: String) {
        try {
            val inputStream: InputStream = context.resources.openRawResource(
                context.resources.getIdentifier(resourceName, "raw", context.packageName)
            )

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentLocationId = ""
            var currentLocationName = ""
            var coordinates = ""
            var inCoordinates = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Placemark" -> {
                                currentLocationId = ""
                                currentLocationName = ""
                                coordinates = ""
                            }
                            "name" -> {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    val text = parser.text.trim()
                                    if (text.isNotEmpty() && !text.startsWith("#")) {
                                        currentLocationName = text
                                    }
                                }
                            }
                            "styleUrl" -> {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    val styleUrl = parser.text.trim()
                                    if (styleUrl.contains("-")) {
                                        currentLocationId = styleUrl.substringAfterLast("-")
                                    }
                                }
                            }
                            "coordinates" -> {
                                inCoordinates = true
                                if (parser.next() == XmlPullParser.TEXT) {
                                    coordinates = parser.text.trim()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "Placemark" -> {
                                if (currentLocationName.isNotEmpty() && coordinates.isNotEmpty()) {
                                    val polygonPoints = parseCoordinates(coordinates)
                                    if (polygonPoints.isNotEmpty()) {
                                        locations.add(
                                            LocationInfo(
                                                id = currentLocationId.ifEmpty { "unknown" },
                                                name = currentLocationName,
                                                polygonPoints = polygonPoints,
                                                isCanaryConnected = currentLocationName.contains("St", ignoreCase = true) &&
                                                        currentLocationName.contains("Stephen", ignoreCase = true) &&
                                                        currentLocationName.contains("Hospital", ignoreCase = true) ||
                                                        currentLocationName.contains("Delhi High Court", ignoreCase = true) // Example for the second location
                                            )
                                        )
                                        Log.d(TAG, "Added location: $currentLocationName with ${polygonPoints.size} points")
                                    }
                                }
                            }
                            "coordinates" -> {
                                inCoordinates = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            inputStream.close()
            Log.d(TAG, "Loaded ${locations.size} locations from KML")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing KML file: $resourceName", e)
            // Handle error if needed, or leave it empty
        }
    }

    private fun parseCoordinates(coordinatesStr: String): List<LatLng> {
        val result = mutableListOf<LatLng>()
        val coordPairs = coordinatesStr.split(" ")

        for (pair in coordPairs) {
            if (pair.isNotEmpty()) {
                val parts = pair.split(",")
                if (parts.size >= 2) {
                    try {
                        val lng = parts[0].toDouble()
                        val lat = parts[1].toDouble()
                        result.add(LatLng(lat, lng))
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Invalid coordinate format: $pair", e)
                    }
                }
            }
        }

        return result
    }

    fun checkUserLocation(latitude: Double, longitude: Double): LocationInfo? {
        matchedLocation = null
        distanceToBoundary = -1.0

        if (latitude == 0.0 || longitude == 0.0) {
            Log.d(TAG, "Invalid user coordinates")
            return null
        }

        val userPoint = LatLng(latitude, longitude)

        for (location in locations) {
            if (isPointInPolygon(userPoint, location.polygonPoints)) {
                matchedLocation = location
                Log.d(TAG, "User is inside location: ${location.name}")
                return location
            } else {
                val distance = calculateDistanceToBoundary(userPoint, location.polygonPoints)
                if (distanceToBoundary < 0 || distance < distanceToBoundary) {
                    distanceToBoundary = distance
                    Log.d(TAG, "Distance to ${location.name} boundary: $distance meters")
                }
            }
        }

        return null
    }

    private fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            val intersect = ((yi > point.latitude) != (yj > point.latitude)) &&
                    (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)

            if (intersect) inside = !inside
            j = i
        }

        return inside
    }

    private fun calculateDistanceToBoundary(point: LatLng, polygon: List<LatLng>): Double {
        var minDistance = Double.MAX_VALUE

        for (i in 0 until polygon.size - 1) {
            val distance = distanceToLine(point, polygon[i], polygon[i + 1])
            if (distance < minDistance) {
                minDistance = distance
            }
        }

        if (polygon.size > 1) {
            val distance = distanceToLine(point, polygon[polygon.size - 1], polygon[0])
            if (distance < minDistance) {
                minDistance = distance
            }
        }

        return minDistance
    }

    private fun distanceToLine(point: LatLng, lineStart: LatLng, lineEnd: LatLng): Double {
        val d1 = haversineDistance(point, lineStart)
        val d2 = haversineDistance(point, lineEnd)
        return minOf(d1, d2)
    }

    private fun haversineDistance(point1: LatLng, point2: LatLng): Double {
        val lat1Rad = Math.toRadians(point1.latitude)
        val lon1Rad = Math.toRadians(point1.longitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val lon2Rad = Math.toRadians(point2.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS * c
    }

    fun getDistanceToBoundary(): Double {
        return distanceToBoundary
    }

    fun getMatchedLocation(): LocationInfo? {
        return matchedLocation
    }

    fun getAllLocations(): List<LocationInfo> {
        return locations
    }

    fun getVenuesForSublocation(sublocationName: String): List<String> {
        return when (sublocationName) {
            "FIRST FLOOR" -> listOf("Reception", "Waiting Room", "Pharmacy", "Cafeteria")
            "GROUND FLOOR" -> listOf("Emergency", "Registration", "Information Desk", "Security")
            else -> emptyList()
        }
    }
}