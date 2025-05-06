package com.example.dashboard

import android.animation.LayoutTransition
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dashboard.databinding.ActivitySearchBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Locale

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var venueAdapter: VenueListAdapter
    private var venueDataList: List<VenueData> = emptyList()

    companion object {
        private const val TAG = "SearchActivity"
        const val EXTRA_VENUE_LIST_JSON = "venue_list_json"
    }

    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText: String? = result.data!!.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.get(0)

            spokenText?.let {
                binding.searchEditText.setText(it)
                binding.searchEditText.setSelection(it.length)
            } ?: run {
                Log.d(TAG, "Speech recognition returned no results.")
                Toast.makeText(this, "No speech detected", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Speech recognition failed or cancelled. Result code: ${result.resultCode}")
        }
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
            // When clearing, hide the list/empty state if focus is lost
            if (!binding.searchEditText.hasFocus()) {
                binding.venuesRecyclerView.visibility = View.GONE
                binding.emptyView.visibility = View.GONE
            }
        }

        // Set up microphone button
        binding.micButton.setOnClickListener {
            startSpeechRecognition()
        }

        // Initialize RecyclerView and adapter
        setupRecyclerView()

        // Extract venue data from intent
        extractVenueData()

        // Process initial query if provided
        intent.getStringExtra("initial_query")?.let { initialQuery ->
            binding.searchEditText.setText(initialQuery)
            // The TextWatcher will handle filtering and visibility once the search bar is focused
        }

        // Set up search listener
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterVenues(s.toString())
                binding.clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        // **MODIFIED: Add Focus Listener to show list on focus**
        binding.searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                Log.d(TAG, "Search EditText gained focus.")
                // When the search bar gains focus, show the list or empty view
                // based on the current data and filter.
                val currentQuery = binding.searchEditText.text.toString()
                if (currentQuery.isBlank() && venueDataList.isNotEmpty()) {
                    // If no text, show the full list
                    venueAdapter.setData(venueDataList) // Ensure adapter has full data if query is blank
                    binding.venuesRecyclerView.visibility = View.VISIBLE
                    binding.emptyView.visibility = View.GONE
                } else if (venueAdapter.itemCount > 0) {
                    // If there are filtered results, show the list
                    binding.venuesRecyclerView.visibility = View.VISIBLE
                    binding.emptyView.visibility = View.GONE
                } else {
                    // If no filtered results (either empty query and no data, or text with no matches)
                    binding.venuesRecyclerView.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                }

            } else {
                Log.d(TAG, "Search EditText lost focus.")
                // When focus is lost, hide the list/empty view unless there's text
                if (binding.searchEditText.text.isNullOrEmpty()) {
                    binding.venuesRecyclerView.visibility = View.GONE
                    binding.emptyView.visibility = View.GONE
                }
            }
        }

        // Set LayoutTransition programmatically to the root ConstraintLayout
        (binding.root as? ViewGroup)?.layoutTransition = LayoutTransition()

        // **REMOVED: binding.searchEditText.requestFocus()**
        // We don't want the search bar to be focused automatically on activity start.
        // The user's click will provide focus.
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

    private fun setupRecyclerView() {
        venueAdapter = VenueListAdapter { venueData ->
            binding.searchEditText.setText(venueData.name)
            binding.searchEditText.setSelection(binding.searchEditText.text.length)
            // Optional: Hide keyboard after selection
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)

            // Optional: Hide the list after selecting an item
            // binding.venuesRecyclerView.visibility = View.GONE
            // binding.emptyView.visibility = View.GONE
        }
        binding.venuesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = venueAdapter
        }

        binding.venuesRecyclerView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
    }

    private fun extractVenueData() {
        try {
            val venueListJson = intent.getStringExtra(MainActivity.EXTRA_VENUE_LIST_JSON)
            if (venueListJson != null) {
                Log.d(TAG, "Received venue JSON: $venueListJson")

                val gson = Gson()
                val venueDataListType: Type = object : TypeToken<List<VenueData>>() {}.type
                venueDataList = gson.fromJson(venueListJson, venueDataListType)

                Log.d(TAG, "Deserialized ${venueDataList.size} VenueData objects")

                // No initial filtering or visibility change here.
                // This will happen when the search bar is clicked and gains focus.

            } else {
                Log.e(TAG, "No venue data was passed")
                binding.emptyView.visibility = View.VISIBLE // Show empty if no data passed
                binding.venuesRecyclerView.visibility = View.GONE
                venueDataList = emptyList()
                Toast.makeText(this, "Failed to load venue data", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting venue data: ${e.message}", e)
            binding.emptyView.visibility = View.VISIBLE // Show empty on error
            binding.venuesRecyclerView.visibility = View.GONE
            venueDataList = emptyList()
            Toast.makeText(this, "Error loading venue data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun filterVenues(query: String) {
        val filteredList = if (query.isBlank()) {
            venueDataList // Show all if query is empty
        } else {
            venueDataList.filter {
                it.name.contains(query, ignoreCase = true) // Filter based on name
            }
        }

        venueAdapter.setData(filteredList)

        // Update visibility based on filtered results, but only if the search bar has focus.
        // If it doesn't have focus, the list/empty state should be hidden (handled by focus listener).
        if (binding.searchEditText.hasFocus()) {
            if (filteredList.isEmpty()) {
                binding.venuesRecyclerView.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.venuesRecyclerView.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
            }
        }
        // If not focused, visibility remains as set by the focus listener (hidden if text is empty)
    }
}