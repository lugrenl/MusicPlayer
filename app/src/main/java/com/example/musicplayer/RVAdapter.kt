package com.example.musicplayer

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RVAdapter : RecyclerView.Adapter<RVAdapter.MyViewHolder>() {

    private var checkedPosition: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.view_a, parent, false))
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.setChecked(position == checkedPosition)

    }

    override fun getItemCount(): Int {
        return 10
    }

    private fun setCheckedPosition(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        checkedPosition = position
        notifyItemChanged(position, Unit)
        notifyItemChanged(checkedPosition, Unit)
    }

    inner class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val radioButton: RadioButton = view.findViewById(R.id.radio_button)

        private val checkedListener: CompoundButton.OnCheckedChangeListener =
            CompoundButton.OnCheckedChangeListener { _, _ -> setCheckedPosition(bindingAdapterPosition) }

        init {
            radioButton.setOnCheckedChangeListener(checkedListener)
        }

        fun setChecked(checked: Boolean) {
            radioButton.setOnCheckedChangeListener(null)
            radioButton.isChecked = checked
            radioButton.setOnCheckedChangeListener(checkedListener)
        }

    }
}

