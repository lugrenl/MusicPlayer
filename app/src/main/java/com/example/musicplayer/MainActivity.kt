package com.example.musicplayer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.adapter = RVAdapter()
//        recycler.setRecycledViewPool(object : RecyclerView.RecycledViewPool() {
//            override fun getRecycledView(viewType: Int): RecyclerView.ViewHolder? {
//                //return super.getRecycledView(viewType)
//                return null
//            }
//        })
        //recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.HORIZONTAL))
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }
}