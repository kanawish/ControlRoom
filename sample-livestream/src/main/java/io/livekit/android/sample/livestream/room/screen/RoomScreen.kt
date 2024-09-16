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

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.livekit.android.compose.chat.rememberChat
import io.livekit.android.compose.local.HandleRoomState
import io.livekit.android.compose.local.RoomLocal
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberParticipants
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.flipped
import io.livekit.android.room.RoomException
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.sample.livestream.model.AppModel
import io.livekit.android.sample.livestream.model.InvitedToStage
import io.livekit.android.sample.livestream.model.Join
import io.livekit.android.sample.livestream.model.MainNav
import io.livekit.android.sample.livestream.model.ParticipantList
import io.livekit.android.sample.livestream.R
import io.livekit.android.sample.livestream.model.RoomNav
import io.livekit.android.sample.livestream.model.RoomNavHost
import io.livekit.android.sample.livestream.model.Start
import io.livekit.android.sample.livestream.model.StreamOptions
import io.livekit.android.sample.livestream.room.data.DefaultLKOverrides
import io.livekit.android.sample.livestream.room.data.DefaultRoomOptions
import io.livekit.android.sample.livestream.room.state.rememberEnableCamera
import io.livekit.android.sample.livestream.room.state.rememberEnableMic
import io.livekit.android.sample.livestream.room.state.rememberHostParticipant
import io.livekit.android.sample.livestream.room.state.rememberOnStageParticipants
import io.livekit.android.sample.livestream.room.state.rememberParticipantMetadata
import io.livekit.android.sample.livestream.room.state.rememberParticipantMetadatas
import io.livekit.android.sample.livestream.room.state.rememberRoomMetadata
import io.livekit.android.sample.livestream.room.state.requirePermissions
import io.livekit.android.sample.livestream.room.ui.ChatBar
import io.livekit.android.sample.livestream.room.ui.ChatLog
import io.livekit.android.sample.livestream.room.ui.ChatWidgetMessage
import io.livekit.android.sample.livestream.room.ui.ConfettiState
import io.livekit.android.sample.livestream.room.ui.ParticipantGrid
import io.livekit.android.sample.livestream.room.ui.RoomConfettiView
import io.livekit.android.sample.livestream.room.ui.RoomControls
import io.livekit.android.sample.livestream.ui.control.LoadingDialog
import io.livekit.android.sample.livestream.util.KeepScreenOn
import io.livekit.android.util.flow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber

/**
 * A container for [RoomScreen] that sets up the needed nav host and dependencies.
 *
 * TODO: Refacto complete, remove this comment when all screens are done.
 */
@Composable
fun RoomScreenContainer(
    appModel: AppModel = koinInject(),
    mainNav: MainNav = koinInject(),
    roomNav: RoomNav = koinInject(),
) {
    // Prevent device from sleeping during stream
    KeepScreenOn()

    val appState by appModel.state.collectAsStateWithLifecycle()

    /*
        TODO Find better way to handle these state null edge cases?
         - If this happen, it's certainly a dev error.
         - Might need a 'safety net' reset button?
         - Crashing is also, in this case, a decent option, as it's a dev error.
    */
    val (_,connectionDetails,isHost,initialCamPos) = appState ?: run {
        Text(stringResource(R.string.appstate_unavailable))
        return
    }

    var enableAudio by remember { mutableStateOf(isHost) }
    var enableVideo by remember { mutableStateOf(isHost) }

    requirePermissions(enableAudio || enableVideo)

    // View state.
    val cameraPosition = remember { mutableStateOf(initialCamPos) }
    val showOptionsDialogOnce = remember { mutableStateOf(false) }

    val context = LocalContext.current
    RoomScope(
        url = connectionDetails.wsUrl,
        token = connectionDetails.token,
        audio = rememberEnableMic(enableAudio),
        video = rememberEnableCamera(enableVideo),
        roomOptions = DefaultRoomOptions { options ->
            options.copy(
                videoTrackCaptureDefaults = LocalVideoTrackOptions(
                    position = initialCamPos
                )
            )
        },
        liveKitOverrides = DefaultLKOverrides(context),
        onConnected = {
            // Show options dialog on connection to display livestream code
            Timber.d("RoomScreenContainer -> onConnected")
            if (isHost) {
                Timber.d("RoomScreenContainer -> showOptionsDialogOnce.value = true")
                // TODO: Not sure what the idea was here. Commented it out, for now.
                // showOptionsDialogOnce.value = true
            }
        },
        onDisconnected = {
            Toast.makeText(context, "Disconnected from livestream.", Toast.LENGTH_LONG).show()
            mainNav.mainPopBackstack(if (isHost) Start else Join,false)
        },
        onError = { _, error ->
            if (error is RoomException.ConnectException) {
                Toast.makeText(
                    context,
                    "Error while joining the stream. Please check the code and try again.",
                    Toast.LENGTH_LONG
                ).show()
                mainNav.mainPopBackstack(if (isHost) Start else Join,false)
            }
        }
    ) { room ->

        // Handle camera position changes
        LaunchedEffect(cameraPosition.value) {
            val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)
                ?.track as? LocalVideoTrack
                ?: return@LaunchedEffect

            if (track.options.position != cameraPosition.value) {
                track.restartTrack(LocalVideoTrackOptions(position = cameraPosition.value))
            }
        }

        // Publish video if have permissions as viewer.
        if (!isHost) {
            LaunchedEffect(room) {
                room.localParticipant::permissions.flow.collect { permissions ->
                    val canPublish = permissions?.canPublish ?: false
                    enableAudio = canPublish
                    enableVideo = canPublish
                }
            }
        }

        RoomNavHost(cameraPosition, showOptionsDialogOnce, roomNav)
    }
}

