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
    fun `incorrect current PIN is rejected before requesting new PIN`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startChange()
        viewModel.submitPin("9999")
        advanceUntilIdle()

        assertEquals(LockedModeDialogStep.CURRENT_PIN, viewModel.uiState.value.dialogStep)
        assertEquals("Incorrect PIN", viewModel.uiState.value.error)
    }

    @Test
    fun `PIN change succeeds after current and new PIN confirmation`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startChange()
        viewModel.submitPin("1234")
        advanceUntilIdle()
        viewModel.submitPin("5678")
        viewModel.submitPin("5678")
        advanceUntilIdle()

        assertEquals("5678", repository.pin)
        assertEquals("1234" to "5678", repository.changedPins)
        assertNull(viewModel.uiState.value.dialogStep)
    }

    @Test
    fun `new PIN mismatch returns to new PIN entry`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startChange()
        viewModel.submitPin("1234")
        advanceUntilIdle()
        viewModel.submitPin("5678")
        viewModel.submitPin("8765")

        assertEquals(LockedModeDialogStep.NEW_PIN, viewModel.uiState.value.dialogStep)
        assertEquals("PINs do not match", viewModel.uiState.value.error)
        assertNull(repository.changedPins)
    }

    @Test
    fun `removal succeeds with correct PIN`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startRemove()
        viewModel.submitPin("1234")
        advanceUntilIdle()

        assertNull(repository.pin)
        assertNull(viewModel.uiState.value.dialogStep)
    }

    @Test
    fun `removal keeps dialog open with incorrect PIN`() = runTest(dispatcher) {
        repository.pin = "1234"
        viewModel.startRemove()
        viewModel.submitPin("9999")
        advanceUntilIdle()

        assertEquals("1234", repository.pin)
        assertEquals(LockedModeDialogStep.REMOVE, viewModel.uiState.value.dialogStep)
        assertEquals("Incorrect PIN", viewModel.uiState.value.error)
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
    private val stateFlow = MutableStateFlow(LockedModeState.UNCONFIGURED)
    override val state: Flow<LockedModeState> = stateFlow

    var pin: String? = null
    var configuredPin: String? = null
    var changedPins: Pair<String, String>? = null

    override suspend fun configure(pin: String): PinResult {
        configuredPin = pin
        this.pin = pin
        stateFlow.value = LockedModeState.READY
        return PinResult.Success
    }

    override suspend fun activate() {
        if (pin != null) stateFlow.value = LockedModeState.LOCKED
    }

    override suspend fun verify(pin: String): PinResult = when {
        this.pin == null -> PinResult.NotConfigured
        this.pin == pin -> PinResult.Success
        else -> PinResult.InvalidPin
    }

    override suspend fun unlock(pin: String): PinResult = verify(pin)

    override suspend fun changePin(currentPin: String, newPin: String): PinResult {
        val result = verify(currentPin)
        if (result == PinResult.Success) {
            changedPins = currentPin to newPin
            pin = newPin
        }
        return result
    }

    override suspend fun remove(currentPin: String): PinResult {
        val result = verify(currentPin)
        if (result == PinResult.Success) {
            pin = null
            stateFlow.value = LockedModeState.UNCONFIGURED
        }
        return result
    }

    override suspend fun isLocked(): Boolean = stateFlow.value == LockedModeState.LOCKED
}
