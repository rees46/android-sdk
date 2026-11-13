package com.personalization.handlers.notifications

import com.personalization.features.notification.domain.helpers.NotificationClickProcessor
import com.personalization.sdk.domain.usecases.trackingSource.SetTrackingSourceUseCase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Test

/**
 * A clicked push attributes later events through the tracking source — the `source` object of
 * `/push` events — and not through top-level `from` and `code`, which `/init` counts as a click.
 */
class NotificationHandlerSourceTest {

    private val setTrackingSource = mockk<SetTrackingSourceUseCase> {
        every { this@mockk(any(), any()) } just runs
    }
    private val processor = mockk<NotificationClickProcessor> {
        every { processClick(any(), any(), any()) } answers {
            thirdArg<(String, String) -> Unit>().invoke("bulk", "push-1")
        }
    }
    private val handler = NotificationHandler(setTrackingSource, processor)

    @Test
    fun `a clicked push sets the tracking source`() {
        handler.notificationClicked(extras = null, sendAsync = { _, _ -> })

        verify(exactly = 1) { setTrackingSource(type = "bulk", code = "push-1") }
    }
}
