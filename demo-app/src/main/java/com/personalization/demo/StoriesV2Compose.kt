package com.personalization.demo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalization.OnClickListener
import com.personalization.Product
import com.personalization.ui.widgets.PersonalizationStories
import com.personalization.ui.widgets.PersonalizationStoriesController
import com.personalization.ui.widgets.PersonalizationStoriesWidget

/** API v3 host of the "Stories V2 with Sharing" block, of the stories link field and of deep links. */
internal const val STORIES_API_V3_DOMAIN = "app.rees46.ru"

/** The demo's own link scheme (see the intent filters of MainActivity in the manifest). */
internal const val DEMO_LINK_SCHEME = "rees46"

/**
 * The demo shares stories links with its own scheme, so a shared link opens the demo app:
 * `https://demo.rees46.ru/?r46_stories=…` → `rees46://demo.rees46.ru/?r46_stories=…`.
 */
internal fun demoShareUrl(url: String): String =
    url.replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "$DEMO_LINK_SCHEME://")

/** A link to a slide of the demo shop's block, for the "Sample link" button. */
internal fun sampleStoriesLink(storiesCode: String): String =
    "$DEMO_LINK_SCHEME://demo.rees46.ru/?r46_stories=$storiesCode&r46_story=40&r46_slide=37"

/**
 * Opens a stories link (`r46_stories` / `r46_story` / `r46_slide`, any scheme, host and path) with
 * PersonalizationStories.handleLink on API v3. `false` — not a stories link.
 */
internal fun openStoriesLink(
    context: Context,
    url: String,
    shopId: String,
    onError: (String) -> Unit,
    onClose: () -> Unit = {},
    onClickListener: OnClickListener = DemoStoriesClicks(context)
): Boolean = PersonalizationStories.handleLink(
    context = context,
    url = url,
    shopId = shopId,
    onClickListener = onClickListener,
    onError = { code, message -> onError("handleLink() onError $code: $message") },
    onClose = onClose,
    apiV3Domain = STORIES_API_V3_DOMAIN,
    shareUrlTransform = ::demoShareUrl
)

/**
 * A stories link opened the app — `rees46://…` or `https://demo.rees46.ru/…` (see the manifest): any
 * host and path, only `r46_stories` matters. The campaign opens over whatever [activity] shows.
 * A link to the demo site that is not a stories link (a popup's "Learn more", say) goes on to the
 * browser.
 */
internal fun openStoriesLinkIntent(
    activity: Activity,
    intent: Intent?,
    shopId: String,
    onClickListener: OnClickListener = DemoStoriesClicks(activity)
) {
    if (intent?.action != Intent.ACTION_VIEW) return
    val url = intent.dataString ?: return
    if (!url.contains("r46_stories=")) {
        if (intent.data?.scheme?.lowercase() in setOf("http", "https")) openInBrowser(activity, url)
        return
    }
    val opened = openStoriesLink(
        context = activity,
        url = url,
        shopId = shopId,
        onError = { message ->
            Log.w("StoriesLink", "$url: $message")
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        },
        onClickListener = onClickListener
    )
    if (!opened) Toast.makeText(activity, "Not a stories link: $url", Toast.LENGTH_LONG).show()
}

/**
 * Opens [url] in the default browser. A plain ACTION_VIEW would come back here: the demo takes the
 * links of its own site.
 */
private fun openInBrowser(activity: Activity, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
    }
    try {
        activity.startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(activity, "No browser to open $url", Toast.LENGTH_LONG).show()
    }
}

/**
 * A "Share" link of a slide opened with PersonalizationStories.handleLink() on API v3 — paste it from
 * the share sheet of the "Stories V2 with Sharing" block. The field starts empty; "Sample link" puts a
 * link to the demo shop's block into it.
 */
@Composable
internal fun StoriesV2LinkExhibit(storiesCode: String, shopId: String) {
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Errors of handleLink() and the viewer closing appear here") }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Stories V2 — open a stories link (handleLink, API v3)",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.weight(1f),
                label = { Text("Stories link") },
                placeholder = { Text("https://…?r46_stories=…&r46_story=…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val handled = openStoriesLink(
                    context = context,
                    url = link.trim(),
                    shopId = shopId,
                    onError = { status = it },
                    onClose = { status = "handleLink() viewer closed" }
                )
                status = if (handled) "handleLink(): opening the campaign…" else "handleLink(): not a stories link"
            }) { Text("Open") }
        }
        OutlinedButton(onClick = { link = sampleStoriesLink(storiesCode) }) { Text("Sample link") }
        Text(text = status, style = MaterialTheme.typography.caption)
    }
}

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
