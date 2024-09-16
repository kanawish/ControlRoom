package io.livekit.android.sample.livestream.model

import com.github.ajalt.timberkt.Timber
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.sample.livestream.room.data.AuthenticatedLivestreamApi
import io.livekit.android.sample.livestream.room.data.ConnectionDetails
import io.livekit.android.sample.livestream.room.data.IdentityRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppModel(
    private val okHttpClient: okhttp3.OkHttpClient,
    private val retrofit: retrofit2.Retrofit,
    scope: CoroutineScope
) : CoroutineScope by scope {

    // TODO: Sealed class 'Connected' and 'Disconnected' states?.
    data class State(
        val authToken: String,
        val connectionDetails: ConnectionDetails,
        val isHost: Boolean,
        val initialCamPos: CameraPosition,
        private val authApiBuilder: (String) -> AuthenticatedLivestreamApi
    ) {
        val authedApi by lazy { authApiBuilder(authToken) }
    }

    private fun authApiBuilder(apiAuthToken:String): AuthenticatedLivestreamApi {
        val authedClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                var request = chain.request()
                request =
                    request.newBuilder().header("Authorization", "Token $apiAuthToken").build()

                return@addInterceptor chain.proceed(request)
            }
            .build()

        return retrofit.newBuilder()
            .client(authedClient)
            .build()
            .create(AuthenticatedLivestreamApi::class.java)
    }

    // Implicitly disconnected if state is null.
    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    init {
        Timber.d { "AppModel created" }
    }

    fun connected(
        authToken: String,
        connectionDetails: ConnectionDetails,
        isHost: Boolean = false,
        initialCamPos: CameraPosition = CameraPosition.FRONT
    ) {
        launch {
            Timber.d { "Connected with $authToken" }
            _state.emit(
                // NOTE: By creating a fresh new State, we get a new lazy authedApi as well.
                State(
                    authToken = authToken,
                    connectionDetails = connectionDetails,
                    isHost = isHost,
                    initialCamPos = initialCamPos,
                    authApiBuilder = ::authApiBuilder
                )
            )
        }
    }

    fun disconnected() {
        launch {
            Timber.d { "Disconnected" }
            _state.emit(null)
        }
    }

    // TODO: Validate functions below works as expected.
    // NOTE: Consider these might be better viewModel lifecycle backed, to stick closely
    //  to original implementation.

    fun stopStream() {
        launch {
            Timber.d { "Stop stream" }
            state.value?.authedApi?.run {
                stopStream()
                _state.emit(null)
            } ?: Timber.e { "No authedApi found to stop stream." }
        }
    }

    // NOTE: New convenience function to avoid call to IdentityRequest(id) from Compose.
    fun leaveStage(localParticipant: Participant) {
        launch {
            Timber.d { "Leave stage" }
            state.value?.authedApi?.run {
                val identity = localParticipant.identity
                if (identity != null) {
                    removeFromStage(IdentityRequest(identity))
                }
            } ?: Timber.e { "No authedApi found to leave stage." }
        }
    }

    fun requestToJoin() {
        launch {
            Timber.d { "Request to join" }
            state.value?.authedApi?.run {
                requestToJoin()
            } ?: Timber.e { "No authedApi found to request to join." }
        }
    }

    fun inviteToStage(identity: Participant.Identity) {
        launch {
            Timber.d { "Invite to stage $identity" }
            state.value?.authedApi?.run {
                inviteToStage(IdentityRequest(identity))
            } ?: Timber.e { "No authedApi found to invite to stage." }
        }
    }

    fun removeFromStage(identity: Participant.Identity) {
        launch {
            Timber.d { "Invite to stage $identity" }
            state.value?.authedApi?.run {
                removeFromStage(IdentityRequest(identity))
            } ?: Timber.e { "No authedApi found to invite to stage." }
        }
    }

}