package com.personalizatio.stories

import android.annotation.TargetApi

internal class StoryItemView : ConstraintLayout {
    interface OnPageListener {
        fun onPrev()

        fun onNext()

        fun onPrepared(position: Int)

        fun onLocked(lock: Boolean)
    }

    var image: ImageView? = null
    var video: PlayerView? = null
    private var product: ConstraintLayout? = null
    private var product_price_box: LinearLayout? = null
    private var product_discount_box: LinearLayout? = null
    private var product_brand: TextView? = null
    private var product_name: TextView? = null
    private var product_oldprice: TextView? = null
    private var product_price: TextView? = null
    private var product_discount: TextView? = null
    private var promocode_text: TextView? = null
    private var product_image: ImageView? = null

    private var page_listener: OnPageListener? = null
    private var prev: View? = null
    private var next: View? = null
    private var stories_view: StoriesView? = null

    //Элементы управления
    private var button: Button? = null
    var reload: ImageButton? = null
    var reload_layout: View? = null
    private var reload_text: TextView? = null
    private var header: ConstraintLayout? = null
    private var titleTextView: TextView? = null
    private var subtitleTextView: TextView? = null
    private var titleCardView: CardView? = null
    private var titleIconImageView: ImageView? = null
    private var button_products: Button? = null
    private var elements_layout: ViewGroup? = null
    private var products: RecyclerView? = null
    private var products_adapter: ProductsAdapter? = null

    constructor(@NonNull context: Context?) : super(context)

    constructor(@NonNull context: Context?, @Nullable attrs: AttributeSet?) : super(context, attrs)

