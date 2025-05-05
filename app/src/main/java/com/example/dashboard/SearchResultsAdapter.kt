package com.example.dashboard // Make sure this matches your package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navigine.idl.java.Venue // Import the Venue class

class SearchResultsAdapter(
    private var venueList: List<Venue>,
    private val onItemClick: (Venue) -> Unit // Lambda for click handling
) : RecyclerView.Adapter<SearchResultsAdapter.VenueViewHolder>() {

    // ViewHolder class to hold the views for each item
    class VenueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val venueNameTextView: TextView = itemView.findViewById(R.id.venueNameTextView) // Assuming you have a TextView with this ID in item_search_result.xml
        // Add other views if needed
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VenueViewHolder {
        // Inflate the item layout
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false) // Assuming your item layout is named item_search_result.xml
        return VenueViewHolder(itemView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: VenueViewHolder, position: Int) {
        val currentVenue = venueList[position]

        // Bind the venue data to the views
        holder.venueNameTextView.text = currentVenue.name // Assuming the Venue class has a 'name' property/getter

        // Set click listener for the item
        holder.itemView.setOnClickListener {
            onItemClick(currentVenue)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return venueList.size
    }

    // Method to update the list of venues and refresh the RecyclerView
    fun updateResults(newVenueList: List<Venue>) {
        venueList = newVenueList
        notifyDataSetChanged() // Notify the adapter that the data has changed
    }
}
