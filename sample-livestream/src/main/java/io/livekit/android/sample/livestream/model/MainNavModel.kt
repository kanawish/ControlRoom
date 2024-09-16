package io.livekit.android.sample.livestream.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.ajalt.timberkt.Timber
import io.livekit.android.sample.livestream.room.screen.RoomScreenContainer
import io.livekit.android.sample.livestream.ui.screen.HomeScreen
import io.livekit.android.sample.livestream.ui.screen.JoinScreen
import io.livekit.android.sample.livestream.ui.screen.StartScreen
import io.livekit.android.sample.livestream.util.NavEvent
import io.livekit.android.sample.livestream.util.TypedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/**
@startuml
hide empty members
!define RECTANGLE class

RECTANGLE Home
RECTANGLE Join
RECTANGLE Start
RECTANGLE RoomContainer
RECTANGLE InvitedToStage
RECTANGLE ParticipantInfo
RECTANGLE ParticipantList
RECTANGLE StreamOptions

Home -down-> Join
Home -down-> Start
Home -down-> RoomContainer

RoomContainer -down-> InvitedToStage
RoomContainer -down-> ParticipantInfo
RoomContainer -down-> ParticipantList
RoomContainer -down-> StreamOptions

@enduml

 NOTE: Combining BottomSheets with the newer compose navigation was a bit tricky.
  Mostly, I had a hard time finding docs on the official Google side to
  clearly cover what the 'old way' was. Here are some helpful articles:
   - [Official docs] https://developer.android.com/guide/navigation/design
   - [Part 3] https://proandroiddev.com/passing-multi-typed-data-between-screens-with-jetpack-compose-navigation-component-39ccbcf901ff
   - [Part 2] ]https://proandroiddev.com/passing-string-typed-data-with-jetpack-compose-navigation-component-fd4759acd906

 */

interface MainNav {
    val mainNavEvents: SharedFlow<NavEvent>
    fun mainNavigate(route: TypedRoute)
    fun mainNavigateUp()
    fun mainPopBackstack(route: TypedRoute, inclusive: Boolean)
    fun mainNavEvent(block: NavHostController.() -> Unit)
}

class MainNavModel(scope: CoroutineScope) : MainNav, CoroutineScope by scope {
    private val _mainNavEvents = MutableSharedFlow<NavEvent>()
    override val mainNavEvents = _mainNavEvents.asSharedFlow()

    override fun mainNavigate(route: TypedRoute) {
        mainNavEvent {
            Timber.d { "main: navigate(${route::class.simpleName})" }
            navigate(route)
        }
    }
    override fun mainNavigateUp()  {
        mainNavEvent {
            Timber.d { "mainNavigateUp()" }
            navigateUp()
        }
    }
    override fun mainPopBackstack(route: TypedRoute, inclusive: Boolean) {
        mainNavEvent {
            Timber.d { "mainPopBackstack(${route::class.simpleName})" }
            popBackStack(route, inclusive)
        }
    }
    override fun mainNavEvent(block: NavHostController.() -> Unit) {
        launch {
            Timber.d { "roomNavEvent ->" }
            _mainNavEvents.emit(NavEvent(block))
        }
    }
}

// Application Routes
@Serializable data object Home: TypedRoute
@Serializable data object Join: TypedRoute
@Serializable data object Start: TypedRoute
@Serializable data object RoomContainer: TypedRoute

@Composable
fun MainNavHost(mainNav: MainNav = koinInject()) {
    val navHostController: NavHostController = rememberNavController()
    NavHost(
        navController = navHostController,
        startDestination = Home
    ) {
        composable<Home> { HomeScreen() }
        composable<Join> { JoinScreen() }
        composable<Start> { StartScreen() }
        composable<RoomContainer> { RoomScreenContainer() }
    }

    LaunchedEffect(Unit) {
        timber.log.Timber.d("MainActivity -> LaunchedEffect(Unit)")
        mainNav.mainNavEvents.collect { event ->
            timber.log.Timber.d("mainNavEvent $event")
            event.block.invoke(navHostController)
        }
    }
}
