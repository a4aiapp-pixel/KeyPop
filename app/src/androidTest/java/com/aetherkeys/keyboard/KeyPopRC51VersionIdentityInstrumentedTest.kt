package com.aetherkeys.keyboard

import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPopRC51VersionIdentityInstrumentedTest {
    @Test fun appAndImeKeepNeutralNameButShowInstalledRc51Version() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pm=context.packageManager
        assertEquals("KeyPop",context.applicationInfo.loadLabel(pm).toString())
        val packageInfo=pm.getPackageInfo(context.packageName,0)
        assertTrue(packageInfo.versionName.orEmpty().contains("1.0.51-rc51"))
        assertTrue(packageInfo.longVersionCode>=90000L)
        val service=pm.getServiceInfo(
            ComponentName(context,AetherImeService::class.java),0)
        assertEquals("KeyPop Keyboard",service.loadLabel(pm).toString())
    }
}
