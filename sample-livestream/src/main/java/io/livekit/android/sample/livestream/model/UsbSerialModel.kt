package io.livekit.android.sample.livestream.model

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.hoho.android.usbserial.util.SerialInputOutputManager.Listener
import io.livekit.android.sample.livestream.model.UsbSerialModel.State.Open
import io.livekit.android.sample.livestream.util.Sampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.lang.Exception
import java.util.Scanner

// NOTE: for USB info, see https://developer.android.com/guide/topics/connectivity/usb/host
// NOTE: val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

// FIXME: We got lazy here re: permissions, would be nice to
//  ask for perms from inside the app as well.
@OptIn(ExperimentalUnsignedTypes::class)
class UsbSerialModel(
    private val manager: UsbManager,
    scope: CoroutineScope
) : CoroutineScope by scope {
    sealed class State {
        data object Init:State()

        data class Closed(private val openHandler:()->Unit) : State() {
            fun open():Unit = openHandler()
        }

        data class Open(
            val port:UsbSerialPort,
            val ioManager:SerialInputOutputManager,
            private val controlHandler:Open.(byteArray:UByteArray)->Unit,
            private val closeHandler:Open.()->Unit
        ):State() {
            fun control(byteArray:UByteArray) = controlHandler(byteArray)
            fun close() = closeHandler()
        }

        data class Error(
            val msg: String,
            private val retryHandler: () -> Unit
        ) : State() {
            fun retry() = retryHandler()
        }
    }

    private val _state = MutableStateFlow<State>(State.Init)
    val state = _state.asStateFlow()

    init {
        openHandler()
    }

    private fun closedState(): State.Closed = State.Closed(::openHandler)

    /**
     * Attempts to open the first driver/device found, updates state
     * accordingly.
     *
     * NOTE: Assumes is a single device is connected!
     */
    private fun openHandler() {
        val newState = UsbSerialProber.getDefaultProber()
            .findAllDrivers(manager)
            .firstOrNull()
            ?.let { driver ->
                val connection = manager.openDevice(driver.device)
                driver.ports.firstOrNull()?.let { port ->
                    port.open(connection)
                    port.setParameters(
                        115200,
                        8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                    )
                    openState(port, constructIoManager(port))
                } ?: State.Error("Port not found", ::openHandler)
            } ?: State.Error("Driver not found", ::openHandler)

        Timber.d("Open handler result $newState")
        _state.update { newState }
    }

    private fun openState(
        newPort:UsbSerialPort,
        newIoManager:SerialInputOutputManager
    ): Open {
        return Open(
            port = newPort,
            ioManager = newIoManager,
            controlHandler = getControlHandler(),
            closeHandler = getCloseHandler()
        )
    }

    private val controlSampler = Sampler(60)

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getControlHandler(): Open.(byteArray: UByteArray) -> Unit = { byteArray ->
        byteArray.toUByteArray().let { command ->
            controlSampler.sample {
                val formatted = command.joinToString(",", "[", "]")
                Timber.d("[frame:$count] $formatted")
            }
            port.write(command.toByteArray(), 0)
        }
    }

    private fun getCloseHandler(): Open.() -> Unit = {
        ioManager.listener = null
        ioManager.stop()
        port.close()

        _state.update { closedState() }
    }

    // ---------------

    private fun constructIoManager(port: UsbSerialPort):SerialInputOutputManager {
        // NOTE: Review this, I'm not 100% convinced the buffer
        //  approach is correct.
        val listener = object : Listener {
            private var stringBuffer = StringBuffer()

            override fun onNewData(data: ByteArray?) {
                data?.let {
                    stringBuffer.append(String(data))
                    Scanner(stringBuffer.toString()).let { s ->
                        if (s.hasNextLine()) {
                            val line = s.nextLine()
                            Timber.d("s:$line")
                            // This dumps and trims.
                            stringBuffer.removePrefix(line)
                        }
                    }
                } ?: Timber.d("serial: null?")
            }

            override fun onRunError(e: Exception?) {
                Timber.d(e, "serial error?")
            }
        }
        return SerialInputOutputManager(port, listener)
    }

}