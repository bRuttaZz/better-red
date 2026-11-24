package site.brutt.betterred.share

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * All the cryptography of partner sharing, using only what Java/Android provide (no library).
 * Pure JVM code, so it is unit-tested on the computer. docs/partner-sharing.md explains the design.
 *
 * Keys
 * - Device key: 32 random bytes per phone, never shared. Its hash is the device id inside the pair
 *   id; her phone sends the key to prove a pair id is hers (the broker keeps nothing).
 * - Pair secret: 32 random bytes made by her phone, given to the partner in the QR code, and sent by
 *   her phone with every write (the broker stores it). The partner's one-time codes come from it.
 * - Share key pair: P-256 (secp256r1). Her phone keeps the public key; the private key goes only
 *   into the QR code, to the partner. The broker never sees either.
 *
 * A blob (one day, or the settings) = version 1 | ephemeral public key (65) | nonce (12) | AES-GCM ciphertext + tag.
 * The AES key comes from ECDH(ephemeral, partner key) through HKDF-SHA256. The pair id and the day
 * are bound in as associated data, so the broker can't swap days around.
 */
object ShareCrypto {
    const val KEY_BYTES = 32
    private const val BLOB_VERSION: Byte = 1
    private const val POINT_BYTES = 65
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_PLAIN = 64 * 1024 // refuse to inflate more than this (zip bombs)
    private const val HKDF_INFO = "better-red/blob/v1"

    private val random = SecureRandom()
    private val b64e = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    fun b64(bytes: ByteArray): String = b64e.encodeToString(bytes)
    fun unb64(text: String): ByteArray = b64d.decode(text)

    fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    // ---------------------------------------------------------------- ids and codes

    /** The phone's public device id: first 16 bytes of SHA-256(device key), base64url. */
    fun deviceId(deviceKey: ByteArray): String = b64(sha256(deviceKey).copyOf(16))

    /** RFC 6238 one-time code: HMAC-SHA256, 30 second steps, 8 digits. */
    fun totp(key: ByteArray, unixSeconds: Long): String {
        val mac = hmac(key, ByteBuffer.allocate(8).putLong(unixSeconds / 30).array())
        val off = mac.last().toInt() and 0x0f
        val bin = ByteBuffer.wrap(mac, off, 4).int and 0x7fffffff
        return (bin % 100_000_000).toString().padStart(8, '0')
    }

    /** Short code both phones show, to check they're paired with each other: "7F3A 91C2 0B4D E8A1". */
    fun fingerprint(pairId: String, secret: ByteArray): String =
        sha256(pairId.toByteArray() + secret).copyOf(8).joinToString("") { "%02X".format(it) }.chunked(4).joinToString(" ")

    // ---------------------------------------------------------------- key pair

    private val params: ECParameterSpec by lazy {
        (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public as ECPublicKey).params
    }

