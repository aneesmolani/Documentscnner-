package com.example.documentscanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Test fun appContextLoads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context.packageName)
    }
}
