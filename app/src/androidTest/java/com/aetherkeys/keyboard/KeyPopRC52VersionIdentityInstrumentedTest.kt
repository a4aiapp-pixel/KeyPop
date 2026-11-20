package com.aetherkeys.keyboard

import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyPopRC52VersionIdentityInstrumentedTest {
    @Test fun appAndKeyboardLabelAndActualInstalledVersionAgree() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val pm=ctx.packageManager
        val pkg=pm.getPackageInfo(ctx.packageName,0)
        assertEquals("KeyPop",ctx.applicationInfo.loadLabel(pm).toString())
        assertTrue(pkg.versionName.orEmpty().startsWith("1.0.52-rc52-"))
        assertTrue(pkg.longVersionCode>=100000L)
        val service=pm.getServiceInfo(ComponentName(ctx,AetherImeService::class.java),0)
        assertEquals("KeyPop Keyboard",service.loadLabel(pm).toString())
    }
}
