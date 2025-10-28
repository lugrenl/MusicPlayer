package com.example.musicplayer

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class RVAdapter : RecyclerView.Adapter<MyViewHolder>() {

    var createCounter = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        Log.e("RVAdapter", "Create ${createCounter++}")
        val view: View = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.view_a, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        Log.e("RVAdapter", "Bind $position")
    }

    override fun getItemCount(): Int {
        return 100
    }
}

class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {

}