package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import com.gamelaunch.frontend.domain.lockedmode.PinResult
import com.gamelaunch.frontend.ui.lockedmode.LockedModeDialogStep
import com.gamelaunch.frontend.ui.lockedmode.LockedModeSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockedModeSettingsViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLockedModeRepository
    private lateinit var viewModel: LockedModeSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeLockedModeRepository()
        viewModel = LockedModeSettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setup and confirmation configures PIN`() = runTest(dispatcher) {
        viewModel.startSetup()
        viewModel.submitPin("1234")
        assertEquals(LockedModeDialogStep.CONFIRM_PIN, viewModel.uiState.value.dialogStep)

        viewModel.submitPin("1234")
        advanceUntilIdle()

        assertEquals("1234", repository.configuredPin)
        assertNull(viewModel.uiState.value.dialogStep)
    }

    @Test
    fun `setup mismatch returns to PIN creation`() = runTest(dispatcher) {
        viewModel.startSetup()
        viewModel.submitPin("1234")
        viewModel.submitPin("4321")

        assertEquals(LockedModeDialogStep.CREATE_PIN, viewModel.uiState.value.dialogStep)
        assertEquals("PINs do not match", viewModel.uiState.value.error)
        assertNull(repository.configuredPin)
    }

    @Test
    fun `PIN change requires only new PIN confirmation`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startChange()
        viewModel.submitPin("5678")
        viewModel.submitPin("5678")
        advanceUntilIdle()

        assertEquals("5678", repository.pin)
        assertEquals("5678", repository.configuredPin)
        assertNull(viewModel.uiState.value.dialogStep)
    }

    @Test
    fun `new PIN mismatch returns to new PIN entry`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startChange()
        viewModel.submitPin("5678")
        viewModel.submitPin("8765")

        assertEquals(LockedModeDialogStep.NEW_PIN, viewModel.uiState.value.dialogStep)
        assertEquals("PINs do not match", viewModel.uiState.value.error)
        assertNull(repository.configuredPin)
    }

    @Test
    fun `PIN removal is direct`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.removePin()
        advanceUntilIdle()

        assertNull(repository.pin)
    }

    @Test
    fun `lock now activates enabled mode without a PIN`() = runTest(dispatcher) {
        viewModel.setEnabled(true)
        advanceUntilIdle()
        viewModel.lockNow()
        advanceUntilIdle()

        assertEquals(LockedModeState.LOCKED, repository.currentState)
    }

    @Test
    fun `dismissal clears workflow and temporary PINs`() = runTest(dispatcher) {
        viewModel.startSetup()
        viewModel.submitPin("1234")
        viewModel.dismissDialog()

        assertNull(viewModel.uiState.value.dialogStep)
        assertNull(viewModel.uiState.value.error)

        viewModel.startSetup()
        viewModel.submitPin("5678")
        viewModel.submitPin("1234")

        assertEquals(LockedModeDialogStep.CREATE_PIN, viewModel.uiState.value.dialogStep)
        assertEquals("PINs do not match", viewModel.uiState.value.error)
        assertNull(repository.configuredPin)
    }
}

private class FakeLockedModeRepository : LockedModeRepository {
    private val stateFlow = MutableStateFlow(LockedModeState.DISABLED)
    private val hasPinFlow = MutableStateFlow(false)
    override val state: Flow<LockedModeState> = stateFlow
    override val hasPin: Flow<Boolean> = hasPinFlow
    val currentState: LockedModeState get() = stateFlow.value

    var pin: String? = null
    var configuredPin: String? = null

    override suspend fun configure(pin: String): PinResult {
        configuredPin = pin
        this.pin = pin
        hasPinFlow.value = true
        return PinResult.Success
    }

    override suspend fun setEnabled(enabled: Boolean) {
        stateFlow.value = if (enabled) LockedModeState.READY else LockedModeState.DISABLED
    }

    override suspend fun activate() {
        if (stateFlow.value != LockedModeState.DISABLED) stateFlow.value = LockedModeState.LOCKED
    }

    override suspend fun verify(pin: String): PinResult = when {
        this.pin == null -> PinResult.NotConfigured
        this.pin == pin -> PinResult.Success
        else -> PinResult.InvalidPin
    }

    override suspend fun unlock(pin: String?): PinResult =
        if (this.pin == null) PinResult.Success else verify(pin.orEmpty())

    override suspend fun removePin() {
        pin = null
        hasPinFlow.value = false
    }

    override suspend fun isLocked(): Boolean = stateFlow.value == LockedModeState.LOCKED
}