/**
 * The room screen, for both hosts and participants to view the stream.
 */
@Composable
fun RoomScreen(
    appModel: AppModel = koinInject(),
    roomNav: RoomNav = koinInject(),
    cameraPosition: MutableState<CameraPosition>,
    showOptionsDialogOnce: MutableState<Boolean>,
) {
    val appState by appModel.state.collectAsStateWithLifecycle()
    val isHost = appState?.run { isHost } ?: run {
        Text(stringResource(R.string.appstate_unavailable))
        return
    }

    val roomMetadata = rememberRoomMetadata()
    val chat = rememberChat()
    val scope = rememberCoroutineScope()

    if (showOptionsDialogOnce.value) {
        Timber.d("RoomScreen -> showOptionsDialogOnce")
        showOptionsDialogOnce.value = false
        roomNav.roomNavigate(StreamOptions)
    }

    HandleInvitedToStage()

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val (chatLog, chatBar, hostScreen, viewerButton) = createRefs()

        val tracks = rememberTracks(usePlaceholders = setOf(Track.Source.CAMERA))
        val hostParticipant = rememberHostParticipant(roomMetadata.creatorIdentity)
        val hostTrack = tracks.firstOrNull { track -> track.participant == hostParticipant }

        // Get all the tracks for all the other participants.
        val stageParticipants = rememberOnStageParticipants(roomMetadata.creatorIdentity)
        val stageTracks = stageParticipants.map { p ->
            tracks.firstOrNull { track -> track.participant == p }
        }

        // Prioritize the host to the top.
        val videoTracks = listOf(hostTrack).plus(stageTracks)

        val metadatas = rememberParticipantMetadatas()

        // Keep track of whether any viewers want to come to the stage.
        val hasRaisedHands = if (isHost) {
            remember(metadatas) {
                metadatas.any { (_, metadata) ->
                    metadata.handRaised && !metadata.isOnStage
                }
            }
        } else {
            // Don't show for viewers.
            false
        }

        // Display video tracks as needed.
        ParticipantGrid(
            videoTracks = videoTracks,
            isHost = isHost,
            modifier = Modifier
                .constrainAs(hostScreen) {
                    width = Dimension.matchParent
                    height = Dimension.fillToConstraints
                    top.linkTo(parent.top)
                    bottom.linkTo(chatBar.top)
                }
        )

        // Handle reactions
        val confettiState = remember { ConfettiState() }
        RoomConfettiView(room = RoomLocal.current, chatState = chat, confettiState = confettiState)

        // Chat overlay
        ChatLog(
            messages = chat.messages.value.mapNotNull {
                val participantMetadata = metadatas[it.participant] ?: return@mapNotNull null
                ChatWidgetMessage(
                    it.participant?.identity?.value ?: "",
                    it.message,
                    participantMetadata.avatarImageUrlWithFallback(it.participant?.identity?.value ?: ""),
                    it.timestamp,
                )
            },
            modifier = Modifier
                .constrainAs(chatLog) {
                    width = Dimension.fillToConstraints
                    height = Dimension.percent(0.5f)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(chatBar.top)
                }
        )

        ChatBar(
            onChatSend = { message ->
                scope.launch { chat.send(message) }
            },
            onOptionsClick = { roomNav.roomNavigate(StreamOptions) },
            chatEnabled = roomMetadata.enableChat,
            modifier = Modifier
                .constrainAs(chatBar) {
                    width = Dimension.fillToConstraints
                    height = Dimension.wrapContent
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                }
        )

        RoomControls(
            showFlipButton = isHost,
            participantCount = rememberParticipants().size,
            showParticipantIndicator = hasRaisedHands,
            onFlipButtonClick = { cameraPosition.value = cameraPosition.value.flipped() },
            onParticipantButtonClick = { roomNav.roomNavigate(ParticipantList) },
            modifier = Modifier.constrainAs(viewerButton) {
                width = Dimension.fillToConstraints
                height = Dimension.wrapContent
                start.linkTo(parent.start, margin = 8.dp)
                end.linkTo(parent.end, margin = 8.dp)

                // Room controls have internal padding, so no margin here.
                top.linkTo(parent.top)
            },
        )
    }

    // Loading dialog while connecting.
    var isConnected by remember {
        mutableStateOf(false)
    }
    HandleRoomState { _, state ->
        isConnected = state == io.livekit.android.room.Room.State.CONNECTED
    }
    LoadingDialog(isShowingDialog = !isConnected)
}

/**
 * A handler to pop a dialog when we've been invited to the stage.
 */
@Composable
fun HandleInvitedToStage(
    roomNav: RoomNav = koinInject(),
) {
    val room = RoomLocal.current
    val localParticipantMetadata = rememberParticipantMetadata(participant = room.localParticipant)
    var showInvitedDialogOnce by remember { mutableStateOf(true) }

    if (!localParticipantMetadata.invitedToStage) {
        showInvitedDialogOnce = true
    }
    if (showInvitedDialogOnce && localParticipantMetadata.invitedToStage) {
        showInvitedDialogOnce = false
        roomNav.roomNavigate(InvitedToStage)
    }
}
