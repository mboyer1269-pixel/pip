package ca.michael.pip.core.acp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject
import ca.michael.pip.core.Clock

/** HMAC-SHA256 over the canonical form, base64url without padding (SPEC 6.3, ACP-11). */
object AcpSigner {
    const val SECRET_BYTES = 32

    fun sign(unsigned: JsonObject, secret: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal(CanonicalJson.encode(unsigned).toByteArray(Charsets.UTF_8))
        return b64(digest)
    }

    /** Constant-time comparison of the expected and received signatures. */
    fun verify(unsigned: JsonObject, sig: String, secret: ByteArray): Boolean {
        val received = try {
            Base64.getUrlDecoder().decode(sig)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val expected = mac.doFinal(CanonicalJson.encode(unsigned).toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(expected, received)
    }

    fun newSecret(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(SECRET_BYTES).also { random.nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Builds signed outbound envelopes. */
class EnvelopeFactory(
    private val secret: () -> ByteArray?,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) {
    /** Returns null when Pip is not paired: nothing can be signed. */
    fun create(type: String, body: JsonObject): Envelope? {
        require(type in Envelope.TYPES) { "unknown envelope type" }
        val key = secret() ?: return null
        val nonce = ByteArray(16).also { random.nextBytes(it) }
        val draft = Envelope(
            v = Envelope.VERSION,
            agentId = Envelope.AGENT_ID,
            msgId = UUID.randomUUID().toString(),
            ts = clock.nowMs(),
            nonce = AcpSigner.b64(nonce),
            type = type,
            body = body,
            sig = "",
        )
        return draft.copy(sig = AcpSigner.sign(draft.unsignedJson(), key))
    }
}
