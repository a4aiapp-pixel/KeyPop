package com.aetherkeys.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Do not permit a newer signed APK to display an old RC47 app/IME label. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC50VersionIdentityInstrumentedTest {
    private val context:Context
        get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun appLabelIsStableAndActualVersionComesFromInstalledPackage() {
        val pm=context.packageManager
        assertEquals("KeyPop",context.applicationInfo.loadLabel(pm).toString())
        val pkg=pm.getPackageInfo(context.packageName,0)
        assertTrue(pkg.versionName.orEmpty().contains("1.0.50-rc50"))
        assertTrue(pkg.longVersionCode>=80000L)
    }

    @Test fun enabledInputMethodDoesNotMislabelCurrentBuildAsRc47() {
        val service=context.packageManager.getServiceInfo(
            ComponentName(context,AetherImeService::class.java),0)
        assertEquals("KeyPop Keyboard",service.loadLabel(context.packageManager).toString())
    }
}
