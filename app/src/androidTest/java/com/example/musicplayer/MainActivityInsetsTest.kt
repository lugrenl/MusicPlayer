package com.example.musicplayer

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musicplayer.ui.main.MainActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInsetsTest {

    @Test
    fun systemBarInsetsAreAppliedIdempotentlyAndAfterRecreation() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.main)
                val insets = requireNotNull(
                    ViewCompat.getRootWindowInsets(root)
                ) { "Root WindowInsets are not available" }
                val bars = systemBars(insets)
                assertTrue("Expected a non-zero status bar inset", bars[1] > 0)

                ViewCompat.dispatchApplyWindowInsets(root, insets)
                val firstPadding = padding(root)
                assertArrayEquals(
                    "Root padding does not match system bar insets",
                    bars,
                    firstPadding
                )

                ViewCompat.dispatchApplyWindowInsets(root, insets)
                assertArrayEquals(
                    "Repeated insets dispatch changed the padding",
                    firstPadding,
                    padding(root)
                )
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val newRoot = activity.findViewById<View>(R.id.main)
                val newInsets = requireNotNull(
                    ViewCompat.getRootWindowInsets(newRoot)
                ) { "Root WindowInsets are not available after recreation" }
                val newBars = systemBars(newInsets)
                assertTrue(
                    "Expected a non-zero status bar inset after recreation",
                    newBars[1] > 0
                )

                ViewCompat.dispatchApplyWindowInsets(newRoot, newInsets)
                val firstPadding = padding(newRoot)
                assertArrayEquals(
                    "Root padding does not match insets after recreation",
                    newBars,
                    firstPadding
                )

                ViewCompat.dispatchApplyWindowInsets(newRoot, newInsets)
                assertArrayEquals(
                    "Repeated insets dispatch changed the padding after recreation",
                    firstPadding,
                    padding(newRoot)
                )
            }
        } finally {
            scenario.close()
        }
    }

    private fun systemBars(insets: WindowInsetsCompat): IntArray {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        return intArrayOf(bars.left, bars.top, bars.right, bars.bottom)
    }

    private fun padding(view: View): IntArray {
        return intArrayOf(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom
        )
    }
}
