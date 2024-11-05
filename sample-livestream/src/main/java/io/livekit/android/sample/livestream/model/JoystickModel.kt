package io.livekit.android.sample.livestream.model

import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class JoystickEvent(
    val xAxis: Float,
    val yAxis: Float,
    val zAxis: Float,
    val rzAxis: Float
)
fun JoystickEvent.toJson(): String {
    return Json.encodeToString(this)
}

fun String.toJoystickEvent(): JoystickEvent {
    return Json.decodeFromString(this)
}

fun MotionEvent.toJoystickEvent(): JoystickEvent {
    return JoystickEvent(
        xAxis = this.getAxisValue(MotionEvent.AXIS_X),
        yAxis = this.getAxisValue(MotionEvent.AXIS_Y),
        zAxis = this.getAxisValue(MotionEvent.AXIS_Z),
        rzAxis = this.getAxisValue(MotionEvent.AXIS_RZ)
    )
}

class JoystickModel {
    private val _events = MutableSharedFlow<JoystickEvent>()
    val events = _events.asSharedFlow()

    /**
     * Will safely convert to Joystick event before publishing.
     */
    suspend fun publishAsJoystickEvent(event: MotionEvent) {
        _events.emit(event.toJoystickEvent())
    }
}