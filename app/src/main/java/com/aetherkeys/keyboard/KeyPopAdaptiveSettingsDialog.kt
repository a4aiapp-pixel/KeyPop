package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

/** Opt-in controls to be linked from KeyPop's existing Settings navigation. */
object KeyPopAdaptiveSettingsDialog {
    fun show(activity: Activity, erase: (callback: (Boolean) -> Unit) -> Unit) {
        val prefs = activity.getSharedPreferences("keypop", Context.MODE_PRIVATE)
        val keys = arrayOf("adaptive_personal_words", "adaptive_finished_phrases",
            "adaptive_addresses")
        val labels = arrayOf("Remember accepted words on this device",
            "Learn finished phrases I explicitly confirm",
            "Allow private address phrase memory (may be sensitive)")
        val checked = BooleanArray(keys.size) { prefs.getBoolean(keys[it], false) }
        AlertDialog.Builder(activity).setTitle("KeyPop Personal Intelligence")
            .setMessage("Opt in to each memory category. Predictions are stored locally, " +
                "encrypted, and excluded from password, incognito and restricted fields. " +
                "The keyboard cannot search your prior WhatsApp messages.")
            .setMultiChoiceItems(labels, checked) { _, index, value -> checked[index] = value }
            .setNeutralButton("Erase learned history") { _, _ ->
                AlertDialog.Builder(activity).setTitle("Erase learned words and phrases?")
                    .setMessage("This removes locally remembered personal predictions.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Erase") { _, _ ->
                        erase { success ->
                            activity.runOnUiThread {
                                AlertDialog.Builder(activity)
                                    .setMessage(if (success) "Learned history erased" else "Unable to erase history")
                                    .setPositiveButton("OK", null).show()
                            }
                        }
                    }.show()
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().apply {
                    keys.forEachIndexed { index, key -> putBoolean(key, checked[index]) }
                }.apply()
            }.show()
    }
}
