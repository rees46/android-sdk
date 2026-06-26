package com.personalization

import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A push can reach the SDK in a process where initialize() never ran — e.g. the cold process FCM
 * spins up just to deliver a message, or a host that integrated the SDK incorrectly. In that state
 * the Dagger-injected fields (sendNetworkMethodUseCase, …) are not set, and the push path used to
 * crash the host app with UninitializedPropertyAccessException.
 *
 * These tests pin the contract: handling a push on an uninitialized SDK must NOT throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MessagingServicePushRobustnessTest {

    @Test
    fun notificationReceived_onUninitializedSdk_doesNotThrow() {
        val sdk = SDK() // initialize() never called

        // Before the guard this threw UninitializedPropertyAccessException: sendNetworkMethodUseCase.
        sdk.notificationReceived(mapOf("type" to "stored_product", "id" to "123"))
    }

    @Test
    fun onMessageReceived_onUninitializedSdk_doesNotCrashHost() {
        val service = MessagingService()
        val remoteMessage = mockk<RemoteMessage>(relaxed = true)
        every { remoteMessage.data } returns mapOf("type" to "stored_product", "id" to "123")
        every { remoteMessage.notification } returns null

        // The FCM callback must never let an SDK failure crash the host app's messaging thread.
        service.onMessageReceived(remoteMessage)
    }
}