    /** A new share key pair: (public key as an uncompressed point, 65 bytes; private key, 32 bytes). */
    fun newKeyPair(): Pair<ByteArray, ByteArray> {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(params, random) }.generateKeyPair()
        return encodePoint((kp.public as ECPublicKey).w) to fixed32((kp.private as ECPrivateKey).s)
    }

    private fun fixed32(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size) // drop the sign byte
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun encodePoint(p: ECPoint): ByteArray = byteArrayOf(4) + fixed32(p.affineX) + fixed32(p.affineY)

    private fun publicKey(bytes: ByteArray): java.security.PublicKey {
        require(bytes.size == POINT_BYTES && bytes[0] == 4.toByte()) { "bad public key" }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        require(onCurve(x, y)) { "point not on the curve" } // blocks invalid-curve attacks
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
    }

    private fun privateKey(bytes: ByteArray): java.security.PrivateKey {
        require(bytes.size == 32) { "bad private key" }
        return KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, bytes), params))
    }

    private fun onCurve(x: BigInteger, y: BigInteger): Boolean {
        val p = (params.curve.field as java.security.spec.ECFieldFp).p
        if (x.signum() < 0 || y.signum() < 0 || x >= p || y >= p) return false
        val left = y.modPow(BigInteger.valueOf(2), p)
        val right = x.modPow(BigInteger.valueOf(3), p).add(params.curve.a.multiply(x)).add(params.curve.b).mod(p)
        return left == right
    }

    // ---------------------------------------------------------------- blobs

    /** Encrypts [plain] for the holder of the private key matching [recipientPublic]. */
    fun encrypt(recipientPublic: ByteArray, aad: String, plain: ByteArray): ByteArray {
        val recipient = publicKey(recipientPublic)
        val eph = KeyPairGenerator.getInstance("EC").apply { initialize(params, random) }.generateKeyPair()
        val ephBytes = encodePoint((eph.public as ECPublicKey).w)
        val shared = KeyAgreement.getInstance("ECDH").apply { init(eph.private); doPhase(recipient, true) }.generateSecret()
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(hkdf(shared, ephBytes), "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad.toByteArray())
        }
        return byteArrayOf(BLOB_VERSION) + ephBytes + nonce + cipher.doFinal(plain)
    }

    /** Opens a blob made by [encrypt]. Throws if it was changed, or is for another key or day. */
    fun decrypt(privateKey: ByteArray, aad: String, blob: ByteArray): ByteArray {
        require(blob.size > 1 + POINT_BYTES + NONCE_BYTES + TAG_BITS / 8 && blob[0] == BLOB_VERSION) { "bad blob" }
        val ephBytes = blob.copyOfRange(1, 1 + POINT_BYTES)
        val nonce = blob.copyOfRange(1 + POINT_BYTES, 1 + POINT_BYTES + NONCE_BYTES)
        val shared = KeyAgreement.getInstance("ECDH").apply { init(privateKey(privateKey)); doPhase(publicKey(ephBytes), true) }.generateSecret()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(hkdf(shared, ephBytes), "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad.toByteArray())
        }
        return cipher.doFinal(blob, 1 + POINT_BYTES + NONCE_BYTES, blob.size - 1 - POINT_BYTES - NONCE_BYTES)
    }

    /** What is bound to each blob: which pair, which day ("0" for the settings). */
    fun aad(pairId: String, key: String) = "$pairId|$key"

    // ---------------------------------------------------------------- compression

    /** Packed data is padded with zeros to a multiple of this, so blob sizes don't hint at what's inside. */
    const val PAD = 64

    /**
     * JSON → bytes, compressed with DEFLATE when that is smaller. First byte: 0 = plain, 1 = deflated.
     * Then zero-padded to a multiple of [PAD] (DEFLATE ignores what follows its end; plain JSON drops trailing zeros).
     */
    fun pack(json: String): ByteArray {
        val raw = json.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val zipped = try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
            out.toByteArray()
        } finally {
            deflater.end()
        }
        val packed = if (zipped.size < raw.size) byteArrayOf(1) + zipped else byteArrayOf(0) + raw
        return packed.copyOf((packed.size + PAD - 1) / PAD * PAD)
    }

    fun unpack(bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "empty" }
        val body = bytes.copyOfRange(1, bytes.size)
        return when (bytes[0].toInt()) {
            0 -> body.toString(Charsets.UTF_8).trimEnd('\u0000')
            1 -> {
                val inflater = Inflater(true)
                try {
                    inflater.setInput(body)
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(1024)
                    while (!inflater.finished()) {
                        val n = inflater.inflate(buf)
                        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                        out.write(buf, 0, n)
                        require(out.size() <= MAX_PLAIN) { "too large" }
                    }
                    out.toByteArray().toString(Charsets.UTF_8)
                } finally {
                    inflater.end()
                }
            }
            else -> error("unknown packing")
        }
    }

    // ---------------------------------------------------------------- primitives

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** HKDF-SHA256 (RFC 5869) for one 32-byte output block. */
    private fun hkdf(ikm: ByteArray, salt: ByteArray): ByteArray {
        val prk = hmac(salt, ikm)
        return hmac(prk, HKDF_INFO.toByteArray() + byteArrayOf(1))
    }
}
