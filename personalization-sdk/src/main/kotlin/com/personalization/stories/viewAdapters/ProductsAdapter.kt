@file:SuppressLint("NotifyDataSetChanged", "SetTextI18n")

package com.personalization.stories.viewAdapters

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.personalization.OnLinkClickListener
import com.personalization.Product
import com.personalization.R
import com.personalization.SDK
import com.personalization.stories.Settings

internal class ProductsAdapter(
    private val itemClickListener: OnLinkClickListener?,
    private val code: String,
    private val settings: Settings,
    private val onCloseStories: () -> Unit
) : RecyclerView.Adapter<ProductsAdapter.ViewHolder>() {

    private var products: List<Product> = emptyList()
    private var storyId = 0
    private var slideId: String = ""

    fun setProducts(products: List<Product>, storyId: Int, slideId: String) {
        this.products = products
        this.storyId = storyId
        this.slideId = slideId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount() = products.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val image: ImageView = view.findViewById(android.R.id.icon)
        private val name: TextView = view.findViewById(android.R.id.text1)
        private val oldPrice: TextView = view.findViewById(R.id.oldprice)
        private val discount: TextView = view.findViewById(R.id.discount)
        private val price: TextView = view.findViewById(R.id.price)

        init {
            setupStrikeThrough()
            applyTypeface()
        }

        private fun setupStrikeThrough() {
            oldPrice.paintFlags = oldPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        private fun applyTypeface() {
            val typeface = settings.font_family
            name.typeface = typeface
            oldPrice.typeface = typeface
            discount.typeface = typeface
            price.typeface = typeface
        }

        fun bind(product: Product) {
            loadProductImage(product)
            name.text = product.name
            setupPriceViews(product)
            setupClickListener(product)
        }

        private fun loadProductImage(product: Product) {
            Glide.with(image.context)
                .load(product.image)
                .into(image)
        }

        private fun setupPriceViews(product: Product) {
            val hasOldPrice = product.oldPrice.isNotEmpty()
            discount.isVisible = hasOldPrice
            oldPrice.isVisible = hasOldPrice

            if (hasOldPrice) {
                oldPrice.text = product.oldPrice
                discount.text = "-${product.discount}%"
            }
            price.text = product.price
        }

        private fun setupClickListener(product: Product) {
            itemView.setOnClickListener {
                Log.d(SDK.TAG, "click: ${product.name}, ${product.url}")
                handleProductClick(product)
            }
        }

        private fun handleProductClick(product: Product) {
            try {
                val urlString = product.deeplink.takeUnless { it.isEmpty() } ?: product.url

                if (itemClickListener?.onClick(product) == true) {
                    when {
                        itemClickListener.onCloseDialogClick(
                            product = product,
                            url = urlString
                        ) -> onCloseStories.invoke()

                        else -> itemView.openLinkInBrowser(Uri.parse(urlString))
                    }
                }
            } catch (e: ActivityNotFoundException) {
                Log.e(SDK.TAG, e.message, e)
            }
        }
    }

    private fun View.openLinkInBrowser(uri: Uri) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        SDK.instance.trackStory("click", code, storyId, slideId)
    }
}
