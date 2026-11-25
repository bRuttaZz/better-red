package site.brutt.betterred

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import site.brutt.betterred.share.Broker
import site.brutt.betterred.share.BrokerException
import site.brutt.betterred.share.RemoteEntry
import site.brutt.betterred.share.ShareChoices
import site.brutt.betterred.share.Server
import site.brutt.betterred.share.ShareCode
import site.brutt.betterred.share.ShareConfig
import site.brutt.betterred.share.ShareCrypto
import site.brutt.betterred.share.SyncError
import java.time.LocalDate

/**
 * The whole protocol against a real broker. Skipped unless one is running:
 *
 *   cd ../better-red-broker && go run . -addr localhost:8099 -db /tmp/br/br.db -key-file /tmp/br/br.key
 *   BR_TEST_BROKER=localhost:8099 ./gradlew testDebugUnitTest
 *
 * For a broker started with BR_PASSWORD=…, also set BR_TEST_PASSWORD to the same value: the
 * password check (missing and wrong passwords) is tested too.
 */
class BrokerIntegrationTest {
    private val host = System.getenv("BR_TEST_BROKER")
    private val password = System.getenv("BR_TEST_PASSWORD")?.takeIf { it.isNotBlank() }
    private val server = host?.let { Server(it, password) }

    private fun kind(block: () -> Unit) = (runCatching(block).exceptionOrNull() as BrokerException).kind

    @Test fun publisherToPartner() {
        assumeTrue("set BR_TEST_BROKER to run", !host.isNullOrBlank())
        val broker = Broker(server!!)
        if (password != null) {
            assertEquals(SyncError.PASSWORD, kind { Broker(Server(host!!)).mint(ShareCrypto.randomKey()) })
            assertEquals(SyncError.PASSWORD, kind { Broker(Server(host!!, "wrong-$password")).mint(ShareCrypto.randomKey()) })
        }

        // Her phone: mint a pair id (nothing stored yet), make a secret and a key pair, show the QR.
        val deviceKey = ShareCrypto.randomKey()
        val pairId = broker.mint(deviceKey).pairId
        val secret = ShareCrypto.randomKey()
        val (pub, priv) = ShareCrypto.newKeyPair()
        val qr = ShareCode(server, pairId, priv, secret).encode()
        val code = ShareCode.parse(qr, allowLocal = true)!!

        // The partner scans before she uploaded anything: waiting.
        assertEquals(SyncError.WAITING, kind { Broker(code.server).read(code.pairId, code.secret) })
        assertTrue(broker.index(pairId, deviceKey).days.isEmpty())

        fun seal(key: String, json: String) =
            RemoteEntry(ShareCrypto.b64(ShareCrypto.encrypt(pub, ShareCrypto.aad(pairId, key), ShareCrypto.pack(json))), Broker.now())

        val today = LocalDate.now().toString()
        val config = ShareConfig("Better", 28, 5, 14, ShareChoices(), 60)
        val all = mapOf("0" to seal("0", config.toJson()), today to seal(today, """{"flow":"HEAVY"}"""))
        broker.push(pairId, deviceKey, secret, all, emptyList())
        assertEquals(setOf("0", today), broker.index(pairId, deviceKey).days.keys)

        // The partner reads and decrypts.
        val remote = Broker(code.server).read(code.pairId, code.secret)
        fun open(key: String) = ShareCrypto.unpack(
            ShareCrypto.decrypt(code.privateKey, ShareCrypto.aad(code.pairId, key), ShareCrypto.unb64(remote.days.getValue(key).blob))
        )
        assertEquals(config, ShareConfig.fromJson(open("0")))
        assertEquals("""{"flow":"HEAVY"}""", open(today))

        // The partner can't write: it doesn't have her device key.
        assertEquals(SyncError.INVALID, kind { broker.push(pairId, ShareCrypto.randomKey(), secret, all, emptyList()) })
        // A wrong secret can't read.
        assertEquals(SyncError.CLOCK, kind { Broker(code.server).read(code.pairId, ShareCrypto.randomKey()) })
        // A made-up pair id is refused outright.
        assertEquals(SyncError.INVALID, kind { Broker(code.server).read(pairId.dropLast(2) + "AA", code.secret) })

        // She deletes a day.
        broker.push(pairId, deviceKey, secret, emptyMap(), listOf(today))
        assertEquals(setOf("0"), broker.index(pairId, deviceKey).days.keys)

        // A new pairing: new id; the old one is gone (the old partner waits).
        val newPairId = broker.mint(deviceKey).pairId
        assertNotEquals(pairId, newPairId)
        assertEquals(SyncError.WAITING, kind { Broker(code.server).read(code.pairId, code.secret) })

        // Unpair.
        broker.push(newPairId, deviceKey, secret, all.filterKeys { it == "0" }.mapValues { seal("0", config.toJson()) }, emptyList())
        broker.delete(newPairId, deviceKey)
        assertTrue(broker.index(newPairId, deviceKey).days.isEmpty())
    }
}
