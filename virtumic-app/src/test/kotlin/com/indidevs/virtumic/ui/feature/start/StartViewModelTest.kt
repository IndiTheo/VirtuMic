package com.indidevs.virtumic.ui.feature.start

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indidevs.virtumic.data.repository.DefaultAppRepository
import com.indidevs.virtumic.data.repository.DefaultAudioMetadataRepository
import com.indidevs.virtumic.data.repository.DefaultSettingsRepository
import com.indidevs.virtumic.dependencies.VMicAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartViewModelTest {

    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var appContainer: VMicAppContainer
    private lateinit var settingsRepository: DefaultSettingsRepository
    private lateinit var viewModel: StartViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        appContainer = VMicAppContainer(context, scope)
        settingsRepository = DefaultSettingsRepository(context)
        viewModel = StartViewModel(
            appContainer = appContainer,
            appRepository = DefaultAppRepository(context),
            audioMetadataRepository = DefaultAudioMetadataRepository(context),
            settingsRepository = settingsRepository
        )
    }

    @Test
    fun `initial uiState has null playbackError`() {
        assertNull(viewModel.uiState.value.playbackError)
    }

    @Test
    fun `error recovery callback resets isStreaming to false`() {
        // Simulate error handling / stop streaming
        viewModel.toggleStreaming(false)
        assertFalse(viewModel.uiState.value.isStreaming)
    }

    @Test
    fun `dismissError resets playbackError in uiState to null`() {
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.playbackError)
    }

    @Test
    fun `toggleStreaming start failure raises Toast and updates playbackError in uiState`() {
        // Attempt to start streaming with an invalid/unsupported default source
        viewModel.toggleStreaming(true)

        // Verifies that a Toast was raised with the VirtuMic prefix
        val latestToast = org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        org.junit.Assert.assertNotNull(latestToast)
        org.junit.Assert.assertTrue(
            "Expected Toast to start with 'VirtuMic: ', but was: $latestToast",
            latestToast.startsWith("VirtuMic: ")
        )

        // Verifies playbackError is set in uiState
        org.junit.Assert.assertNotNull(viewModel.uiState.value.playbackError)
    }
}
