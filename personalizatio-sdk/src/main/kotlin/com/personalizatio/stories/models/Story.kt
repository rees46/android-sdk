package com.personalizatio.stories.models

import org.json.JSONObject
import java.util.Objects

class Story(json: JSONObject) {
    val id: Int = json.optInt("id", 0)
    val avatar: String = json.optString("avatar", "")
    val name: String = json.optString("name", "")
    var isViewed: Boolean = json.optBoolean("viewed", false)
    val isPinned: Boolean = json.optBoolean("pinned", false)
    var startPosition: Int = json.optInt("start_position", 0)
    private val slides: MutableList<Slide> = ArrayList()

    init {
        val slidesJsonArray = json.optJSONArray("slides")
        if (slidesJsonArray != null) {
            for (i in 0 until slidesJsonArray.length()) {
                slides.add(Slide(slidesJsonArray.optJSONObject(i)))
            }
        }
    }

    fun getSlide(position: Int): Slide {
        return slides[position]
    }

    val slidesCount: Int
        get() = slides.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Story) return false
        return id == other.id
                && isViewed == other.isViewed
                && isPinned == other.isPinned
                && startPosition == other.startPosition
                && avatar == other.avatar
                && name == other.name
                && slides == other.slides
    }

    override fun hashCode(): Int {
        return Objects.hash(id, avatar, name, isViewed, isPinned, startPosition, slides)
    }

    override fun toString(): String {
        return "Story{id='$id', avatar='$avatar', name='$name', viewed=$isViewed, pinned=$isPinned, startPosition=$startPosition, slides=$slides}"
    }
}
