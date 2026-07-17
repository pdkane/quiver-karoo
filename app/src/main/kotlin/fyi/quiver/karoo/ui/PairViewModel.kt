package fyi.quiver.karoo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.quiver.karoo.QuiverApi
import fyi.quiver.karoo.QuiverPrefs
import fyi.quiver.karoo.SeedMapper
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.Bikes
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairUiState(
    val connected: Boolean = false,
    val paired: Boolean = false,
    val serial: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Drives the pairing screen. Holds its own [KarooSystemService] so it can read the
 * head-unit serial (used as the paired device's label) and the at-rest garage
 * snapshot (Bikes / SavedDevices / UserProfile) to seed Quiver right after pairing.
 */
class PairViewModel(app: Application) : AndroidViewModel(app) {

    private val karoo = KarooSystemService(app.applicationContext)
    private val _state = MutableStateFlow(
        PairUiState(paired = QuiverPrefs.isPaired(app.applicationContext), serial = QuiverPrefs.getSerial(app.applicationContext)),
    )
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    private var bikes: List<Bikes.Bike> = emptyList()
    private var devices: List<SavedDevices.SavedDevice> = emptyList()
    private var profile: UserProfile? = null

    init {
        karoo.connect { connected ->
            _state.update { it.copy(connected = connected, serial = karoo.serial ?: it.serial) }
        }
        karoo.addConsumer { e: Bikes -> bikes = e.bikes }
        karoo.addConsumer { e: SavedDevices -> devices = e.devices }
        karoo.addConsumer { e: UserProfile -> profile = e }
    }

    private fun deviceName(): String = karoo.serial?.let { "Karoo $it" } ?: "Karoo"

    fun pair(rawCode: String) {
        val code = rawCode.trim()
        if (code.isEmpty()) {
            _state.update { it.copy(message = "Enter the code from quiver.fyi.") }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val res = QuiverApi.claimPairing(karoo, code, deviceName())) {
                is QuiverApi.ClaimResult.Success -> {
                    QuiverPrefs.setToken(getApplication(), res.token, karoo.serial)
                    _state.update {
                        it.copy(busy = false, paired = true, serial = karoo.serial ?: it.serial, message = "Paired. Syncing your garage…")
                    }
                    seedGarage()
                }
                is QuiverApi.ClaimResult.Failure ->
                    _state.update { it.copy(busy = false, message = res.message) }
            }
        }
    }

    fun syncNow() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = "Syncing your garage…") }
        viewModelScope.launch { seedGarage() }
    }

    private suspend fun seedGarage() {
        val token = QuiverPrefs.getToken(getApplication())
        if (token == null) {
            _state.update { it.copy(busy = false, message = "Not paired.") }
            return
        }
        val payload = SeedMapper.build(bikes, devices, profile)
        val ok = QuiverApi.seed(karoo, token, payload)
        _state.update {
            it.copy(busy = false, message = if (ok) "Garage synced to Quiver." else "Couldn't reach Quiver — check the Karoo's connection.")
        }
    }

    fun unpair() {
        QuiverPrefs.clear(getApplication())
        _state.update { it.copy(paired = false, message = "Head unit disconnected.") }
    }

    override fun onCleared() {
        runCatching { karoo.disconnect() }
        super.onCleared()
    }
}
