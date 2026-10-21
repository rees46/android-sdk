package com.personalization.demo

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalization.OnClickListener
import com.personalization.Product
import com.personalization.ui.widgets.PersonalizationStories
import com.personalization.ui.widgets.PersonalizationStoriesController
import com.personalization.ui.widgets.PersonalizationStoriesWidget

/**
 * The Compose wrapper of the Stories V2 block on the demo shop, driven by a controller (open a story
 * by index — while the viewer is open it pages there), and the same block shown without the bar
 * through PersonalizationStories.show(), which reports the viewer closing.
 */
@Composable
internal fun StoriesV2ComposeExhibit(storiesCode: String, shopId: String) {
    val context = LocalContext.current
    val controller = remember { PersonalizationStoriesController() }
    var status by remember { mutableStateOf("Errors of the block and of show(), and its closing, appear here") }
    val clicks = remember { DemoStoriesClicks(context) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Stories V2 — Compose PersonalizationStoriesWidget with a controller",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        PersonalizationStoriesWidget(
            code = storiesCode,
            shopId = shopId,
            controller = controller,
            onClickListener = clicks,
            onError = { code, message -> status = "onError $code: $message" }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.open(1) }) { Text("Open #2") }
            OutlinedButton(onClick = {
                PersonalizationStories.show(
                    context = context,
                    code = storiesCode,
                    shopId = shopId,
                    onClickListener = clicks,
                    onError = { code, message -> status = "show() onError $code: $message" },
                    onClose = { status = "show() viewer closed" }
                )
            }) { Text("Show without bar") }
        }
        Text(text = status, style = MaterialTheme.typography.caption)
    }
}

/**
 * Logs every link and product the stories hand to the host and lets the SDK open it — the V1
 * OnClickListener contract: `true` means "SDK, open it", `false` means "the host handled it".
 */
internal class DemoStoriesClicks(private val context: Context) : OnClickListener {
    override fun onClick(url: String): Boolean {
        toast("Link: $url")
        return false
    }

    override fun onClick(product: Product): Boolean {
        toast("Product: ${product.name}")
        return false
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
