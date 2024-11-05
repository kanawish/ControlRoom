/*
 * Copyright 2023 LiveKit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.livekit.android.sample.livestream

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import io.livekit.android.sample.livestream.model.JoystickViewModel
import io.livekit.android.sample.livestream.model.MainNavHost
import io.livekit.android.sample.livestream.ui.theme.AppTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val joystickViewModel: JoystickViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        joystickViewModel.name = "🎩"
        // TODO: Make this 'drone/gamepad specific' somehow.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent {
            AppTheme {
                Surface {
                    MainNavHost()
                }
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE) {
            // Process joystick events
            val xAxis = event.getAxisValue(MotionEvent.AXIS_X)
            val yAxis = event.getAxisValue(MotionEvent.AXIS_Y)
            val zAxis = event.getAxisValue(MotionEvent.AXIS_Z)
            val rzAxis = event.getAxisValue(MotionEvent.AXIS_RZ)

            joystickViewModel.publish(event)

            // Handle the joystick input here
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
