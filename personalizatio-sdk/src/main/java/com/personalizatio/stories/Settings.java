package com.personalizatio.stories;

import android.graphics.Typeface;

final public class Settings {

	public enum ICON_DISPLAY_FORMAT {
		CIRCLE,
		RECTANGLE
	}

	//Основной блок сторисов

	//Иконка сторисов
	public String label_font_color = "#212529";
	public int icon_size = 60;
	public Typeface label_font_family;
	public int label_font_size = 13;
	public Integer label_width;
	public Integer icon_padding_x;
	public Integer icon_padding_top;
	public Integer icon_padding_bottom;
	public float visited_campaign_transparency = 1;
	public String new_campaign_border_color = "#FD7C50";
	public String visited_campaign_border_color = "#FDC2A1";
	public String background_pin = "#FD7C50";
	public String pin_symbol = "📌";
	public ICON_DISPLAY_FORMAT icon_display_format = ICON_DISPLAY_FORMAT.CIRCLE;

	//Просмотр сториса
	public String close_color = "#ffffff";
	public Typeface font_family;
	public Typeface button_font_family;
	public Typeface products_button_font_family;
	public String background_progress = "#ffffff";

	//Описание ошибки
	public String failed_load_text;
	public String failed_load_color = "#ffffff";
	public int failed_load_size = 13;
	public Typeface failed_load_font_family;
}
