package com.personalizatio.stories;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.FontRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.personalizatio.Product;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.models.Element;
import com.personalizatio.stories.models.Slide;
import com.personalizatio.stories.models.Story;


final class StoryItemView extends ConstraintLayout {

	public interface OnPageListener {
		void onPrev();

		void onNext();

		void onPrepared(int position);

		void onLocked(boolean lock);
	}

	public ImageView image;
	public PlayerView video;
	private ConstraintLayout product;
	private LinearLayout product_price_box;
	private LinearLayout product_discount_box;
	private TextView product_brand;
	private TextView product_name;
	private TextView product_oldprice;
	private TextView product_price;
	private TextView product_discount;
	private TextView promocode_text;
	private ImageView product_image;

	private OnPageListener page_listener;
	private View prev;
	private View next;
	private StoriesView stories_view;

	//Элементы управления
	private Button button;
	private FrameLayout text_blocks_layout;
	public ImageButton reload;
	public View reload_layout;
	private TextView reload_text;
	private ConstraintLayout header;
	private TextView titleTextView, subtitleTextView;
	private CardView titleCardView;
	private ImageView titleIconImageView;
	private Button button_products;
	private ViewGroup elements_layout;
	private RecyclerView products;
	private ProductsAdapter products_adapter;

	private int viewHeight;
	private int viewTopOffset;

	public StoryItemView(@NonNull Context context) {
		super(context);
	}

