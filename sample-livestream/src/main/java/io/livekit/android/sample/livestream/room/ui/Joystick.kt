package io.livekit.android.sample.livestream.room.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.livekit.android.compose.flow.DataSendOptions
import io.livekit.android.compose.flow.rememberDataMessageHandler
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.sample.livestream.model.JoystickEvent
import io.livekit.android.sample.livestream.model.JoystickViewModel
import io.livekit.android.sample.livestream.model.UsbSerialModel
import io.livekit.android.sample.livestream.model.toJoystickEvent
import io.livekit.android.sample.livestream.model.toJson
import io.livekit.android.sample.livestream.util.Sampler
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber
import kotlin.math.abs

object Direction {
    val FORWARD = 1.toUByte()
    val BACKWARD = 2.toUByte()
    val BRAKE = 3.toUByte()
    val RELEASE = 4.toUByte()
}
private val fooSampler = Sampler(60)

@OptIn(ExperimentalUnsignedTypes::class)
fun JoystickEvent.toCommandUByteArray(): UByteArray {
    val drive:(Float)->UByte = {
        if (abs(it) < 0.01) Direction.RELEASE
        else if (it > 0) Direction.BACKWARD else Direction.FORWARD
    }
    val value: (Float) -> UByte = { (abs(it) * 255.0).toInt().toUByte() }

    val (_,y1Axis,_,y2Axis) = this

    val command = ubyteArrayOf(
        drive(y1Axis), value(y1Axis),
        drive(y2Axis), value(y2Axis)
    )
    fooSampler.sample {
        val formatted = command.joinToString(",","[","]")
        Timber.d("[frame:$count] raw(L$y1Axis,R$y2Axis)->${formatted}")
    }
    return command
}

@Composable
fun ReceiveJoystick(
    room: Room,
    usbSerialModel: UsbSerialModel = koinInject()
) {
    val usbState:UsbSerialModel.State by usbSerialModel.state.collectAsState()

    Text("🦻")
    val handler = rememberDataMessageHandler(room, "joystick")
    LaunchedEffect(room, usbState) {
        handler.messageFlow.collect {
            val joystickEvent = it.payload.decodeToString().toJoystickEvent()
            joystickEvent.apply {
                Timber.d("🦻Joystick event: X=$xAxis, Y=$yAxis, Z=$zAxis, RZ=$rzAxis")

                when(val s = usbState) {
                    is UsbSerialModel.State.Open -> s.control(joystickEvent.toCommandUByteArray())
                    else -> {} // noop.
                }
            }
        }
    }
}

@Composable
fun SendJoystick(
    room: Room,
    viewModel: JoystickViewModel = koinViewModel()
) {
    Text("🗣️")
    val handler = rememberDataMessageHandler(room, "joystick")
    LaunchedEffect(room) {
        viewModel.events.collect { event ->
            event.apply {
                Timber.d("\uD83D\uDDE3\uFE0F Joystick event: X=$xAxis, Y=$yAxis, Z=$zAxis, RZ=$rzAxis")
            }
            handler.sendMessage(
                event.toJson().encodeToByteArray(),
                DataSendOptions(DataPublishReliability.LOSSY)
            )
        }
    }
}

