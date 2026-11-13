package com.personalization.sdk.data.repositories.network

import com.personalization.api.OnApiCallbackListener
import com.personalization.sdk.domain.repositories.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Requests made before the device has a `did` wait in a queue that `/init` drains. Adding and
 * draining race on different threads: no request may be lost, stuck or started twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkRepositoryQueueTest {

    private val did = AtomicReference("")
    private val settings = mockk<UserSettingsRepository>(relaxed = true) {
        every { getDid() } answers { did.get() }
        every { getShopId() } returns "shop"
        every { getSid() } returns "sid"
        every { getSegmentForABTesting() } returns "A"
        every { getStream() } returns "android"
    }
    private val repository = NetworkRepositoryImpl(settings).apply {
        // A port nobody listens on: every request ends at once in onError, which is all we count.
        val port = ServerSocket(0).use { it.localPort }
        initialize("http://127.0.0.1:$port/")
    }

    @Test
    fun `tasks added while the queue drains all run once`() {
        val producers = 8
        val perProducer = 200
        val ran = AtomicInteger()
        val failures = CopyOnWriteArrayList<Throwable>()
        val producing = CountDownLatch(producers)

        repeat(producers) {
            Thread {
                repeat(perProducer) { repository.addTaskToQueue(Thread { ran.incrementAndGet() }) }
                producing.countDown()
            }.start()
        }
        val drainer = Thread {
            try {
                while (producing.count > 0) repository.executeQueueTasks()
            } catch (throwable: Throwable) {
                failures += throwable
            }
        }
        drainer.start()
        assertTrue(producing.await(10, TimeUnit.SECONDS))
        drainer.join(10_000)
        repository.executeQueueTasks()

        waitFor { ran.get() == producers * perProducer }
        assertEquals(emptyList<Throwable>(), failures)
        assertEquals(producers * perProducer, ran.get())
    }

    @Test
    fun `requests made around the arrival of the did are all sent`() {
        val requests = 200
        val ended = AtomicInteger()
        val listener = object : OnApiCallbackListener() {
            override fun onSuccess(response: JSONObject?) {
                ended.incrementAndGet()
            }

            override fun onError(code: Int, msg: String?) {
                ended.incrementAndGet()
            }
        }
        val start = CountDownLatch(1)
        val senders = (1..4).map {
            Thread {
                start.await()
                repeat(requests / 4) { repository.getAsync("products", JSONObject(), listener) }
            }.apply { start() }
        }
        val init = Thread {
            start.await()
            Thread.sleep(2)
            did.set("did")
            repository.executeQueueTasks()
        }.apply { start() }

        start.countDown()
        senders.forEach { it.join(10_000) }
        init.join(10_000)

        waitFor { ended.get() == requests }
        assertEquals(requests, ended.get())
    }

    private fun waitFor(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }
}
