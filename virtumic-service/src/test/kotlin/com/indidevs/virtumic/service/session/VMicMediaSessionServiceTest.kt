package com.indidevs.virtumic.service.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class VMicMediaSessionServiceTest {

    @Test
    fun `service instantiates and destroys cleanly`() {
        val serviceController = Robolectric.buildService(VMicMediaSessionService::class.java)
        val service = serviceController.create().get()
        assertNotNull(service)
        service.onDestroy()
    }
}
