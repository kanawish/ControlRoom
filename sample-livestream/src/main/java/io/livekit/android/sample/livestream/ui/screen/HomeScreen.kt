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

package io.livekit.android.sample.livestream.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import io.livekit.android.sample.livestream.model.JoinRoute
import io.livekit.android.sample.livestream.model.MainNav
import io.livekit.android.sample.livestream.R
import io.livekit.android.sample.livestream.model.JoystickViewModel
import io.livekit.android.sample.livestream.model.StartRoute
import io.livekit.android.sample.livestream.ui.control.LargeTextButton
import io.livekit.android.sample.livestream.ui.theme.Dimens
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

/**
 * The start screen shown upon launch of the app.
 */
@Composable
fun HomeScreen(
    mainNav: MainNav = koinInject(),
    joystickViewModel: JoystickViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        joystickViewModel.name = "🎼"
        joystickViewModel.events.collectLatest { event ->
            Timber.d("😬 Joystick event: X=${event.xAxis}, Y=${event.yAxis}, Z=${event.zAxis}, RZ=${event.rzAxis}")
        }
    }

    joystickViewModel.events

    // Test
    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val (content, startButton, joinButton) = createRefs()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(43.dp)
                .constrainAs(content) {
                    width = Dimension.fillToConstraints
                    height = Dimension.wrapContent
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(startButton.top)
                }
        ) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            Image(
//                painter = painterResource(id = R.drawable.livekit_icon),
                painter = painterResource(id = R.drawable.kanastruk_logo_white),
                contentDescription = "",
                modifier = if (isLandscape) Modifier.size(100.dp) else Modifier.size(200.dp)
            )

            if (isLandscape) Spacer(modifier = Modifier.height(8.dp))
            else Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Welcome!",
                fontSize = 34.sp,
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Welcome to the Kanastruk Control Room demo app, based on LiveKit's open source live streaming demo app.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val startButtonColors = ButtonDefaults.buttonColors(
            contentColor = Color.White,
            containerColor = Color(0xFF937F64)
        )
        LargeTextButton(
            text = "Start a livestream",
            colors = startButtonColors,
            onClick = { mainNav.mainNavigate(StartRoute) },
            modifier = Modifier.constrainAs(startButton) {
                width = Dimension.fillToConstraints
                height = Dimension.value(Dimens.buttonHeight)
                bottom.linkTo(joinButton.top, margin = Dimens.spacer)
                start.linkTo(parent.start, margin = Dimens.spacer)
                end.linkTo(parent.end, margin = Dimens.spacer)
            }
        )

        val joinButtonColors = ButtonDefaults.buttonColors(
            contentColor = Color.White,
            containerColor = Color(0xFF131313)
        )
        LargeTextButton(
            text = "Join a livestream",
            colors = joinButtonColors,
            onClick = { mainNav.mainNavigate(JoinRoute) },
            modifier = Modifier.constrainAs(joinButton) {
                width = Dimension.fillToConstraints
                height = Dimension.value(Dimens.buttonHeight)
                bottom.linkTo(parent.bottom, margin = Dimens.spacer)
                start.linkTo(parent.start, margin = Dimens.spacer)
                end.linkTo(parent.end, margin = Dimens.spacer)
            }
        )
    }
}
