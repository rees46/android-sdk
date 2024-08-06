package com.personalization.stories.viewAdapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.personalization.R
import com.personalization.stories.Settings
import com.personalization.stories.models.Story
import com.personalization.stories.views.StoriesView

class StoriesAdapter (
    private val storiesView: StoriesView,
    private val data: List<Story>,
    private val listener: ClickListener
) : RecyclerView.Adapter<StoriesAdapter.ViewHolder>() {

    interface ClickListener {
        fun onStoryClick(id: Int)
    }

    inner class ViewHolder(view: View, private val listener: ClickListener) :
        RecyclerView.ViewHolder(view), View.OnClickListener {

        private val image: ShapeableImageView = view.findViewById(R.id.avatar)
        private val border: ShapeableImageView = view.findViewById(R.id.avatar_border)
        private val avatarSize: ViewGroup = view.findViewById(R.id.avatar_size)
        val name: TextView = view.findViewById(android.R.id.text1)
        private val pin: TextView = view.findViewById(android.R.id.text2)

        private var storyIndex = 0

        fun setRow(story: Story, position: Int) {
            val scale = itemView.resources.displayMetrics.density

            Glide.with(image.context).load(story.avatar).into(image)

            val settings = storiesView.settings

            if (settings.icon_display_format == Settings.ICON_DISPLAY_FORMAT.RECTANGLE) {
                val shape = image.shapeAppearanceModel.toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build()
                image.shapeAppearanceModel = shape
                border.shapeAppearanceModel = shape
            }

            val firstSlide = story.getSlide(story.startPosition)
            if (firstSlide.type == "image") {
                Glide.with(image.context).load(firstSlide.background).preload()
            }
            if (storiesView.settings.new_campaign_border_color != null) {
                border.strokeWidth = border.context.resources.getDimension(R.dimen.story_avatar_border)
                border.strokeColor = ColorStateList.valueOf(
                    Color.parseColor(
                        if (story.isViewed) settings.visited_campaign_border_color
                        else settings.new_campaign_border_color
                    )
                )
            } else {
                //Default border style for old api
                border.strokeWidth = if (story.isViewed) 0f else border.context.resources.getDimension(R.dimen.story_avatar_border)
            }
            itemView.alpha = if (story.isViewed) settings.visited_campaign_transparency else 1f

            val layoutParams = avatarSize.layoutParams
            val layoutParamsSize = (settings.icon_size * scale + 0.5f).toInt()
            layoutParams.width = layoutParamsSize
            layoutParams.height = layoutParamsSize
            avatarSize.layoutParams = layoutParams
            itemView.setPadding(
                if (settings.icon_padding_x != null) (settings.icon_padding_x!! * scale).toInt() else itemView.paddingLeft,
                if (settings.icon_padding_top != null) (settings.icon_padding_top!! * scale).toInt() else itemView.paddingTop,
                if (settings.icon_padding_x != null) (settings.icon_padding_x!! * scale).toInt() else itemView.paddingRight,
                if (settings.icon_padding_bottom != null) (settings.icon_padding_bottom!! * scale).toInt() else itemView.paddingBottom
            )

            storyIndex = position
            image.setOnClickListener(this)
            name.text = story.name
            name.setTextColor(Color.parseColor(settings.label_font_color))
            name.textSize = settings.label_font_size.toFloat()
            name.typeface = settings.label_font_family
            name.width = ((if (settings.label_width != null) settings.label_width!! else settings.icon_size) * scale).toInt()

            pin.visibility = if (story.isPinned) View.VISIBLE else View.GONE
            pin.text = settings.pin_symbol
            val shapeAppearanceModel = ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, 50f).build()
            val shapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)
            shapeDrawable.fillColor = ColorStateList.valueOf(Color.parseColor(settings.background_pin))
            ViewCompat.setBackground(pin, shapeDrawable)
        }

        override fun onClick(view: View) {
            listener.onStoryClick(storyIndex)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.story_avatar, viewGroup, false)

        return ViewHolder(view, listener)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.setRow(data[position], position)
    }

    override fun getItemCount(): Int {
        return data.size
    }
}
