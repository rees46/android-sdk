package com.personalizatio.stories.views.storyItem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.FontRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import com.personalizatio.R;
import com.personalizatio.stories.models.elements.TextBlockElement;
import com.personalizatio.utils.ViewUtils;

final public class TextBlockView extends androidx.appcompat.widget.AppCompatTextView {

	public TextBlockView(@NonNull Context context) {
		super(context);
	}

	@SuppressLint("ResourceAsColor")
	public void updateView(TextBlockElement element, int parentHeight, int parentTopOffset) {
		var layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		setLayoutParams(layoutParams);

		setText(element.getTextInput());

		var fontSize = element.getFontSize();

		setPadding(0, fontSize, 0, fontSize);

		setY(parentHeight * element.getYOffset() / 100f + parentTopOffset);

		setTextSize(fontSize);

		var typeface = ResourcesCompat.getFont(getContext(), getFontRes(element.getFontType(), element.isBold(), element.isItalic()));
		setTypeface(typeface, ViewUtils.getTypefaceStyle(element.isBold(), element.isItalic()));

		setTextAlignment(getTextAlignment(element.getTextAlign()));

		setLineSpacing(getLineHeight(), (float)element.getTextLineSpacing());

		setBackgroundColor(element.getTextBackgroundColor(), element.getTextBackgroundColorOpacity());
		ViewUtils.setTextColor(getContext(), this, element.getTextColor(), R.color.white);
	}

	private @FontRes int getFontRes(String fontType, boolean bold, boolean italic) {
		switch (fontType) {
            case "serif": {
				if (bold && italic) return R.font.droid_serif_bold_italic;
				if (bold) return R.font.droid_serif_bold;
				if (italic) return R.font.droid_serif_italic;
				return R.font.droid_serif_regular;
			}
			case "sans-serif": {
				if (bold) return R.font.droid_sans_bold;
				return R.font.droid_sans_regular;
			}
			case "monospaced":
			default: {
				return R.font.droid_sans_mono;
			}
		}
	}

	private static int getTextAlignment(String textAlign) {
		return switch (textAlign) {
			case "center" -> View.TEXT_ALIGNMENT_CENTER;
			case "right" -> View.TEXT_ALIGNMENT_TEXT_END;
			default -> View.TEXT_ALIGNMENT_TEXT_START;
		};
	}

	@SuppressLint("ResourceAsColor")
	private void setBackgroundColor(String colorString, String colorOpacityString) {
		if( !colorString.startsWith("#") ) {
			colorString = "#FFFFFF";
		}

		var colorOpacity = GetColorOpacity(colorOpacityString);
		var colorOpacityValueString = Integer.toString(colorOpacity,16);
		if( colorOpacityValueString.length() == 1) colorOpacityValueString = 0 + colorOpacityValueString;

		var fullColorString = colorString.replace("#", "#" + colorOpacityValueString);

		var color = ViewUtils.getColor(getContext(), fullColorString, R.color.white);
		setBackgroundColor(color);
	}

	private int GetColorOpacity(String percentsString) {
		var percents = 0;

		try {
			percents = Integer.parseInt(percentsString);
		}
		catch( NumberFormatException e ) {
			try {
				if (!percentsString.isEmpty()) {
					percents = Integer.parseInt(percentsString.substring(0, percentsString.length() - 1));
				}
			}
			catch( NumberFormatException ignored) {
			}
		}

		return ViewUtils.MAX_COLOR_CHANNEL_VALUE * percents / 100;
	}
}
