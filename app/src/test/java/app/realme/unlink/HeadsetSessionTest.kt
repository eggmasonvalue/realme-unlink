package app.realme.unlink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HeadsetSessionTest {
    @Test
    fun ignoresUnsolicitedFramesAndMatchesTheExpectedResponse() {
        val responseWire = wire(
            HeadsetProtocol.wrap(
                HeadsetProtocol.encode(
                    command = 0x0501,
                    transferId = 99,
                    payload = byteArrayOf(1, 2, 3),
                ),
            ),
            response(
                command = HeadsetProtocol.BATTERY_COMMAND,
                transferId = 0,
                payload = byteArrayOf(0, 1, 1, 70),
            ),
        )
        val output = ByteArrayOutputStream()
        val session = HeadsetSession.forTesting(ByteArrayInputStream(responseWire), output)

        try {
            val frame = session.transact(HeadsetProtocol.BATTERY_COMMAND)

            assertEquals(0x8106, frame.command)
            assertEquals(0, frame.transferId)
            assertArrayEquals(byteArrayOf(0, 1, 1, 70), frame.payload)
            assertArrayEquals(
                HeadsetProtocol.wrap(
                    HeadsetProtocol.encode(HeadsetProtocol.BATTERY_COMMAND, 0),
                ),
                output.toByteArray(),
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun buttonMappingQueryUsesTheCanonicalReadOnlyPayload() {
        val output = ByteArrayOutputStream()
        val session = HeadsetSession.forTesting(
            input = ByteArrayInputStream(
                response(HeadsetProtocol.KEY_FUNCTION_COMMAND, 0, byteArrayOf(0)),
            ),
            output = output,
        )

        try {
            session.queryButtonMappings()

            assertArrayEquals(
                HeadsetProtocol.wrap(
                    HeadsetProtocol.encode(
                        HeadsetProtocol.KEY_FUNCTION_COMMAND,
                        0,
                        byteArrayOf(14, 1, 3, 4, 5, 6, 11, 12, 8, 13, 17, 31, 26, 27, 0),
                    ),
                ),
                output.toByteArray(),
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun sendsAllOutboundFragmentsBeforeReadingTheResponse() {
        val requestPayload = ByteArray(2_100) { it.toByte() }
        val request = HeadsetProtocol.encode(0x0123, 0, requestPayload)
        val output = ByteArrayOutputStream()
        val session = HeadsetSession.forTesting(
            input = ByteArrayInputStream(response(0x0123, 0, byteArrayOf(0))),
            output = output,
        )

        try {
            session.transact(0x0123, requestPayload)

            assertArrayEquals(
                wire(*HeadsetProtocol.wrapFrames(request).toTypedArray()),
                output.toByteArray(),
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun matchesTransferIdsAcrossTheEightBitRollover() {
        val responses = ByteArrayOutputStream()
        repeat(257) { transferId ->
            responses.write(response(HeadsetProtocol.BATTERY_COMMAND, transferId and 0xFF, byteArrayOf(0)))
        }
        val session = HeadsetSession.forTesting(
            input = ByteArrayInputStream(responses.toByteArray()),
            output = ByteArrayOutputStream(),
        )

        try {
            repeat(257) {
                val frame = session.transact(HeadsetProtocol.BATTERY_COMMAND)
                assertEquals(0x8106, frame.command)
            }
        } finally {
            session.close()
        }
    }

    @Test
    fun failsCleanlyWhenThePeerDisconnects() {
        val session = HeadsetSession.forTesting(
            input = ByteArrayInputStream(ByteArray(0)),
            output = ByteArrayOutputStream(),
        )

        try {
            session.transact(HeadsetProtocol.BATTERY_COMMAND)
            fail("Expected a disconnected-peer failure")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("Failed reading"))
        } finally {
            session.close()
        }
    }

    @Test
    fun closeCancelsAnInFlightTransaction() {
        val input = BlockingInputStream()
        val session = HeadsetSession.forTesting(
            input = input,
            output = ByteArrayOutputStream(),
            responseTimeoutMillis = 5_000,
        )
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        val worker = Thread {
            try {
                session.transact(HeadsetProtocol.BATTERY_COMMAND)
                fail("Expected cancellation")
            } catch (error: Throwable) {
                failure = error
            } finally {
                done.countDown()
            }
        }
        worker.start()

        try {
            assertTrue(input.awaitReadStarted())
            session.close()
            assertTrue(done.await(1, TimeUnit.SECONDS))
            assertTrue(failure is IOException)
        } finally {
            session.close()
            worker.join(1_000)
        }
    }

    @Test
    fun cancelsAndClosesABlockedReadOnTimeout() {
        val input = BlockingInputStream()
        val session = HeadsetSession.forTesting(
            input = input,
            output = ByteArrayOutputStream(),
            responseTimeoutMillis = 50,
        )

        try {
            session.transact(HeadsetProtocol.BATTERY_COMMAND)
            fail("Expected a response timeout")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("Timed out"))
            assertTrue(input.wasClosed)
        } finally {
            session.close()
        }
    }

    private fun response(command: Int, transferId: Int, payload: ByteArray): ByteArray {
        return HeadsetProtocol.wrap(
            HeadsetProtocol.encode(command or 0x8000, transferId, payload),
        )
    }

    private fun wire(vararg frames: ByteArray): ByteArray {
        return ByteArrayOutputStream().also { output ->
            frames.forEach(output::write)
        }.toByteArray()
    }

    private class BlockingInputStream : InputStream() {
        private val readStartedLatch = CountDownLatch(1)
        private val closedLatch = CountDownLatch(1)

        @Volatile
        var wasClosed: Boolean = false
            private set

        fun awaitReadStarted(): Boolean = readStartedLatch.await(1, TimeUnit.SECONDS)

        override fun read(): Int {
            readStartedLatch.countDown()
            try {
                closedLatch.await()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("blocked read interrupted", error)
            }
            throw IOException("stream closed")
        }

        override fun close() {
            wasClosed = true
            closedLatch.countDown()
        }
    }
}
