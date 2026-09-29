package com.scrollstop.data.repository

import com.scrollstop.domain.model.EnforcementTrigger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository interface for emitting and observing internal [EnforcementTrigger] events.
 */
interface EnforcementTriggerRepository {
    val triggerFlow: SharedFlow<EnforcementTrigger>
    val latestTrigger: StateFlow<EnforcementTrigger?>
    fun emitTrigger(trigger: EnforcementTrigger)
    fun clearLatestTrigger()
}

/**
 * In-memory implementation of [EnforcementTriggerRepository].
 */
class InMemoryEnforcementTriggerRepository : EnforcementTriggerRepository {

    private val _triggerFlow = MutableSharedFlow<EnforcementTrigger>(replay = 0)
    override val triggerFlow: SharedFlow<EnforcementTrigger> = _triggerFlow.asSharedFlow()

    private val _latestTrigger = MutableStateFlow<EnforcementTrigger?>(null)
    override val latestTrigger: StateFlow<EnforcementTrigger?> = _latestTrigger.asStateFlow()

    override fun emitTrigger(trigger: EnforcementTrigger) {
        _latestTrigger.value = trigger
        _triggerFlow.tryEmit(trigger)
    }

    override fun clearLatestTrigger() {
        _latestTrigger.value = null
    }
}
