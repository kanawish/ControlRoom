package io.livekit.android.sample.livestream

import android.content.Context
import io.livekit.android.LiveKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * NOTE:
 * Below failed, but that's normal. Fixable I believe... but it'll be kludgey.
 * 2024-09-18 14:19:49.107 17804-17804 JoinScreen...$startLoad io....kit.android.sample.livestream  V  response received: JoinStreamResponse(authToken=eyJhbGciOiJIUzI1NiJ9.eyJyb29tX25hbWUiOiJrYW5hc3RydWsiLCJpZGVudGl0eSI6IkV0aWVubmUifQ.mn7Wj8mRcpEXLEDALGHbYYZemoN9PsPmUH7E4tSOKcc, connectionDetails=ConnectionDetails(wsUrl=ws://localhost:7880, token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ2aWRlbyI6eyJyb29tIjoia2FuYXN0cnVrIiwicm9vbUpvaW4iOnRydWUsImNhblB1Ymxpc2giOmZhbHNlLCJjYW5TdWJzY3JpYmUiOnRydWUsImNhblB1Ymxpc2hEYXRhIjp0cnVlfSwiaWF0IjoxNzI2NjgzNTg5LCJuYmYiOjE3MjY2ODM1ODksImV4cCI6MTcyNjcwNTE4OSwiaXNzIjoiZGV2a2V5Iiwic3ViIjoiRXRpZW5uZSIsImp0aSI6IkV0aWVubmUifQ.mnrcSQOC54gz-y5V_8tBTxhg-QM98DHNIoHJaErcfl4))
 */

object Sandbox {
}

const val wsUrl = "ws://localhost:7880" // Points to your LiveKit server.
const val token = "<SECRET>" // // Participant access token: backend-generated, expires.

fun CoroutineScope.foo(applicationContext: Context, wsUrl: String, token: String) = launch {
    wsUrl // Points to your LiveKit server.
    token // access token used by each Participant to connect. Backend-generated.

    val room = LiveKit.create(appContext = applicationContext)
    room.connect(wsUrl, token)

    // Populated after successful connection:
    room.localParticipant
    room.remoteParticipants

    // Leaving a room. (Fallback: 15 second auto-disconnect)
    room.disconnect()
}

