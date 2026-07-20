package com.personalization.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalization.OnClickListener
import com.personalization.Product
import com.personalization.SDK
import com.personalization.stories.compose.StoriesWidget

/**
 * "UI Kit" tab — the stories block through the SDK's Compose wrapper.
 *
 * The counterpart of the "Legacy UI" tab in [MainActivity], which shows the same block through the
 * XML [com.personalization.stories.views.StoriesView].
 */
@Composable
fun ComposeStoriesPane(sdk: SDK, code: String) {
    var routeOwnScheme by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<String>() }

    fun log(message: String) {
        events.add(0, message)
        if (events.size > MAX_LOGGED_EVENTS) {
            events.removeAt(events.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.compose_stories_title),
            fontWeight = FontWeight.Bold
        )

        StoriesWidget(
            sdk = sdk,
            code = code,
            modifier = Modifier.fillMaxWidth(),
            onClickListener = object : OnClickListener {
                override fun onClick(url: String): Boolean {
                    val openedBySdk = !(routeOwnScheme && url.startsWith(OWN_SCHEME))
                    log("onClick(url): $url — ${if (openedBySdk) "opened by SDK" else "routed by the app"}")
                    return openedBySdk
                }

                override fun onClick(product: Product): Boolean {
                    log("onClick(product): ${product.name}")
                    return true
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.compose_stories_routing))
            Switch(checked = routeOwnScheme, onCheckedChange = { routeOwnScheme = it })
        }

        if (events.isEmpty()) {
            Text(text = stringResource(R.string.compose_stories_log_placeholder))
        } else {
            events.forEach { event ->
                Text(text = event, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

private const val OWN_SCHEME = "demo://"
private const val MAX_LOGGED_EVENTS = 20