    constructor(@NonNull context: Context?, @Nullable attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        @NonNull context: Context?,
        @Nullable attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Override
    protected fun onFinishInflate() {
        super.onFinishInflate()
        init()
    }

    private fun init() {
        image = findViewById(android.R.id.background)
        video = findViewById(android.R.id.widget_frame)
        image.setVisibility(View.GONE)
        video.setVisibility(View.GONE)

        //Переключение слайда
        prev = findViewById(R.id.reverse)
        next = findViewById(R.id.skip)
        prev.setOnClickListener { View ->
            if (page_listener != null) {
                page_listener.onPrev()
            }
        }
        next.setOnClickListener { View ->
            if (page_listener != null) {
                page_listener.onNext()
            }
        }

        //Элементы управления
        button = findViewById(android.R.id.button1)
        reload = findViewById(R.id.reload)
        reload_layout = findViewById(R.id.reload_layout)
        reload_text = findViewById(R.id.reload_text)
        header = findViewById(R.id.header)
        titleTextView = findViewById(R.id.title_textView)
        subtitleTextView = findViewById(R.id.subtitle_textView)
        titleIconImageView = findViewById(R.id.title_imageView)
        titleCardView = findViewById(R.id.titleCardView)
        button_products = findViewById(android.R.id.button2)
        elements_layout = findViewById(R.id.elements_layout)

        products_adapter = ProductsAdapter()
        products = findViewById(android.R.id.list)
        products.setAdapter(products_adapter)

        //Товарный слайд
        product = findViewById(R.id.product)
        product.setVisibility(GONE)

        product_price_box = findViewById(R.id.product_price_box)
        product_brand = findViewById(R.id.product_brand)
        product_name = findViewById(R.id.product_name)
        product_oldprice = findViewById(R.id.product_oldprice)
        product_price = findViewById(R.id.product_price)
        product_discount = findViewById(R.id.product_discount)
        product_discount_box = findViewById(R.id.product_discount_box)
        product_image = findViewById(R.id.product_image)
        promocode_text = findViewById(R.id.promocode_text)
    }

    /**
     * Слушатель переключения слайда
     * @param listener OnPageListener
     */
    fun setOnPageListener(listener: OnPageListener?) {
        page_listener = listener
    }

    /**
     * Слушатель клика по ссылке
     * @param view StoriesView
     */
    fun setStoriesView(view: StoriesView?) {
        stories_view = view
        products_adapter.setStoriesView(view)
    }

    fun setOnTouchListener(listener: OnTouchListener?) {
        prev.setOnTouchListener(listener)
        next.setOnTouchListener(listener)
        super.setOnTouchListener { v, event -> true }
    }

    /**
     * Обновляет данные слайда
     *
     * @param slide Story.Slide
     */
    fun update(slide: Story.Slide?, position: Int, code: String?, story_id: Int) {
        slide.prepared = false
        setBackgroundColor(
            if (slide.background_color == null) getContext().getResources()
                .getColor(android.R.color.black) else Color.parseColor(slide.background_color)
        )
        video.setVisibility(GONE)
        reload_layout.setVisibility(GONE)
        reload_text.setTypeface(stories_view.settings.failed_load_font_family)
        reload_text.setTextSize(stories_view.settings.failed_load_size)
        reload_text.setText(stories_view.settings.failed_load_text)
        reload_text.setTextColor(Color.parseColor(stories_view.settings.failed_load_color))

        reload.setOnClickListener { View ->
            update(slide, position, code, story_id)
        }

        //Вызываем клик по кнопке
        button.setOnClickListener { view ->
            try {
                var product: Product? = null
                var link: String? = null
                //Сначала ищем элемент с товаром
                for (element in slide.elements) {
                    when (element.type) {
                        "product" -> product = element.item
                        "button" -> link = element.link
                    }
                }
                Log.d(
                    SDK.TAG,
                    "open link: " + link + (if (product != null) (" with product: `" + product.id).toString() + "`" else "")
                )
                //Вызываем колбек клика
                if (stories_view.click_listener == null || product == null && stories_view.click_listener.onClick(link) || product != null && stories_view.click_listener.onClick(
                        product
                    )
                ) {
                    getContext().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }
                SDK.track_story("click", code, story_id, slide.id)
            } catch (e: ActivityNotFoundException) {
                Log.e(SDK.TAG, e.getMessage(), e)
                Toast.makeText(getContext(), "Unknown error", Toast.LENGTH_SHORT).show()
            } catch (e: NullPointerException) {
                Log.e(SDK.TAG, e.getMessage(), e)
                Toast.makeText(getContext(), "Unknown error", Toast.LENGTH_SHORT).show()
            }
        }

        //Загуражем картинку
        if (slide.type.equals("image")) {
            loadImage(slide.background, object : RequestListener() {
                @Override
                fun onLoadFailed(
                    @Nullable e: GlideException?,
                    model: Object?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    reload_layout.setVisibility(VISIBLE)
                    return false
                }

                @Override
                fun onResourceReady(
                    resource: Drawable?,
                    model: Object?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    onPreparedSlide(slide, position)
                    return false
                }
            })
        }

        //Загружаем видео
        if (slide.type.equals("video")) {
            video.setVisibility(VISIBLE)

            //Загружаем превью
            if (slide.preview != null) {
                loadImage(slide.preview, object : RequestListener() {
                    @Override
                    fun onLoadFailed(
                        @Nullable e: GlideException?,
                        model: Object?,
                        target: Target<Drawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }

                    @Override
                    fun onResourceReady(
                        resource: Drawable?,
                        model: Object?,
                        target: Target<Drawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
            }
        }

        //Обновляем элементы слайда
        updateElements(slide, code, story_id, position)
    }

    private fun onPreparedSlide(slide: Story.Slide?, position: Int) {
        slide.prepared = true
        reload_layout.setVisibility(GONE)
        elements_layout.setVisibility(VISIBLE)
        if (!button_products.isActivated() && page_listener != null) {
            page_listener.onPrepared(position)
        }
    }

    /**
     * Загружает изображение слайда
     *
     * @param url      String
     * @param listener RequestListener<Drawable>
    </Drawable> */
    private fun loadImage(url: String?, listener: RequestListener<Drawable?>?) {
        image.setVisibility(View.VISIBLE)
        Glide.with(getContext()).load(url).listener(listener).into(image)
    }

    fun updateProduct(element: Story.Slide.Element?, listener: RequestListener<Drawable?>?) {
        product.setVisibility(VISIBLE)
        product_brand.setVisibility(if (element.item.brand == null) GONE else VISIBLE)
        product_brand.setText(element.item.brand)
        product_brand.setTypeface(stories_view.settings.font_family)
        product_name.setText(element.item.name)
        product_name.setTypeface(stories_view.settings.font_family)
        Glide.with(getContext()).load(element.item.image).listener(listener).override(Target.SIZE_ORIGINAL)
            .into(product_image)
        product_price.setText(element.item.price)
        product_price.setTypeface(stories_view.settings.font_family)
        product_oldprice.setVisibility(if (element.item.oldprice == null) GONE else VISIBLE)
        product_oldprice.setText(element.item.oldprice)
        product_oldprice.setPaintFlags(product_oldprice.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
        product_oldprice.setTypeface(stories_view.settings.font_family)
        product_discount_box.setVisibility(if (element.item.discount_percent == null && element.item.promocode == null) GONE else VISIBLE)
        promocode_text.setText(element.title)
        promocode_text.setVisibility(if (element.title == null || element.item.promocode == null) GONE else VISIBLE)
        promocode_text.setTypeface(stories_view.settings.font_family)

        //Указываем закругления
        val radius: Float = getResources().getDimension(R.dimen.product_price_box_radius)
        val shape_box: ShapeAppearanceModel =
            ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, radius).build()
        val shape_box_drawable: MaterialShapeDrawable = MaterialShapeDrawable(shape_box)
        val shape_discount: ShapeAppearanceModel = ShapeAppearanceModel().toBuilder()
            .setTopRightCorner(CornerFamily.ROUNDED, radius)
            .setBottomRightCorner(CornerFamily.ROUNDED, radius)
            .build()
        val shape_discount_drawable: MaterialShapeDrawable = MaterialShapeDrawable(shape_discount)

        //Заполняем
        shape_box_drawable.setFillColor(ContextCompat.getColorStateList(getContext(), android.R.color.white))
        ViewCompat.setBackground(product_price_box, shape_box_drawable)

        //Блок скидки или промокода
        product_discount.setTypeface(stories_view.settings.font_family)
        if (element.item.promocode != null) {
            product_discount.setText(element.item.promocode)
            product_price.setText(element.item.price_with_promocode)
            shape_discount_drawable.setFillColor(
                ContextCompat.getColorStateList(
                    getContext(),
                    R.color.product_promocode_color
                )
            )
        } else if (element.item.discount_percent != null) {
            product_discount.setText(("-" + element.item.discount_percent).toString() + "%")
            shape_discount_drawable.setFillColor(
                ContextCompat.getColorStateList(
                    getContext(),
                    R.color.product_discount_color
                )
            )
        }

        ViewCompat.setBackground(product_discount_box, shape_discount_drawable)
    }

    fun release() {
        video.setPlayer(null)
    }

    fun setHeadingVisibility(visibility: Int) {
        elements_layout.animate().alpha(if (visibility == GONE) 0 else 1)
            .setStartDelay(if (visibility == GONE) 100 else 0).setDuration(200)
    }

    private fun updateHeader(element: Story.Slide.Element?, slide_id: String?, code: String?, story_id: Int) {
        if (element.type.equals("header")) {
            header.setVisibility(VISIBLE)
            header.setOnTouchListener { v: View?, event: MotionEvent? ->
                if (event.getAction() === MotionEvent.ACTION_UP) {
                    getContext().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(element.link)))
                    SDK.track_story("click", code, story_id, slide_id)
                }
                true
            }

            if (element.icon != null) {
                titleCardView.setVisibility(VISIBLE)
                if (getContext() == null) return
                Glide.with(getContext()).load(element.icon).into(titleIconImageView)
            } else {
                titleCardView.setVisibility(GONE)
            }

            titleTextView.setTypeface(stories_view.settings.font_family)
            if (element.title != null) {
                titleTextView.setVisibility(VISIBLE)
                titleTextView.setText(element.title)
            } else {
                titleTextView.setVisibility(GONE)
            }

            subtitleTextView.setTypeface(stories_view.settings.font_family)
            if (element.subtitle != null) {
                subtitleTextView.setVisibility(VISIBLE)
                subtitleTextView.setText(element.subtitle)
            } else {
                subtitleTextView.setVisibility(GONE)
            }
        }
    }

    private fun updateElements(slide: Story.Slide?, code: String?, story_id: Int, position: Int) {
        //Скрываем все элементы

        header.setVisibility(GONE)
        button.setVisibility(GONE)
        button_products.setVisibility(GONE)
        products.setVisibility(GONE)

        //Отображаем необходимые элементы
        for (element in slide.elements) {
            when (element.type) {
                "header" -> updateHeader(element, slide.id, code, story_id)
                "button" -> {
                    button.setVisibility(VISIBLE)
                    button.setText(element.title)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        button.setBackgroundTintList(
                            ColorStateList.valueOf(
                                if (element.background == null) button.getContext().getResources()
                                    .getColor(R.color.primary) else Color.parseColor(element.background)
                            )
                        )
                    } else {
                        button.setBackgroundColor(
                            if (element.background == null) button.getContext().getResources()
                                .getColor(R.color.primary) else Color.parseColor(element.background)
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        button.setTextColor(
                            ColorStateList.valueOf(
                                if (element.color == null) button.getContext().getResources()
                                    .getColor(R.color.white) else Color.parseColor(element.color)
                            )
                        )
                    } else {
                        button.setTextColor(
                            if (element.color == null) button.getContext().getResources()
                                .getColor(R.color.white) else Color.parseColor(element.color)
                        )
                    }
                    button.setTypeface(
                        Typeface.create(
                            stories_view.settings.button_font_family,
                            if (element.text_bold) Typeface.BOLD else Typeface.NORMAL
                        )
                    )
                }

                "products" -> {
                    products_adapter.setProducts(element.products, story_id, slide.id)
                    button_products.setVisibility(VISIBLE)
                    button_products.setText(element.label_show)
                    button_products.setTypeface(stories_view.settings.products_button_font_family)
                    button_products.setOnClickListener { view ->
                        button_products.setActivated(!button_products.isActivated())
                        button_products.setText(if (button_products.isActivated()) element.label_hide else element.label_show)

                        //Анимация появления товаров
                        val set: ConstraintSet = ConstraintSet()
                        set.clone(elements_layout as ConstraintLayout?)

                        val transition: Transition = ChangeBounds()
                        transition.addTarget(button_products.getId())
                        transition.addTarget(button.getId())

                        val transition2: Transition = Slide(Gravity.BOTTOM)
                        transition2.addTarget(products.getId())

                        val transitions: TransitionSet = TransitionSet()
                        transitions.addTransition(transition)
                        transitions.addTransition(transition2)

                        TransitionManager.beginDelayedTransition(elements_layout, transitions)
                        products.setVisibility(if (button_products.isActivated()) VISIBLE else GONE)

                        //--->
                        if (page_listener != null) {
                            page_listener.onLocked(button_products.isActivated())
                        }
                    }
                    if (page_listener != null) {
                        page_listener.onLocked(button_products.isActivated())
                    }
                }

                "product" -> updateProduct(element, object : RequestListener() {
                    @Override
                    fun onLoadFailed(
                        @Nullable e: GlideException?,
                        model: Object?,
                        target: Target<Drawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }

                    @Override
                    fun onResourceReady(
                        resource: Drawable?,
                        model: Object?,
                        target: Target<Drawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        slide.prepared = true
                        if (page_listener != null) {
                            page_listener.onPrepared(position)
                        }
                        return false
                    }
                })
            }
        }
    }
}
