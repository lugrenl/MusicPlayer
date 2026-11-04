package com.example.musicplayer.ui.main

import com.example.musicplayer.R

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)
        val initialPadding = intArrayOf(
            root.paddingLeft,
            root.paddingTop,
            root.paddingRight,
            root.paddingBottom
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialPadding[0] + bars.left,
                initialPadding[1] + bars.top,
                initialPadding[2] + bars.right,
                initialPadding[3] + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}