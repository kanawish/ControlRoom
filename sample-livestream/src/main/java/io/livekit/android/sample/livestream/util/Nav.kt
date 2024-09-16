package io.livekit.android.sample.livestream.util

import androidx.navigation.NavHostController

/** When type routes are used, remember the implementation needs to be @Serializable. */
interface TypedRoute

/**
 * Needed to support current version of NavGraphBuilder.bottomSheet
 * builder that only accepting strings as routes.
 */
interface NamedRoute {
    val name: String get() = this::class.simpleName!!
}

/**
 * These will be consumed by the NavHostController.
 */
data class NavEvent(val block: NavHostController.() -> Unit)

