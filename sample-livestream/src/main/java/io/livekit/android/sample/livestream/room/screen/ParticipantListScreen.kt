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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.livekit.android.sample.livestream.model.AppModel
import io.livekit.android.sample.livestream.R
import io.livekit.android.sample.livestream.model.RoomNav
import io.livekit.android.sample.livestream.room.state.rememberHostParticipant
import io.livekit.android.sample.livestream.room.state.rememberParticipantMetadatas
import io.livekit.android.sample.livestream.room.state.rememberRoomMetadata
import io.livekit.android.sample.livestream.room.ui.AvatarIcon
import io.livekit.android.sample.livestream.ui.control.HorizontalLine
import io.livekit.android.sample.livestream.ui.control.SmallTextButton
import io.livekit.android.sample.livestream.ui.control.Spacer
import io.livekit.android.sample.livestream.ui.theme.AppTheme
import io.livekit.android.sample.livestream.ui.theme.Dimens
import io.livekit.android.sample.livestream.ui.theme.LKButtonColors
import io.livekit.android.sample.livestream.ui.theme.LKTextStyle
import org.koin.compose.koinInject

private const val headerRequestKey = "header_request_key"
private const val headerHostKey = "header_host_key"
private const val headerViewerKey = "header_viewer_key"

/**
 * A BottomSheet screen that shows all the participants in the room.
 */
@Composable
fun ParticipantListScreen(
    appModel: AppModel = koinInject(),
    roomNav: RoomNav = koinInject()
) {
    val appState by appModel.state.collectAsStateWithLifecycle()
    val isHost = appState?.isHost ?: run {
        Text(stringResource(R.string.appstate_unavailable))
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacer),
    ) {
        Text(
            text = "Viewers",
            style = LKTextStyle.header,
        )

        Spacer(Dimens.spacer)
        HorizontalLine()
        Spacer(Dimens.spacer)

        val roomMetadata = rememberRoomMetadata()
        val metadatas = rememberParticipantMetadatas()
        val hostParticipant = rememberHostParticipant(roomMetadata.creatorIdentity)

        val hosts = metadatas
            .filter { (participant, metadata) -> metadata.isOnStage || participant == hostParticipant }
            .entries
            .toList()
            .sortedBy { it.key.identity?.value ?: "" }

        // Only visible to the host.
        val requestsToJoin = if (isHost) {
            metadatas
                .filter { (participant, metadata) -> metadata.handRaised && !metadata.invitedToStage && !hosts.any { it.key == participant } }
                .entries
                .toList()
                .sortedBy { it.key.identity?.value ?: "" }
        } else {
            emptyList()
        }

        val viewers = metadatas
            .filter { p -> !requestsToJoin.any { it == p } }
            .filter { p -> !hosts.any { it == p } }
            .entries
            .toList()
            .sortedBy { it.key.identity?.value ?: "" }

        LazyColumn {
            if (requestsToJoin.isNotEmpty()) {
                item(headerRequestKey) {
                    Text(
                        text = "Requests to join".uppercase(),
                        style = LKTextStyle.listSectionHeader,
                        modifier = Modifier.animateItem(),
                    )
                    Spacer(size = Dimens.spacer)
                }

                // TODO: Fix this sid issue.
                items(
                    items = requestsToJoin,
                    key = { it.key.sid.value }
                ) { (participant, metadata) ->
                    ParticipantRow(
                        name = participant.identity?.value ?: "",
                        imageUrl = metadata.avatarImageUrlWithFallback(participant.identity?.value ?: ""),
                        isRequestingToJoin = true,
                        onAllowClick = {
                            val identity = participant.identity ?: return@ParticipantRow
                            appModel.inviteToStage(identity)
                        },
                        onDenyClick = {
                            val identity = participant.identity ?: return@ParticipantRow
                            appModel.removeFromStage(identity)
                        },
                        modifier = Modifier
                            .clickable { roomNav.roomNavigateParticipantInfo(participant.sid) }
                            .animateItem()
                    )
                }
            }

            if (hosts.isNotEmpty()) {
                item(headerHostKey) {
                    Text(
                        text = "Hosts".uppercase(),
                        style = LKTextStyle.listSectionHeader,
                        modifier = Modifier.animateItem(),
                    )
                    Spacer(size = Dimens.spacer)
                }

                items(
                    items = hosts,
                    key = { it.key.sid.value }
                ) { (participant, metadata) ->
                    ParticipantRow(
                        name = participant.identity?.value ?: "",
                        imageUrl = metadata.avatarImageUrlWithFallback(participant.identity?.value ?: ""),
                        modifier = Modifier
                            .clickable { roomNav.roomNavigateParticipantInfo(participant.sid) }
                            .animateItem()
                    )
                }
            }

            if (viewers.isNotEmpty()) {
                item(headerViewerKey) {
                    Text(
                        text = "Viewers".uppercase(),
                        style = LKTextStyle.listSectionHeader,
                        modifier = Modifier.animateItem(),
                    )
                    Spacer(size = Dimens.spacer)
                }

                items(
                    items = viewers,
                    key = { it.key.sid.value }
                ) { (participant, metadata) ->
                    ParticipantRow(
                        name = participant.identity?.value ?: "",
                        imageUrl = metadata.avatarImageUrlWithFallback(participant.identity?.value ?: ""),
                        modifier = Modifier
                            .clickable { roomNav.roomNavigateParticipantInfo(participant.sid) }
                            .animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.ParticipantRow(
    name: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    isRequestingToJoin: Boolean = false,
    onAllowClick: () -> Unit = {},
    onDenyClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
    ) {
        // Profile icon
        AvatarIcon(
            name = name,
            imageUrl = imageUrl,
            modifier = Modifier.size(32.dp)
        )
        Spacer(size = 12.dp)
        Text(name, modifier = Modifier.weight(1f))

        if (isRequestingToJoin) {

            SmallTextButton(
                text = "Allow",
                onClick = { onAllowClick() },
                colors = LKButtonColors.blueButtonColors(),
                modifier = Modifier.defaultMinSize(60.dp, 30.dp)
            )
            Spacer(8.dp)
            SmallTextButton(
                text = "Deny",
                onClick = { onDenyClick() },
                colors = LKButtonColors.secondaryButtonColors(),
                modifier = Modifier.defaultMinSize(60.dp, 30.dp)
            )
        }
    }
    Spacer(size = Dimens.spacer)
}

// Generate a color based on the name.
fun nameToColor(name: String?): Color {
    if (name.isNullOrEmpty()) {
        return Color.White
    }
    return Color(name.hashCode().toLong() or 0xFF000000)
}

@Preview(showBackground = true)
@Composable
fun ParticipantRowPreview() {
    AppTheme {
        LazyColumn {
            item {
                ParticipantRow(name = "Viewer", imageUrl = "")
            }
            item {
                ParticipantRow(name = "Viewer requesting to Join", imageUrl = "", isRequestingToJoin = true)
            }
        }
    }
}
