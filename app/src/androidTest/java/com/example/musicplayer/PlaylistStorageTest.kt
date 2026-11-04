package com.example.musicplayer

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musicplayer.data.PlaylistStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistStorageTest {

    @Test
    fun encodesAndDecodesJsonInOrder() {
        val uris = listOf(
            Uri.parse("content://provider/document/a"),
            Uri.parse("content://provider/document/b")
        )

        val encoded = PlaylistStorage.encode(uris)
        val decoded = PlaylistStorage.decode(encoded) as PlaylistStorage.Decoded.Parsed

        assertTrue(encoded.startsWith("["))
        assertFalse(decoded.legacy)
        assertEquals(uris, decoded.uris)
    }

    @Test
    fun jsonDecodeRemovesEmptyAndDuplicateUris() {
        val raw = """["content://provider/document/a","","content://provider/document/a","content://provider/document/b"]"""

        val decoded = PlaylistStorage.decode(raw) as PlaylistStorage.Decoded.Parsed

        assertEquals(
            listOf(
                Uri.parse("content://provider/document/a"),
                Uri.parse("content://provider/document/b")
            ),
            decoded.uris
        )
    }

    @Test
    fun jsonDecodePreservesUriWithComma() {
        val uri = Uri.parse("content://provider/document/a, b")

        val decoded = PlaylistStorage.decode(PlaylistStorage.encode(listOf(uri))) as PlaylistStorage.Decoded.Parsed

        assertEquals(listOf(uri), decoded.uris)
    }

    @Test
    fun decodesLegacyFormat() {
        val decoded = PlaylistStorage.decode(
            "content://provider/document/a, content://provider/document/b"
        ) as PlaylistStorage.Decoded.Parsed

        assertTrue(decoded.legacy)
        assertEquals(
            listOf(
                Uri.parse("content://provider/document/a"),
                Uri.parse("content://provider/document/b")
            ),
            decoded.uris
        )
    }

    @Test
    fun invalidContentIsRejected() {
        assertSame(PlaylistStorage.Decoded.Invalid, PlaylistStorage.decode("{broken"))
        assertSame(PlaylistStorage.Decoded.Invalid, PlaylistStorage.decode("plain text"))
        assertSame(PlaylistStorage.Decoded.Invalid, PlaylistStorage.decode("[1,2]"))
    }

    @Test
    fun emptyContentIsEmpty() {
        assertSame(PlaylistStorage.Decoded.Empty, PlaylistStorage.decode(null))
        assertSame(PlaylistStorage.Decoded.Empty, PlaylistStorage.decode(""))
        assertSame(PlaylistStorage.Decoded.Empty, PlaylistStorage.decode("   "))
        assertSame(PlaylistStorage.Decoded.Empty, PlaylistStorage.decode(", "))
    }

    @Test
    fun encodesEmptyPlaylistAsJsonArray() {
        val encoded = PlaylistStorage.encode(emptyList())
        val decoded = PlaylistStorage.decode(encoded) as PlaylistStorage.Decoded.Parsed

        assertEquals("[]", encoded)
        assertFalse(decoded.legacy)
        assertTrue(decoded.uris.isEmpty())
    }
}
