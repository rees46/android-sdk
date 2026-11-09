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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personalization.ui.components.PersonalizationProduct
import com.personalization.ui.components.PersonalizationProductsBlock
import com.personalization.ui.widgets.PersonalizationRecommenderBlock
import com.personalization.ui.widgets.PersonalizationRecommenderBlockController
import com.personalization.ui.widgets.PersonalizationRecommenderBlockWidget

/** A recommender block of the demo shop (c1140c…) that has products. */
private const val DEMO_RECOMMENDER_CODE = "e6249bb15043644bf25b135006149962"

/**
 * The data-bound recommender block on the demo shop, the way a host drops it in: a code and a
 * shopId, and the block loads, hides while it has nothing to show, and appears with the products.
 * Its onLoaded / onError land in the caption under it; "Reload" goes past the cache.
 *
 * All three exhibits ask for the same code, so only the first goes to the network — the others
 * come out of the kit's response cache at once.
 */
@Composable
internal fun LiveRecommenderExhibit(title: String, shopId: String, layout: PersonalizationProductsBlock.Layout, limit: Int? = null) {
    var status by remember { mutableStateOf("Loading… (the block stays hidden until it has products)") }
    var block by remember { mutableStateOf<PersonalizationRecommenderBlock?>(null) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(title)
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                PersonalizationRecommenderBlock(ctx).apply {
                    this.shopId = shopId
                    code = DEMO_RECOMMENDER_CODE
                    this.layout = layout
                    this.limit = limit
                    actionText = "Add to cart"
                    showAllText = "Show all"
                    onShowAll = { ctx.toast("Show all") }
                    onProductClick = { product -> ctx.toast(product.describe()) }
                    onProductAction = { product -> ctx.toast("Add to cart: ${product.name}") }
                    onLoaded = { count -> status = "onLoaded: $count" }
                    onError = { code, message -> status = "onError $code: $message" }
                    block = this
                }
            }
        )
        StatusRow(status) {
            status = "Reloading…"
            block?.reload()
        }
    }
}

/** The same block through the Compose wrapper, reloaded through its controller. */
@Composable
internal fun LiveRecommenderComposeExhibit(shopId: String) {
    val context = LocalContext.current
    val controller = remember { PersonalizationRecommenderBlockController() }
    var status by remember { mutableStateOf("Loading… (zero height until it has products)") }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("Recommender Block — Compose PersonalizationRecommenderBlockWidget, list, own title")
        PersonalizationRecommenderBlockWidget(
            code = DEMO_RECOMMENDER_CODE,
            modifier = Modifier.fillMaxWidth(),
            shopId = shopId,
            controller = controller,
            layout = PersonalizationProductsBlock.Layout.LIST,
            title = "Popular, from Compose",
            limit = 3,
            actionText = "Add to cart",
            onProductClick = { product -> context.toast(product.describe()) },
            onProductAction = { product -> context.toast("Add to cart: ${product.name}") },
            onLoaded = { count -> status = "onLoaded: $count" },
            onError = { code, message -> status = "onError $code: $message" }
        )
        StatusRow(status) {
            status = "Reloading…"
            controller.reload()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
    )
}

@Composable
private fun StatusRow(status: String, onReload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onReload) { Text("Reload") }
        Text(text = status, style = MaterialTheme.typography.caption)
    }
}

/** Where the host would navigate: the app deeplink first, the site page otherwise. */
private fun PersonalizationProduct.describe(): String = "Product: $name\n${deeplink ?: url ?: "no link"}"

private fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
