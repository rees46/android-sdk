package com.personalizatio.stories

import android.content.res.ColorStateList

internal class StoriesAdapter(view: StoriesView?, data: ArrayList<Story?>?, private val listener: ClickListener?) :
    RecyclerView.Adapter<StoriesAdapter.ViewHolder?>() {
    private val data: ArrayList<Story?>? = data
    private val stories_view: StoriesView? = view

    interface ClickListener {
        fun onStoryClick(id: Int)
    }

    internal inner class ViewHolder(view: View?, private val listener: ClickListener?) : RecyclerView.ViewHolder(view),
        View.OnClickListener {
        val image: ShapeableImageView? = view.findViewById(R.id.avatar)
        val border: ShapeableImageView? = view.findViewById(R.id.avatar_border)
        val avatar_size: ViewGroup? = view.findViewById(R.id.avatar_size)
        val name: TextView? = view.findViewById(android.R.id.text1)
        val pin: TextView? = view.findViewById(android.R.id.text2)
        private var story_index = 0

        fun setRow(story: Story?, position: Int) {
            val scale: Float = itemView.getResources().getDisplayMetrics().density

            //Загружаем иконку
            Glide.with(image.getContext()).load(story.avatar).into(image)

            //Изменяем иконку на квадратную
            if (stories_view.settings.icon_display_format === Settings.ICON_DISPLAY_FORMAT.RECTANGLE) {
                val shape: ShapeAppearanceModel = image.getShapeAppearanceModel().toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 0)
                    .build()
                image.setShapeAppearanceModel(shape)
                border.setShapeAppearanceModel(shape)
            }

            //Загружаем в кеш картинку первого слайда
            if (story.slides.get(story.start_position).type.equals("image")) {
                Glide.with(image.getContext()).load(story.slides.get(story.start_position).background).preload()
            }
            if (stories_view.settings.new_campaign_border_color != null) {
                border.setStrokeWidth(border.getContext().getResources().getDimension(R.dimen.story_avatar_border))
                border.setStrokeColor(ColorStateList.valueOf(Color.parseColor(if (story.viewed) stories_view.settings.visited_campaign_border_color else stories_view.settings.new_campaign_border_color)))
            } else {
                //Default border style for old api
                border.setStrokeWidth(
                    if (story.viewed) 0 else border.getContext().getResources()
                        .getDimension(R.dimen.story_avatar_border)
                )
            }
            itemView.setAlpha(if (story.viewed) stories_view.settings.visited_campaign_transparency else 1)
            //Размер аватарки
            val layoutParams: ViewGroup.LayoutParams = avatar_size.getLayoutParams()
            layoutParams.width = (stories_view.settings.icon_size * scale + 0.5f) as Int
            layoutParams.height = (stories_view.settings.icon_size * scale + 0.5f) as Int
            avatar_size.setLayoutParams(layoutParams)
            //Устанавливаем отступы
            itemView.setPadding(
                if (stories_view.settings.icon_padding_x != null) (stories_view.settings.icon_padding_x * scale) as Int else itemView.getPaddingLeft(),
                if (stories_view.settings.icon_padding_top != null) (stories_view.settings.icon_padding_top * scale) as Int else itemView.getPaddingTop(),
                if (stories_view.settings.icon_padding_x != null) (stories_view.settings.icon_padding_x * scale) as Int else itemView.getPaddingRight(),
                if (stories_view.settings.icon_padding_bottom != null) (stories_view.settings.icon_padding_bottom * scale) as Int else itemView.getPaddingBottom()
            )
            //Позиция
            story_index = position
            image.setOnClickListener(this)
            name.setText(story.name)
            name.setTextColor(Color.parseColor(stories_view.settings.label_font_color))
            name.setTextSize(stories_view.settings.label_font_size)
            name.setTypeface(stories_view.settings.label_font_family)
            name.setWidth(((if (stories_view.settings.label_width != null) stories_view.settings.label_width else stories_view.settings.icon_size) * scale) as Int)
            //Пин
            pin.setVisibility(if (story.pinned) View.VISIBLE else View.GONE)
            pin.setText(stories_view.settings.pin_symbol)
            val shapeAppearanceModel: ShapeAppearanceModel =
                ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, 50).build()
            val shapeDrawable: MaterialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
            shapeDrawable.setFillColor(ColorStateList.valueOf(Color.parseColor(stories_view.settings.background_pin)))
            ViewCompat.setBackground(pin, shapeDrawable)
        }

        @Override
        fun onClick(view: View?) {
            listener.onStoryClick(story_index)
        }
    }

    @NonNull
    @Override
    fun onCreateViewHolder(viewGroup: ViewGroup?, viewType: Int): ViewHolder? {
        val view: View = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.story_avatar, viewGroup, false)

        return ViewHolder(view, listener)
    }

    @Override
    fun onBindViewHolder(viewHolder: ViewHolder?, position: Int) {
        viewHolder.setRow(data.get(position), position)
    }

    @Override
    fun getItemCount(): Int {
        return data.size()
    }
}
