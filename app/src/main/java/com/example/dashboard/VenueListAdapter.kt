package com.example.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dashboard.databinding.ItemSearchResultBinding

// Modify the constructor to accept a click listener lambda
class VenueListAdapter(private val onItemClick: (VenueData) -> Unit) : RecyclerView.Adapter<VenueListAdapter.VenueViewHolder>() {

    private var venueDataList: List<VenueData> = emptyList()

    fun setData(venues: List<VenueData>) {
        venueDataList = venues
        notifyDataSetChanged() // Consider DiffUtil for larger lists
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VenueViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VenueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VenueViewHolder, position: Int) {
        val venueData = venueDataList[position]
        holder.bind(venueData)
    }

    override fun getItemCount(): Int = venueDataList.size

    inner class VenueViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(venueData: VenueData) {
            binding.venueName.text = venueData.name
            // You can add more info here if your VenueData has it
            binding.venueInfo.text = "" // Example: Set to empty or other info

            // Set click listener using the lambda passed to the adapter
            binding.root.setOnClickListener {
                onItemClick.invoke(venueData) // Call the click listener with the clicked VenueData
            }
        }
    }
}