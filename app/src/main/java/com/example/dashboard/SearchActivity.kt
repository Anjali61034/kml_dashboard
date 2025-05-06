// SearchActivity.kt
package com.example.dashboard

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dashboard.databinding.ActivitySearchBinding
import com.google.gson.Gson // Keep Gson for deserialization
import com.google.gson.reflect.TypeToken
// No longer need to import Navigine Venue directly here
// import com.navigine.idl.java.Venue
import java.lang.reflect.Type

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var venueAdapter: VenueListAdapter
    private var venueDataList: List<VenueData> = emptyList() // Use the VenueData list

    companion object {
        private const val TAG = "SearchActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_search)

        // Set up back button
        binding.backButton.setOnClickListener {
            onBackPressed()
        }

        // Set up clear button
        binding.clearButton.setOnClickListener {
            binding.searchEditText.text.clear()
        }

        // Initialize RecyclerView and adapter
        setupRecyclerView()

        // Extract venue data from intent
        extractVenueData()

        // Process initial query if provided
        intent.getStringExtra("initial_query")?.let { initialQuery ->
            binding.searchEditText.setText(initialQuery)
            filterVenues(initialQuery)
        }

        // Set up search listener
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filterVenues(s.toString())
            }
        })

        // Set focus to search field
        binding.searchEditText.requestFocus()
    }

    private fun setupRecyclerView() {
        venueAdapter = VenueListAdapter() // Adapter now expects List<VenueData>
        binding.venuesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = venueAdapter
            setHasFixedSize(true)
        }

        // Set empty view if we need it
        binding.emptyView.visibility = View.GONE
    }

    private fun extractVenueData() {
        try {
            val venueListJson = intent.getStringExtra(MainActivity.EXTRA_VENUE_LIST_JSON)
            if (venueListJson != null) {
                Log.d(TAG, "Received venue JSON: $venueListJson")

                val gson = Gson()
                // Define the type for Gson to properly deserialize the JSON into a list of VenueData
                val venueDataListType: Type = object : TypeToken<List<VenueData>>() {}.type
                venueDataList = gson.fromJson(venueListJson, venueDataListType)

                Log.d(TAG, "Deserialized ${venueDataList.size} VenueData objects")

                if (venueDataList.isNotEmpty()) {
                    // Update the adapter with the VenueData list
                    venueAdapter.setData(venueDataList)
                    binding.emptyView.visibility = View.GONE
                    binding.venuesRecyclerView.visibility = View.VISIBLE
                } else {
                    // Handle empty list
                    binding.emptyView.visibility = View.VISIBLE
                    binding.venuesRecyclerView.visibility = View.GONE
                    Log.d(TAG, "No venues available")
                }
            } else {
                Log.e(TAG, "No venue data was passed")
                // Show empty state
                binding.emptyView.visibility = View.VISIBLE
                venueDataList = emptyList() // Ensure the list is empty
                binding.venuesRecyclerView.visibility = View.GONE
                Toast.makeText(this, "Failed to load venue data", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting venue data: ${e.message}", e)
            // Show error state
            binding.emptyView.visibility = View.VISIBLE
            binding.venuesRecyclerView.visibility = View.GONE
            venueDataList = emptyList() // Ensure the list is empty
            Toast.makeText(this, "Error loading venue data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun filterVenues(query: String) {
        if (query.isBlank()) {
            // If the query is empty, show all venues
            venueAdapter.setData(venueDataList) // Filter the VenueData list
        } else {
            // Filter venues based on the query
            val filteredList = venueDataList.filter {
                it.name.contains(query, ignoreCase = true) // Filter based on the 'name' property of VenueData
            }
            venueAdapter.setData(filteredList)
        }

        // Update empty view visibility
        if (venueAdapter.itemCount == 0) {
            binding.emptyView.visibility = View.VISIBLE
            binding.venuesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.venuesRecyclerView.visibility = View.VISIBLE
        }
    }
}