package com.aetherkeys.keyboard

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPopRC54VersionIdentityInstrumentedTest {
    @Test fun installedAppAndImeRetainCorrectLabelsAndUpgradeVersion() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pm=context.packageManager
        assertEquals("KeyPop",context.applicationInfo.loadLabel(pm).toString())
        val app=pm.getPackageInfo(context.packageName,0)
        assertTrue(app.versionName.orEmpty().contains("1.0.54-rc54"))
        assertTrue(app.longVersionCode>=120000L)
        val service=pm.getServiceInfo(
            ComponentName(context,AetherImeService::class.java),0)
        assertEquals("KeyPop Keyboard",service.loadLabel(pm).toString())
    }
}