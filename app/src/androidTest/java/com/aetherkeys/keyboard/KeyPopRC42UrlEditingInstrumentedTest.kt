package com.aetherkeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android editing baseline. This DOES NOT prove the host keyboard's live
 * suggestion handler is connected; see docs/URL_EDITING_REGRESSIONS.md. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC42UrlEditingInstrumentedTest {
    @Test fun urlEditInTheMiddlePreservesTheFullLongAddress() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val url = "https://example.com/" + "folder/".repeat(45) + "?long=query&keep=value"
            val editor = EditText(instrumentation.targetContext).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(url)
                val cut = url.indexOf("folder/")
                setSelection(cut, cut + "folder".length)
            }
            val info = EditorInfo()
            val connection = editor.onCreateInputConnection(info)
            assertNotNull(connection)
            assertTrue(KeyPopUrlEditingPolicy.decide(true, false, url.take(20)).protected)
            assertTrue(connection.commitText("archive", 1))
            assertEquals(url.replaceFirst("folder", "archive"), editor.text.toString())
            assertTrue(editor.text.toString().endsWith("?long=query&keep=value"))
        }
    }

    @Test fun typingAndDeletingInsideUrlDoesNotClobberSuffix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val url = "https://example.com/products/item-123?ref=abc"
            val editor = EditText(instrumentation.targetContext).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(url)
                val cursor = url.indexOf("item-123") + 4
                setSelection(cursor)
            }
            val connection = editor.onCreateInputConnection(EditorInfo())
            assertTrue(connection.commitText("NEW", 1))
            assertEquals(url.replace("item-123", "itemNEW-123"), editor.text.toString())
            assertTrue(connection.deleteSurroundingTextInCodePoints(3, 0))
            assertEquals(url, editor.text.toString())
        }
    }

    @Test fun selectingEntireUrlCanDeleteWithoutAHiddenPartialValue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val editor = EditText(instrumentation.targetContext).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText("https://example.com/" + "a".repeat(240))
                setSelection(0, text.length)
            }
            val connection = editor.onCreateInputConnection(EditorInfo())
            assertTrue(connection.commitText("", 1))
            assertEquals("", editor.text.toString())
        }
    }
}
