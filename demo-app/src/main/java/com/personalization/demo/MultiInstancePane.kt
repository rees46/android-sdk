package com.personalization.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalization.OnClickListener
import com.personalization.Product
import com.personalization.Rees46
import com.personalization.SDK
import com.personalization.stories.compose.StoriesWidget

/**
 * "Multi-instance" tab — two shops living in one app at the same time.
 *
 * Shop A is the eager default (initialized in [DemoApplication]); shop B is registered there lazily
 * and comes to life the moment this screen resolves it (its session card or its [StoriesWidget]).
 * Everything each instance sends carries its own `shop_id`/`did`, so the HTTP log is the ground truth
 * for isolation — this screen surfaces the sessions, the per-shop stories, and the fail-fast /
 * push-routing behaviour in-app.
 */
@Composable
fun MultiInstancePane(
    shopIdA: String,
    shopIdB: String,
    storiesCodeA: String,
    storiesCodeB: String,
) {
    val events = remember { mutableStateListOf<String>() }
    fun log(message: String) {
        events.add(0, message)
        if (events.size > MAX_LOGGED_EVENTS) events.removeAt(events.lastIndex)
    }

    // Re-reading the sessions after B's async /init lands: bumping this recomposes the cards.
    var refreshToken by remember { mutableStateOf(0) }
    val shopA = remember(refreshToken) { runCatching { Rees46.getInstance(shopIdA) }.getOrNull() }
    // Resolving B by its id materializes the lazy registration — this is where shop B is born.
    val shopB = remember(refreshToken) { runCatching { Rees46.getInstance(shopIdB) }.getOrNull() }

    // The global Rees46.setOnMessageListener (DemoApplication) already posts a notification for every
    // shop. This extra, screen-scoped subscriber shows the (deprecated) per-instance
    // SDK.setOnMessageListener still works and fires alongside the global one: pressing "push
    // shop_id=B" both posts the notification and logs the callback here in-app.
    LaunchedEffect(shopB) {
        @Suppress("DEPRECATION")
        shopB?.setOnMessageListener { data ->
            log("✓ shop B onMessage: ${data.title ?: data.type ?: "—"}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.mi_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = stringResource(R.string.mi_subtitle), fontSize = 13.sp)

        SessionCard(title = stringResource(R.string.mi_shop_a), shopId = shopIdA, sdk = shopA)
        SessionCard(title = stringResource(R.string.mi_shop_b), shopId = shopIdB, sdk = shopB)
        Button(onClick = { refreshToken++; log("refreshed sessions") }) {
            Text(text = "Refresh sessions")
        }

        // Per-shop stories: each widget self-resolves its own instance by shopId and loads the block.
        // Kept on a light surface (the block colors its labels from merchant settings assuming a
        // light backdrop) so it stays legible when the demo is in dark mode.
        Text(text = "${stringResource(R.string.mi_shop_a)} — stories", fontWeight = FontWeight.Bold)
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            StoriesWidget(
                code = storiesCodeA,
                modifier = Modifier.fillMaxWidth(),
                shopId = shopIdA,
                onClickListener = clickLogger("A", ::log),
            )
        }

        Text(text = "${stringResource(R.string.mi_shop_b)} — stories", fontWeight = FontWeight.Bold)
        if (storiesCodeB.isNotBlank()) {
            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                StoriesWidget(
                    code = storiesCodeB,
                    modifier = Modifier.fillMaxWidth(),
                    shopId = shopIdB,
                    onClickListener = clickLogger("B", ::log),
                )
            }
        } else {
            Text(text = stringResource(R.string.mi_stories2_missing), fontSize = 12.sp)
        }

        // Fail-fast contracts, exercised live.
        Text(text = stringResource(R.string.mi_contracts_title), fontWeight = FontWeight.Bold)
        Button(onClick = { log(runContract { Rees46.getInstance() }) }) {
            Text(text = stringResource(R.string.mi_btn_ambiguous))
        }
        Button(onClick = { log(runContract { Rees46.getInstance("nope") }) }) {
            Text(text = stringResource(R.string.mi_btn_unknown))
        }

        // Push routing through the same entry point the SDK's messaging services use.
        Text(text = stringResource(R.string.mi_push_title), fontWeight = FontWeight.Bold)
        Button(onClick = {
            SDK.onMessage(pushPayload(shopIdA, "Shop A push", "Routed to shop A"))
            log("injected shop_id=A → routes to A (system notification + track/received)")
        }) { Text(text = stringResource(R.string.mi_btn_push_a)) }
        Button(onClick = {
            SDK.onMessage(pushPayload(shopIdB, "Shop B push", "Routed to shop B"))
            log("injected shop_id=B → routes to B (see callback above + track/received)")
        }) { Text(text = stringResource(R.string.mi_btn_push_b)) }
        Button(onClick = {
            SDK.onMessage(pushPayload("zzz-unknown-shop", "Unknown", "Should be dropped"))
            log("injected shop_id=unknown → dropped (no track/received)")
        }) { Text(text = stringResource(R.string.mi_btn_push_unknown)) }
        Button(onClick = {
            SDK.onMessage(mapOf("type" to "bulk", "id" to "mi-demo", "title" to "No shop", "body" to "Ambiguous"))
            log("injected no shop_id → dropped (2 shops live, ambiguous)")
        }) { Text(text = stringResource(R.string.mi_btn_push_none)) }

        if (events.isEmpty()) {
            Text(text = stringResource(R.string.mi_log_placeholder), fontSize = 12.sp)
        } else {
            events.forEach { event ->
                Text(text = event, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SessionCard(title: String, shopId: String, sdk: SDK?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = "shop_id=$shopId", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            val did = sdk?.getDid().orEmpty()
            val sid = sdk?.getSid().orEmpty()
            val body = if (sdk == null || (did.isBlank() && sid.isBlank())) {
                stringResource(R.string.mi_not_ready)
            } else {
                stringResource(R.string.mi_session_line, did.ifBlank { "—" }, sid.ifBlank { "—" })
            }
            Text(text = body, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

/** Runs a getInstance call and turns the outcome (a value or the fail-fast exception) into a log line. */
private inline fun runContract(call: () -> Any): String = try {
    call()
    "unexpected: call returned without throwing"
} catch (throwable: Throwable) {
    "${throwable::class.simpleName}: ${throwable.message}"
}

private fun clickLogger(shop: String, log: (String) -> Unit) = object : OnClickListener {
    override fun onClick(url: String): Boolean {
        log("shop $shop onClick(url): $url")
        return true
    }

    override fun onClick(product: Product): Boolean {
        log("shop $shop onClick(product): ${product.name}")
        return true
    }
}

private fun pushPayload(shopId: String, title: String, body: String): Map<String, String> = mapOf(
    "shop_id" to shopId,
    "type" to "bulk",
    "id" to "mi-demo",
    "title" to title,
    "body" to body,
)

private const val MAX_LOGGED_EVENTS = 20
