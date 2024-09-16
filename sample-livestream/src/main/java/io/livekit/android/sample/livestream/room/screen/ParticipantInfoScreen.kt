/*
 * Copyright 2023-2024 LiveKit, Inc.
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

package io.livekit.android.sample.livestream.room.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.livekit.android.compose.local.RoomLocal
import io.livekit.android.compose.state.rememberParticipantInfo
import io.livekit.android.sample.livestream.model.AppModel
import io.livekit.android.sample.livestream.model.RoomNav
import io.livekit.android.sample.livestream.room.state.rememberParticipantMetadata
import io.livekit.android.sample.livestream.room.state.rememberRoomMetadata
import io.livekit.android.sample.livestream.room.ui.AvatarIcon
import io.livekit.android.sample.livestream.ui.control.HorizontalLine
import io.livekit.android.sample.livestream.ui.control.LargeTextButton
import io.livekit.android.sample.livestream.ui.control.Spacer
import io.livekit.android.sample.livestream.ui.theme.Dimens
import org.koin.compose.koinInject

/**
 * A BottomSheet screen that can show information on a participant as well as moderation controls if [isHost].
 */
@Composable
fun ParticipantInfoScreen(
    appModel: AppModel = koinInject(),
    roomNav: RoomNav = koinInject(),
    participantSid: String?
) {
    val appState by appModel.state.collectAsStateWithLifecycle()
    // TODO Find better way to handle these state null edge cases?
    val (isHost, sid) = appState?.run {
        participantSid?.let { isHost to it }
    } ?: run {
        Text("No participant selected")
        return
    }

    val room = RoomLocal.current
    val participant = remember(room, sid) {
        room.getParticipantBySid(sid)
    }

    if (participant == null) {
        roomNav.roomNavigateUp()
        return
    }

    val roomMetadata = rememberRoomMetadata()
    val participantInfo = rememberParticipantInfo(participant)
    val participantMetadata = rememberParticipantMetadata(participant)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacer)
    ) {
        Text(
            text = "Moderation",
            fontWeight = FontWeight.W700,
            fontSize = 20.sp,
        )

        Spacer(Dimens.spacer)
        HorizontalLine()
        Spacer(Dimens.spacer)

        AvatarIcon(
            imageUrl = participantMetadata.avatarImageUrlWithFallback(participant.identity?.value ?: ""),
            name = participant.identity?.value ?: "",
            modifier = Modifier
                .size(108.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(8.dp)

        Text(
            text = participantInfo.identity?.value ?: "",
            fontWeight = FontWeight.W700,
            fontSize = 14.sp,
        )

        Spacer(Dimens.spacer)
        HorizontalLine()
        Spacer(Dimens.spacer)

        val identity = participant.identity
        if (isHost &&
            identity != roomMetadata.creatorIdentity &&
            identity != null
        ) {
            if (participantMetadata.isOnStage) {
                LargeTextButton(
                    text = "Remove from stage",
                    onClick = { appModel.removeFromStage(identity) }
                )
            } else {
                LargeTextButton(
                    text = "Invite to stage",
                    enabled = !participantMetadata.invitedToStage,
                    onClick = { appModel.inviteToStage(identity) }
                )
            }

            Spacer(8.dp)

            LargeTextButton(
                text = "Remove from stream",
                onClick = {
                    TODO("No API available")
                }
            )
        }
    }
}
