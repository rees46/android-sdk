package com.personalizatio.stories

import android.content.ActivityNotFoundException

internal class ProductsAdapter : RecyclerView.Adapter<ProductsAdapter.ViewHolder?>() {
    private var products: ArrayList<Product?>? = ArrayList()
    private var story_id = 0
    private var slide_id: String? = null
    private var stories_view: StoriesView? = null

    fun setStoriesView(view: StoriesView?) {
        stories_view = view
    }

    fun setProducts(products: ArrayList<Product?>?, story_id: Int, slide_id: String?) {
        this.products = products
        this.story_id = story_id
        this.slide_id = slide_id
        notifyDataSetChanged()
    }

    @NonNull
    @Override
    fun onCreateViewHolder(@NonNull parent: ViewGroup?, viewType: Int): ViewHolder? {
        return ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.product, parent, false))
    }

    @Override
    fun onBindViewHolder(@NonNull holder: ViewHolder?, position: Int) {
        holder.bind(products.get(position))
    }

    @Override
    fun getItemCount(): Int {
        return products.size()
    }

    internal inner class ViewHolder(@NonNull view: View?) : RecyclerView.ViewHolder(view) {
        val image: ImageView? = view.findViewById(android.R.id.icon)
        val name: TextView? = view.findViewById(android.R.id.text1)
        val oldprice: TextView? = view.findViewById(R.id.oldprice)
        val discount: TextView?
        val price: TextView?

        init {
            oldprice.setPaintFlags(oldprice.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
            discount = view.findViewById(R.id.discount)
            price = view.findViewById(R.id.price)
        }

        fun bind(product: Product?) {
            Glide.with(image.getContext()).load(product.image).into(image)
            name.setText(product.name)
            name.setTypeface(stories_view.settings.font_family)
            discount.setTypeface(stories_view.settings.font_family)
            oldprice.setTypeface(stories_view.settings.font_family)
            price.setTypeface(stories_view.settings.font_family)
            if (product.oldprice == null) {
                discount.setVisibility(View.GONE)
                oldprice.setVisibility(View.GONE)
            } else {
                discount.setVisibility(View.VISIBLE)
                oldprice.setVisibility(View.VISIBLE)
                oldprice.setText(product.oldprice)
                discount.setText((String.format("-%s%%", product.discount)))
            }
            price.setText(product.price)
            itemView.setOnClickListener { view ->
                Log.d(SDK.TAG, ("click: " + product.name).toString() + ", " + product.url)
                try {
                    if (stories_view.click_listener == null || stories_view.click_listener.onClick(product)) {
                        itemView.getContext().startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(if (product.deeplink != null) product.deeplink else product.url)
                            )
                        )
                    }
                    SDK.track_story("click", stories_view.code, story_id, slide_id)
                } catch (e: ActivityNotFoundException) {
                    Log.e(SDK.TAG, e.getMessage(), e)
                    Toast.makeText(itemView.getContext(), "Unknown error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
