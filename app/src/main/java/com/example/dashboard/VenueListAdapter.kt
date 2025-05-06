// VenueListAdapter.kt
package com.example.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dashboard.databinding.ItemSearchResultBinding // Use ViewBinding

class VenueListAdapter : RecyclerView.Adapter<VenueListAdapter.VenueViewHolder>() {

    // Change the list type to VenueData
    private var venueDataList: List<VenueData> = emptyList()

    // Update the setData method to accept List<VenueData>
    fun setData(venues: List<VenueData>) {
        venueDataList = venues
        // Call notifyDataSetChanged() on the main thread if you're calling setData from a background thread
        // For simple cases like this, it's usually called on the main thread after data is ready.
        // For better performance with large lists, consider using DiffUtil.
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VenueViewHolder {
        // Use ViewBinding to inflate the layout
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VenueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VenueViewHolder, position: Int) {
        val venueData = venueDataList[position]
        holder.bind(venueData)
    }

    override fun getItemCount(): Int = venueDataList.size

    // Update the ViewHolder to use ViewBinding and accept VenueData
    inner class VenueViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(venueData: VenueData) {
            // Bind data from the VenueData object
            binding.venueName.text = venueData.name

            // **CORRECTION:** Removed the line referencing venueData.nativeRef
            // as nativeRef is not a public property on the VenueData class defined previously.
            // If you added an 'id' property to VenueData (as discussed in the previous response),
            // you could display it here like: binding.venueInfo.text = "ID: ${venueData.id}"
            // Otherwise, you can leave venueInfo blank or show other relevant info.
            binding.venueInfo.text = "" // Set to empty or some other relevant info

            // Set click listener for the item if needed
            binding.root.setOnClickListener {
                // Handle venue selection
                // You might want to navigate to a detail view or start navigation
                // You can use venueData.name or potentially another identifier
                // if you added one to VenueData (like an ID if available).
                // Example: Toast.makeText(itemView.context, "Clicked on ${venueData.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}