	public StoryItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public StoryItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public StoryItemView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		init();
	}

	private void init() {
		image = findViewById(android.R.id.background);
		video = findViewById(android.R.id.widget_frame);
		image.setVisibility(View.GONE);
		video.setVisibility(View.GONE);

		//Переключение слайда
		prev = findViewById(R.id.reverse);
		next = findViewById(R.id.skip);
		prev.setOnClickListener((View) -> {
			if( page_listener != null ) {
				page_listener.onPrev();
			}
		});
		next.setOnClickListener((View) -> {
			if( page_listener != null ) {
				page_listener.onNext();
			}
		});

		//Элементы управления
		button = findViewById(android.R.id.button1);
		text_blocks_layout = findViewById(R.id.text_blocks_layout);
		reload = findViewById(R.id.reload);
		reload_layout = findViewById(R.id.reload_layout);
		reload_text = findViewById(R.id.reload_text);
		header = findViewById(R.id.header);
		titleTextView = findViewById(R.id.title_textView);
		subtitleTextView = findViewById(R.id.subtitle_textView);
		titleIconImageView = findViewById(R.id.title_imageView);
		titleCardView = findViewById(R.id.titleCardView);
		button_products = findViewById(android.R.id.button2);
		elements_layout = findViewById(R.id.elements_layout);

		products_adapter = new ProductsAdapter();
		products = findViewById(android.R.id.list);
		products.setAdapter(products_adapter);

		//Товарный слайд
		product = findViewById(R.id.product);
		product.setVisibility(GONE);

		product_price_box = findViewById(R.id.product_price_box);
		product_brand = findViewById(R.id.product_brand);
		product_name = findViewById(R.id.product_name);
		product_oldprice = findViewById(R.id.product_oldprice);
		product_price = findViewById(R.id.product_price);
		product_discount = findViewById(R.id.product_discount);
		product_discount_box = findViewById(R.id.product_discount_box);
		product_image = findViewById(R.id.product_image);
		promocode_text = findViewById(R.id.promocode_text);
	}

	/**
	 * Слушатель переключения слайда
	 * @param listener OnPageListener
	 */
	public void setOnPageListener(OnPageListener listener) {
		page_listener = listener;
	}

	/**
	 * Слушатель клика по ссылке
	 * @param view StoriesView
	 */
	public void setStoriesView(StoriesView view) {
		stories_view = view;
		products_adapter.setStoriesView(view);
	}

	public void setOnTouchListener(OnTouchListener listener) {
		prev.setOnTouchListener(listener);
		next.setOnTouchListener(listener);
		super.setOnTouchListener((v, event) -> true);
	}

	public void setViewSize(int height, int topOffset) {
		viewHeight = height;
		viewTopOffset = topOffset;
	}

	/**
	 * Обновляет данные слайда
	 *
	 * @param slide Story.Slide
	 */
	public void update(Slide slide, int position, String code, int story_id) {
		slide.setPrepared(false);
		var backgroundColor = slide.getBackgroundColor();
		setBackgroundColor(backgroundColor == null ? getContext().getResources().getColor(android.R.color.black) : Color.parseColor(backgroundColor));
		video.setVisibility(GONE);
		reload_layout.setVisibility(GONE);
		reload_text.setTypeface(stories_view.settings.failed_load_font_family);
		reload_text.setTextSize(stories_view.settings.failed_load_size);
		reload_text.setText(stories_view.settings.failed_load_text);
		reload_text.setTextColor(Color.parseColor(stories_view.settings.failed_load_color));

		reload.setOnClickListener((View) -> {
			update(slide, position, code, story_id);
		});

		//Вызываем клик по кнопке
		button.setOnClickListener(view -> {
			try {
				Product product = null;
				String link = null;
				//Сначала ищем элемент с товаром
				for (var element : slide.getElements()) {
					switch( element.getType() ) {
						case "product":
							product = element.getItem();
							break;
						case "button":
							link = element.getLink();
							break;
					}
				}
				Log.d(SDK.TAG, "open link: " + link + (product != null ? " with product: `" + product.id + "`" : ""));
				//Вызываем колбек клика
				if( stories_view.click_listener == null || product == null && stories_view.click_listener.onClick(link) || product != null && stories_view.click_listener.onClick(product) ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
				}
				SDK.track_story("click", code, story_id, slide.getId());
			} catch(ActivityNotFoundException | NullPointerException e) {
				Log.e(SDK.TAG, e.getMessage(), e);
				Toast.makeText(getContext(), "Unknown error", Toast.LENGTH_SHORT).show();
			}
		});

		//Загуражем картинку
		if( slide.getType().equals("image") ) {
			loadImage(slide.getBackground(), new RequestListener<>() {
				@Override
				public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
					reload_layout.setVisibility(VISIBLE);
					return false;
				}

				@Override
				public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
					onPreparedSlide(slide, position);
					return false;
				}
			});
		}

		//Загружаем видео
		if (slide.getType().equals("video")) {
			video.setVisibility(VISIBLE);

			//Загружаем превью
			var preview = slide.getPreview();
			if (preview != null) {
				loadImage(preview, new RequestListener<>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
						return false;
					}

					@Override
					public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
						return false;
					}
				});
			}
		}

		//Обновляем элементы слайда
		updateElements(slide, code, story_id, position);
	}

	private void onPreparedSlide(Slide slide, int position) {
		slide.setPrepared(true);
		reload_layout.setVisibility(GONE);
		elements_layout.setVisibility(VISIBLE);
		if( !button_products.isActivated() && page_listener != null ) {
			page_listener.onPrepared(position);
		}
	}

	/**
	 * Загружает изображение слайда
	 *
	 * @param url      String
	 * @param listener RequestListener<Drawable>
	 */
	private void loadImage(String url, RequestListener<Drawable> listener) {
		image.setVisibility(View.VISIBLE);
		Glide.with(getContext()).load(url).listener(listener).into(image);
	}

	public void updateProduct(Element element, RequestListener<Drawable> listener) {
		var item = element.getItem();
		product.setVisibility(VISIBLE);
		product_brand.setVisibility(item.brand == null ? GONE : VISIBLE);
		product_brand.setText(item.brand);
		product_brand.setTypeface(stories_view.settings.font_family);
		product_name.setText(item.name);
		product_name.setTypeface(stories_view.settings.font_family);
		Glide.with(getContext()).load(item.image).listener(listener).override(Target.SIZE_ORIGINAL).into(product_image);
		product_price.setText(item.price);
		product_price.setTypeface(stories_view.settings.font_family);
		product_oldprice.setVisibility(item.oldprice == null ? GONE : VISIBLE);
		product_oldprice.setText(item.oldprice);
		product_oldprice.setPaintFlags(product_oldprice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		product_oldprice.setTypeface(stories_view.settings.font_family);
		product_discount_box.setVisibility(item.discount_percent == null && item.promocode == null ? GONE : VISIBLE);
		promocode_text.setText(element.getTitle());
		promocode_text.setVisibility(element.getTitle() == null || item.promocode == null ? GONE : VISIBLE);
		promocode_text.setTypeface(stories_view.settings.font_family);

		//Указываем закругления
		float radius = getResources().getDimension(R.dimen.product_price_box_radius);
		ShapeAppearanceModel shape_box = new ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, radius).build();
		MaterialShapeDrawable shape_box_drawable = new MaterialShapeDrawable(shape_box);
		ShapeAppearanceModel shape_discount = new ShapeAppearanceModel().toBuilder()
				.setTopRightCorner(CornerFamily.ROUNDED, radius)
				.setBottomRightCorner(CornerFamily.ROUNDED, radius)
				.build();
		MaterialShapeDrawable shape_discount_drawable = new MaterialShapeDrawable(shape_discount);

		//Заполняем
		shape_box_drawable.setFillColor(ContextCompat.getColorStateList(getContext(), android.R.color.white));
		ViewCompat.setBackground(product_price_box, shape_box_drawable);

		//Блок скидки или промокода
		product_discount.setTypeface(stories_view.settings.font_family);
		if( item.promocode != null ) {
			product_discount.setText(item.promocode);
			product_price.setText(item.price_with_promocode);
			shape_discount_drawable.setFillColor(ContextCompat.getColorStateList(getContext(), R.color.product_promocode_color));
		} else if( item.discount_percent != null ) {
			product_discount.setText("-" + item.discount_percent + "%");
			shape_discount_drawable.setFillColor(ContextCompat.getColorStateList(getContext(), R.color.product_discount_color));
		}

		ViewCompat.setBackground(product_discount_box, shape_discount_drawable);
	}

	public void release() {
		video.setPlayer(null);
	}

	public void setHeadingVisibility(int visibility) {
		elements_layout.animate().alpha(visibility == GONE ? 0 : 1).setStartDelay(visibility == GONE ? 100 : 0).setDuration(200);
	}

	private void updateHeader(Element element, String slide_id, String code, int story_id) {
		if( element.getType().equals("header") ) {
			header.setVisibility(VISIBLE);
			header.setOnTouchListener((View v, MotionEvent event) -> {
				if( event.getAction() == MotionEvent.ACTION_UP ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.getLink())));
					SDK.track_story("click", code, story_id, slide_id);
				}
				return true;
			});

			if( element.getIcon() != null ) {
				titleCardView.setVisibility(VISIBLE);
				if( getContext() == null ) return;
				Glide.with(getContext()).load(element.getIcon()).into(titleIconImageView);
			} else {
				titleCardView.setVisibility(GONE);
			}

			titleTextView.setTypeface(stories_view.settings.font_family);
			if( element.getTitle() != null ) {
				titleTextView.setVisibility(VISIBLE);
				titleTextView.setText(element.getTitle());
			} else {
				titleTextView.setVisibility(GONE);
			}

			subtitleTextView.setTypeface(stories_view.settings.font_family);
			if( element.getSubtitle() != null ) {
				subtitleTextView.setVisibility(VISIBLE);
				subtitleTextView.setText(element.getSubtitle());
			} else {
				subtitleTextView.setVisibility(GONE);
			}
		}
	}

	private void updateElements(Slide slide, String code, int story_id, int position) {

		//Скрываем все элементы
		header.setVisibility(GONE);
		button.setVisibility(GONE);
		button_products.setVisibility(GONE);
		products.setVisibility(GONE);

		//Отображаем необходимые элементы
		for (Element element : slide.getElements() ) {
			switch( element.getType() ) {
				case "header":
					updateHeader(element, slide.getId(), code, story_id);
					break;
				case "button":
					button.setVisibility(VISIBLE);
					button.setText(element.getTitle());
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						button.setBackgroundTintList(ColorStateList.valueOf(element.getBackground() == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.getBackground())));
					} else {
						button.setBackgroundColor(element.getBackground() == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.getBackground()));
					}
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						button.setTextColor(ColorStateList.valueOf(element.getColor() == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.getColor())));
					} else {
						button.setTextColor(element.getColor() == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.getColor()));
					}
					button.setTypeface(Typeface.create(stories_view.settings.button_font_family, element.getTextBold() ? Typeface.BOLD : Typeface.NORMAL));
					break;
				case "products":
					products_adapter.setProducts(element.getProducts(), story_id, slide.getId());
					button_products.setVisibility(VISIBLE);
					button_products.setText(element.getLabelShow());
					button_products.setTypeface(stories_view.settings.products_button_font_family);
					button_products.setOnClickListener(view -> {
						button_products.setActivated(!button_products.isActivated());
						button_products.setText(button_products.isActivated() ? element.getLabelHide() : element.getLabelShow());

						//Анимация появления товаров
						ConstraintSet set = new ConstraintSet();
						set.clone((ConstraintLayout) elements_layout);

						Transition transition = new ChangeBounds();
						transition.addTarget(button_products.getId());
						transition.addTarget(button.getId());

						Transition transition2 = new androidx.transition.Slide(Gravity.BOTTOM);
						transition2.addTarget(products.getId());

						TransitionSet transitions = new TransitionSet();
						transitions.addTransition(transition);
						transitions.addTransition(transition2);

						TransitionManager.beginDelayedTransition(elements_layout, transitions);
						products.setVisibility(button_products.isActivated() ? VISIBLE : GONE);
						//--->

						if( page_listener != null ) {
							page_listener.onLocked(button_products.isActivated());
						}
					});
					if( page_listener != null ) {
						page_listener.onLocked(button_products.isActivated());
					}
					break;

				case "product":
					updateProduct(element, new RequestListener<>() {
						@Override
						public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
							return false;
						}

						@Override
						public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
							slide.setPrepared(true);
							if( page_listener != null ) {
								page_listener.onPrepared(position);
							}
							return false;
						}
					});
				case "text_block":
					var textView = new TextView(getContext());

					var layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
					textView.setLayoutParams(layoutParams);

					textView.setText(element.getTextInput());

					var fontSize = element.getFontSize();

					textView.setPadding(2, fontSize, 2, fontSize);

					textView.setY(viewHeight * element.getYOffset() / 100f + viewTopOffset);

					textView.setTextSize(fontSize);

					var typeface = ResourcesCompat.getFont(getContext(), getFontRes(element.getFontType(), element.getTextBold(), element.getTextItalic()));
					textView.setTypeface(typeface, getTypefaceStyle(element.getTextBold(), element.getTextItalic()));

					textView.setTextAlignment(getTextAlignment(element.getTextAlign()));

					textView.setLineSpacing(textView.getLineHeight(), (float)element.getTextLineSpacing());

					setTextBackgroundColor(textView, element.getTextBackgroundColor(), element.getTextBackgroundColorOpacity());
					setTextColor(textView, element.getTextColor());

					text_blocks_layout.addView(textView);
			}
		}
	}

	private @FontRes int getFontRes(String fontType, boolean bold, boolean italic) {
		switch( fontType ) {
            case "serif": {
				if( bold && italic ) return R.font.droid_serif_bold_italic;
				if( bold ) return R.font.droid_serif_bold;
				if( italic ) return R.font.droid_serif_italic;
				return R.font.droid_serif_regular;
			}
			case "sans-serif": {
				if( bold ) return R.font.droid_sans_bold;
				return R.font.droid_sans_regular;
			}
			case "monospaced":
			default: {
				return R.font.droid_sans_mono;
			}
		}
	}

	private static int getTypefaceStyle(boolean bold, boolean italic) {
		if( bold && italic ) return Typeface.BOLD_ITALIC;
		if( bold ) return Typeface.BOLD;
		if( italic ) return Typeface.ITALIC;
		return Typeface.NORMAL;
	}

	private static int getTextAlignment(String textAlign) {
		return switch (textAlign) {
			case "center" -> View.TEXT_ALIGNMENT_CENTER;
			case "right" -> View.TEXT_ALIGNMENT_TEXT_END;
			default -> View.TEXT_ALIGNMENT_TEXT_START;
		};
	}

	private static void setTextColor(TextView textView, String colorString) {
		if( !colorString.startsWith("#") ) {
			colorString = "#FFFFFF";
		}

		var color = Color.parseColor(colorString);

		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			textView.setTextColor(ColorStateList.valueOf(color));
		} else {
			textView.setTextColor(color);
		}
	}

	private static void setTextBackgroundColor(TextView textView, String colorString, String colorOpacityString) {
		if( !colorString.startsWith("#") ) {
			colorString = "#FFFFFF";
		}

		var colorOpacity = GetColorOpacity(colorOpacityString);
		var colorOpacityValueString = Integer.toString(colorOpacity,16);
		if( colorOpacityValueString.length() == 1) colorOpacityValueString = 0 + colorOpacityValueString;

		var fullColorString = colorString.replace("#", "#" + colorOpacityValueString);

		textView.setBackgroundColor(Color.parseColor(fullColorString));
	}

	private static int GetColorOpacity(String percentsString) {
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

		return 255 * percents / 100;
	}
}
