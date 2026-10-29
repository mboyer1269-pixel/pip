package ca.michael.pip.acp

import ca.michael.pip.AcpState
import ca.michael.pip.BuildConfig
import ca.michael.pip.PipRuntime
import ca.michael.pip.core.acp.EnvelopeFactory
import ca.michael.pip.core.acp.EnvelopeVerifier
import ca.michael.pip.core.acp.RejectReason
import ca.michael.pip.core.acp.Verdict
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * The visible ACP client (V1-6). One outbound wss:// connection that the
 * owner opens from S5; Pip listens on no port (ACP-4). Every frame is
 * verified before anything reads it; tasks then wait for the owner (S7).
 */
class AcpConnection(private val runtime: PipRuntime, private val url: String) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    private val verifier = EnvelopeVerifier({ runtime.secrets.acpSecret() }, runtime.clock)
    private val factory = EnvelopeFactory({ runtime.secrets.acpSecret() }, runtime.clock)
    @Volatile private var socket: WebSocket? = null

    fun open() {
        require(url.startsWith("wss://")) { "wss only" }
        runtime.state.update { acp = AcpState.CONNECTING }
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    fun send(type: String, body: JsonObject): Boolean {
        val envelope = factory.create(type, body) ?: return false
        return socket?.send(envelope.toWire()) ?: false
    }

    fun close() {
        socket?.close(1000, null)
        socket = null
        client.dispatcher.executorService.shutdown()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            runtime.state.update { acp = AcpState.CONNECTED }
            send("hello", buildJsonObject { put("pip_version", JsonPrimitive(BuildConfig.VERSION_NAME)) })
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val verdict = verifier.verify(text)) {
                is Verdict.Accepted -> if (verdict.envelope.type == "task") runtime.broker.onTask(verdict.envelope)
                // Other verified types (status, error) carry no action in v1.
                is Verdict.Rejected -> runtime.broker.onEnvelopeRejected(verdict.reason, verdict.detail)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            runtime.broker.onEnvelopeRejected(RejectReason.MALFORMED, "binary frame")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket === webSocket) runtime.state.update { acp = AcpState.OFF }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket === webSocket) runtime.state.update { acp = AcpState.OFF }
        }
    }
}
