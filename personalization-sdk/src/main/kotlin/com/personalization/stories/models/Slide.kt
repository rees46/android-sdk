package com.personalization.stories.models

import com.personalization.stories.models.elements.ButtonElement
import com.personalization.stories.models.elements.Element
import com.personalization.stories.models.elements.HeaderElement
import com.personalization.stories.models.elements.ProductElement
import com.personalization.stories.models.elements.ProductsElement
import com.personalization.stories.models.elements.TextBlockElement
import org.json.JSONObject
import java.io.Serializable
import java.util.Objects

class Slide(json: JSONObject) : Serializable {
    val id: String = json.optString("id", "")
    val background: String = json.optString("background", "")
    val backgroundColor: String = json.optString("background_color", DEFAULT_BACKGROUND_COLOR)
    val preview: String = json.optString("preview", "")
    val type: String = json.optString("type", "")
    var duration: Long = json.optLong("duration", DEFAULT_DURATION_SECONDS.toLong()) * 1000L
    private val elements: MutableList<Element> = ArrayList()
    var isPrepared: Boolean = false

    init {
        val elementsJsonArray = json.optJSONArray("elements")
        if (elementsJsonArray != null) {
            for (i in 0 until elementsJsonArray.length()) {
                val element = createElement(elementsJsonArray.optJSONObject(i))
                if (element != null) {
                    elements.add(element)
                }
            }
        }
    }

    fun getElements(): List<Element> {
        return elements
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Slide) return false
        return duration == other.duration
                && isPrepared == other.isPrepared
                && id == other.id
                && background == other.background
                && backgroundColor == other.backgroundColor
                && preview == other.preview
                && type == other.type
                && elements == other.elements
    }

    override fun hashCode(): Int {
        return Objects.hash(id, background, backgroundColor, preview, type, elements, duration, isPrepared)
    }

    override fun toString(): String {
        return "Slide{id='$id', background='$background', backgroundColor='$backgroundColor', preview='$preview', type='$type', elements=$elements, duration=$duration, prepared=$isPrepared}"
    }

    companion object {
        private const val DEFAULT_DURATION_SECONDS = 5
        private const val DEFAULT_BACKGROUND_COLOR = "#000000"

        private fun createElement(json: JSONObject): Element? {
            val type = json.optString("type", "")

            return when (type) {
                "text_block" -> TextBlockElement(json)
                "header" -> HeaderElement(json)
                "products" -> ProductsElement(json)
                "product" -> ProductElement(json)
                "button" -> ButtonElement(json)
                else -> null
            }
        }
    }
}
