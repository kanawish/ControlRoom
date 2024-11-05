package io.livekit.android.sample.livestream.model

import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import timber.log.Timber

// NOTE: https://insert-koin.io/docs/quickstart/android-compose#injecting-viewmodel-in-compose
class JoystickViewModel(
    private val joystickModel: JoystickModel,
): ViewModel() {
    @Deprecated("`name` will be deleted, just a lifecycle debugging affordance.")
    var name = "??"

    init {
        Timber.d("🍫 JoystickViewModel ($name) init")
/*
        viewModelScope.launch {
            joystickModel.events.collectLatest { (xAxis, yAxis, zAxis, rzAxis) ->
                Timber.d("🍫($name) Joystick event: X=$xAxis, Y=$yAxis, Z=$zAxis, RZ=$rzAxis")
            }
        }
*/
    }

    val events = joystickModel.events

    fun publish(event: MotionEvent) = viewModelScope.launch {
        joystickModel.publishAsJoystickEvent(event)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("🍫($name) JoystickViewModel ($name) cleared")
    }
}