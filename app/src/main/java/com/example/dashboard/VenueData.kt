// VenueData.kt
package com.example.dashboard

import java.io.Serializable

data class VenueData(
    val name: String,
    // Add other fields from Venue that you need, e.g., level, category, etc.
    // For this example, we'll just use name and nativeRef.
) : Serializable