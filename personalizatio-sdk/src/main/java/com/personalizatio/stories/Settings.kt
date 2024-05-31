package com.personalizatio.stories

import android.graphics.Typeface

class Settings {
    enum class ICON_DISPLAY_FORMAT {
        CIRCLE,
        RECTANGLE
    }

    //Основной блок сторисов
    //Иконка сторисов
    var label_font_color: String = "#212529"
    var icon_size: Int = 60
    var label_font_family: Typeface? = null
    var label_font_size: Int = 13
    var label_width: Int? = null
    var icon_padding_x: Int? = null
    var icon_padding_top: Int? = null
    var icon_padding_bottom: Int? = null
    var visited_campaign_transparency: Float = 1f
    var new_campaign_border_color: String = "#FD7C50"
    var visited_campaign_border_color: String = "#FDC2A1"
    var background_pin: String = "#FD7C50"
    var pin_symbol: String = "📌"
    var icon_display_format: ICON_DISPLAY_FORMAT = ICON_DISPLAY_FORMAT.CIRCLE

    //Просмотр сториса
	var close_color: String = "#ffffff"
	var font_family: Typeface? = null
    var button_font_family: Typeface? = null
    var products_button_font_family: Typeface? = null
	var background_progress: String = "#ffffff"

    //Описание ошибки
	var failed_load_text: String? = null
    var failed_load_color: String = "#ffffff"
    var failed_load_size: Int = 13
    var failed_load_font_family: Typeface? = null
}
