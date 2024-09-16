package io.livekit.android.sample.livestream.model

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.navigation.BottomSheetNavigator
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.bottomSheet
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavArgument
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.plusAssign
import com.github.ajalt.timberkt.Timber
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.sample.livestream.room.screen.InvitedToStageScreen
import io.livekit.android.sample.livestream.room.screen.ParticipantInfoScreen
import io.livekit.android.sample.livestream.room.screen.ParticipantListScreen
import io.livekit.android.sample.livestream.room.screen.RoomScreen
import io.livekit.android.sample.livestream.room.screen.StreamOptionsScreen
import io.livekit.android.sample.livestream.util.NamedRoute
import io.livekit.android.sample.livestream.util.NavEvent
import io.livekit.android.sample.livestream.util.TypedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

interface RoomNav {
    val roomNavEvents: SharedFlow<NavEvent>
    fun roomNavigate(route: NamedRoute)
    fun roomNavigateUp()
    fun roomNavigateParticipantInfo(participantSid: Participant.Sid)
    fun roomNavEvent(block: NavHostController.() -> Unit)
}

class RoomNavModel(scope: CoroutineScope): RoomNav, CoroutineScope by scope {
    private val _roomNavEvents = MutableSharedFlow<NavEvent>()
    override val roomNavEvents = _roomNavEvents.asSharedFlow()

    override fun roomNavigate(route: NamedRoute) {
        roomNavEvent {
            Timber.d { "roomNavigate(${route::class.simpleName})" }
            navigate(route.name)
        }
    }
    override fun roomNavigateUp() {
        roomNavEvent {
            Timber.d { "roomNavigateUp()" }
            navigateUp()
        }
    }

    override fun roomNavigateParticipantInfo(participantSid: Participant.Sid) {
        roomNavEvent {
            Timber.d { "roomNavigateParticipantInfo($participantSid)" }
            // FIXME -
            roomNavEvent {
                navigate(ParticipantInfo.name+"/${participantSid.value}")
            }
        }
    }

    override fun roomNavEvent(block: NavHostController.() -> Unit) {
        launch {
            Timber.d { "roomNavEvent ->" }
            _roomNavEvents.emit(NavEvent(block))
        }
    }
}

@Serializable
data object Room: TypedRoute
data object InvitedToStage: NamedRoute
data object ParticipantInfo: NamedRoute
data object ParticipantList: NamedRoute
data object StreamOptions: NamedRoute

@Composable
fun RoomNavHost(
    cameraPosition: MutableState<CameraPosition>,
    showOptionsDialogOnce: MutableState<Boolean>,
    roomNav: RoomNav
) {
    val roomNavHostController: NavHostController = rememberNavController()
    val bottomSheetNavigator: BottomSheetNavigator = rememberBottomSheetNavigator()
    roomNavHostController.navigatorProvider += bottomSheetNavigator
    ModalBottomSheetLayout(
        bottomSheetNavigator = bottomSheetNavigator,
        sheetShape = RoundedCornerShape(16.dp),
        sheetBackgroundColor = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = roomNavHostController,
            startDestination = Room
        ) {
            composable<Room> {
                // Pass in 'view state' that belongs to container.
                RoomScreen(
                    cameraPosition = cameraPosition,
                    showOptionsDialogOnce = showOptionsDialogOnce
                )
            }
            bottomSheet(StreamOptions.name) { StreamOptionsScreen() }
            bottomSheet(ParticipantList.name) { ParticipantListScreen() }
            // FIXME -
            bottomSheet(ParticipantInfo.name + "/{sid}") {
                val sid = it.arguments?.getString("sid")
                ParticipantInfoScreen(participantSid = sid)
            }
            bottomSheet(InvitedToStage.name) { InvitedToStageScreen() }
        }
    }

    LaunchedEffect(Unit) {
        timber.log.Timber.d("RoomScreenContainer -> LaunchedEffect(Unit)")
        roomNav.roomNavEvents.collect { event ->
            timber.log.Timber.d("RoomNavEvent: $event")
            event.run { roomNavHostController.block() }
        }
    }
}