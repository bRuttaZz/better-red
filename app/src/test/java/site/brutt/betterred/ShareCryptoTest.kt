package site.brutt.betterred

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Flow
import site.brutt.betterred.domain.Mood
import site.brutt.betterred.domain.Sex
import site.brutt.betterred.domain.Symptom
import site.brutt.betterred.share.Share
import site.brutt.betterred.share.ShareChoices
import site.brutt.betterred.share.Server
import site.brutt.betterred.share.ShareCode
import site.brutt.betterred.share.ShareConfig
import site.brutt.betterred.share.ShareCrypto
import site.brutt.betterred.share.forPartner
import java.time.LocalDate

class ShareCryptoTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // RFC 6238 appendix B (SHA-256, 8 digits). The broker's auth_test.go checks the same values.
    @Test fun totpMatchesRfc() {
        val key = "12345678901234567890123456789012".toByteArray()
        mapOf(59L to "46119246", 1111111109L to "68084774", 1111111111L to "67062674", 1234567890L to "91819424", 2000000000L to "90698825")
            .forEach { (t, want) -> assertEquals(want, ShareCrypto.totp(key, t)) }
    }

    // Same vector as the broker (vectors_test.go): both hash device keys the same way.
    @Test fun deviceIdMatchesBroker() {
        assertEquals("Zmh6rfhivXdsj8GLjp-OIA", ShareCrypto.deviceId(ByteArray(32)))
    }

    @Test fun fingerprintIsShortAndStable() {
        val fp = ShareCrypto.fingerprint("pair", ByteArray(32))
        assertEquals(fp, ShareCrypto.fingerprint("pair", ByteArray(32)))
        assertTrue(Regex("^[0-9A-F]{4}( [0-9A-F]{4}){3}$").matches(fp))
        assertNotEquals(fp, ShareCrypto.fingerprint("pair", ByteArray(32) { 1 }))
    }

    @Test fun encryptRoundTrip() {
        val (pub, priv) = ShareCrypto.newKeyPair()
        assertEquals(65, pub.size)
        assertEquals(32, priv.size)
        val plain = ShareCrypto.pack("""{"flow":"HEAVY","mood":"CALM"}""")
        val blob = ShareCrypto.encrypt(pub, "pair|2026-11-20", plain)
        assertEquals(1.toByte(), blob[0])
        assertTrue("blob stays small: ${blob.size}", blob.size < 200)
        assertArrayEquals(plain, ShareCrypto.decrypt(priv, "pair|2026-11-20", blob))
        // Every encryption is different (fresh ephemeral key and nonce).
        assertNotEquals(hex(blob), hex(ShareCrypto.encrypt(pub, "pair|2026-11-20", plain)))
    }

    @Test fun blobsCanNotBeMovedChangedOrOpenedByOthers() {
        val (pub, priv) = ShareCrypto.newKeyPair()
        val (_, otherPriv) = ShareCrypto.newKeyPair()
        val blob = ShareCrypto.encrypt(pub, "pair|2026-11-20", ShareCrypto.pack("{}"))
        fails { ShareCrypto.decrypt(priv, "pair|2026-11-21", blob) } // moved to another day
        fails { ShareCrypto.decrypt(otherPriv, "pair|2026-11-20", blob) } // someone else's key
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        fails { ShareCrypto.decrypt(priv, "pair|2026-11-20", tampered) }
        val badPoint = blob.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() } // not on the curve
        fails { ShareCrypto.decrypt(priv, "pair|2026-11-20", badPoint) }
    }

    @Test fun packCompressesWhenItHelps() {
        val short = """{"mood":"LOW"}"""
        assertEquals(0.toByte(), ShareCrypto.pack(short)[0])
        assertEquals(short, ShareCrypto.unpack(ShareCrypto.pack(short)))
        val long = """{"note":"${"tea and rest ".repeat(15)}"}"""
        val packed = ShareCrypto.pack(long)
        assertEquals(1.toByte(), packed[0])
        assertTrue(packed.size < long.length / 2)
        assertEquals(long, ShareCrypto.unpack(packed))
    }

    @Test fun shareCodeRoundTripAndRejectsJunk() {
        val code = ShareCode(Server("better-red.brutt.site"), "A".repeat(22) + "." + "B".repeat(32), ByteArray(32) { 1 }, ByteArray(32) { 2 })
        val text = code.encode()
        assertTrue("fits a QR code comfortably: ${text.length}", text.length < 200)
        assertEquals(code, ShareCode.parse(text))
        assertNull(ShareCode.parse("hello"))
        assertNull(ShareCode.parse(text.replace("BR1", "BR9")))
        assertNull(ShareCode.parse(text.replace("better-red.brutt.site", "evil site")))
        assertNull(ShareCode.parse(text.replace(code.pairId, "A".repeat(22) + "." + "B".repeat(22)))) // old/short pair id
        val local = ShareCode(Server("localhost:8080"), code.pairId, code.privateKey, code.secret).encode()
        assertNull(ShareCode.parse(local)) // only debug builds
        assertEquals(Server("localhost:8080"), ShareCode.parse(local, allowLocal = true)?.server)
    }

    @Test fun shareCodeCarriesTheServerPassword() {
        // Every awkward character a password may have, including the code's own separators.
        val server = Server("my.server.org", "p@ss|w%rd+/:#?&=~")
        val code = ShareCode(server, "A".repeat(22) + "." + "B".repeat(32), ByteArray(32) { 1 }, ByteArray(32) { 2 })
        val text = code.encode()
        assertEquals(5, text.split('|').size)
        assertEquals(server, ShareCode.parse(text)?.server)
        assertTrue("the password is never part of toString", "p@ss" !in server.toString())
    }

    @Test fun serverWithOptionalPassword() {
        assertEquals(Server("better-red.brutt.site"), Server.parse("better-red.brutt.site"))
        assertEquals(Server("my.server.org", "Secret123"), Server.parse(" https://Secret123@My.Server.org/ "))
        assertEquals(Server("my.server.org", "a@b@c"), Server.parse("a@b@c@my.server.org")) // the last "@" splits
        assertEquals(Server("localhost:8080", "pw"), Server.parse("pw@localhost:8080", allowLocal = true))
        for (bad in listOf("@my.server.org", "with space@my.server.org", "pässword@my.server.org", "pw@", "pw@not a host",
            "x".repeat(129) + "@my.server.org", "pw@localhost:8080")) {
            assertNull(bad, Server.parse(bad))
        }
        for (s in listOf(Server("a.org"), Server("a.org", "Secret123"), Server("a.org", "p@ss|w%rd+ ".trim()))) {
            assertEquals(s, Server.decode(s.encode()))
        }
        assertEquals("a.org", Server("a.org").encode())
        assertEquals("Secret123@a.org", Server("a.org", "Secret123").encode())
    }

    @Test fun serverAddressesAreDomainsOnly() {
        assertEquals("better-red.brutt.site", Share.normalizeServer(" https://Better-Red.brutt.site/ "))
        assertEquals("relay.example.org:8443", Share.normalizeServer("relay.example.org:8443"))
        for (bad in listOf("", "localhost", "brutt", "https://x.org/path", "a b.org", "x.org?q=1", "-x.org", "user@x.org")) {
            assertNull(bad, Share.normalizeServer(bad))
        }
        assertEquals("10.0.2.2:8080", Share.normalizeServer("http://10.0.2.2:8080", allowLocal = true))
        assertEquals("http://localhost:8080", Share.baseUrl("localhost:8080"))
        assertEquals("https://better-red.brutt.site", Share.baseUrl("better-red.brutt.site"))
    }

    @Test fun configRoundTrip() {
        val c = ShareConfig("Better", 29, 5, 13, ShareChoices(mood = true, symptoms = false, notes = true, fertility = false), 60)
        assertEquals(c, ShareConfig.fromJson(c.toJson()))
    }

    @Test fun onlyChosenThingsAreShared() {
        val day = DayLog(LocalDate.of(2026, 11, 20), Flow.LIGHT, Mood.LOW, setOf(Symptom.CRAMPS), "private", Sex.PROTECTED)
        val periodOnly = day.forPartner(ShareChoices(mood = false))!!
        assertEquals(DayLog(day.date, flow = Flow.LIGHT), periodOnly)
        val all = day.forPartner(ShareChoices(mood = true, symptoms = true, notes = true))!!
        assertNull("sex is never shared", all.sex)
        assertEquals("private", all.note)
        assertNull("a day with nothing shared is left out", DayLog(day.date, sex = Sex.PROTECTED, note = "x").forPartner(ShareChoices()))
    }

    private fun fails(block: () -> Unit) {
        val failed = runCatching(block).isFailure
        assertTrue("should have failed", failed)
    }
}
