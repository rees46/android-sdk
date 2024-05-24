package com.personalizatio.stories;

import android.annotation.TargetApi;
import android.app.Activity;
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
import android.util.DisplayMetrics;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.Slide;
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
import com.personalizatio.OnLinkClickListener;
import com.personalizatio.Product;
import com.personalizatio.R;
import com.personalizatio.SDK;

import org.w3c.dom.Text;

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

	private int screenHeight;

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

		screenHeight = getScreenHeight(getContext());
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

	/**
	 * Обновляет данные слайда
	 *
	 * @param slide Story.Slide
	 */
	public void update(Story.Slide slide, int position, String code, int story_id) {
		slide.prepared = false;
		setBackgroundColor(slide.background_color == null ? getContext().getResources().getColor(android.R.color.black) : Color.parseColor(slide.background_color));
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
				for( Story.Slide.Element element : slide.elements ) {
					switch( element.type ) {
						case "product":
							product = element.item;
							break;
						case "button":
							link = element.link;
							break;
					}
				}
				Log.d(SDK.TAG, "open link: " + link + (product != null ? " with product: `" + product.id + "`" : ""));
				//Вызываем колбек клика
				if( stories_view.click_listener == null || product == null && stories_view.click_listener.onClick(link) || product != null && stories_view.click_listener.onClick(product) ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
				}
				SDK.track_story("click", code, story_id, slide.id);
			} catch(ActivityNotFoundException | NullPointerException e) {
				Log.e(SDK.TAG, e.getMessage(), e);
				Toast.makeText(getContext(), "Unknown error", Toast.LENGTH_SHORT).show();
			}
		});

		//Загуражем картинку
		if( slide.type.equals("image") ) {
			loadImage(slide.background, new RequestListener<>() {
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
		if( slide.type.equals("video") ) {
			video.setVisibility(VISIBLE);

			//Загружаем превью
			if( slide.preview != null ) {
				loadImage(slide.preview, new RequestListener<>() {
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

	private void onPreparedSlide(Story.Slide slide, int position) {
		slide.prepared = true;
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

	public void updateProduct(Story.Slide.Element element, RequestListener<Drawable> listener) {
		product.setVisibility(VISIBLE);
		product_brand.setVisibility(element.item.brand == null ? GONE : VISIBLE);
		product_brand.setText(element.item.brand);
		product_brand.setTypeface(stories_view.settings.font_family);
		product_name.setText(element.item.name);
		product_name.setTypeface(stories_view.settings.font_family);
		Glide.with(getContext()).load(element.item.image).listener(listener).override(Target.SIZE_ORIGINAL).into(product_image);
		product_price.setText(element.item.price);
		product_price.setTypeface(stories_view.settings.font_family);
		product_oldprice.setVisibility(element.item.oldprice == null ? GONE : VISIBLE);
		product_oldprice.setText(element.item.oldprice);
		product_oldprice.setPaintFlags(product_oldprice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		product_oldprice.setTypeface(stories_view.settings.font_family);
		product_discount_box.setVisibility(element.item.discount_percent == null && element.item.promocode == null ? GONE : VISIBLE);
		promocode_text.setText(element.title);
		promocode_text.setVisibility(element.title == null || element.item.promocode == null ? GONE : VISIBLE);
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
		if( element.item.promocode != null ) {
			product_discount.setText(element.item.promocode);
			product_price.setText(element.item.price_with_promocode);
			shape_discount_drawable.setFillColor(ContextCompat.getColorStateList(getContext(), R.color.product_promocode_color));
		} else if( element.item.discount_percent != null ) {
			product_discount.setText("-" + element.item.discount_percent + "%");
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

	private void updateHeader(Story.Slide.Element element, String slide_id, String code, int story_id) {
		if( element.type.equals("header") ) {
			header.setVisibility(VISIBLE);
			header.setOnTouchListener((View v, MotionEvent event) -> {
				if( event.getAction() == MotionEvent.ACTION_UP ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(element.link)));
					SDK.track_story("click", code, story_id, slide_id);
				}
				return true;
			});

			if( element.icon != null ) {
				titleCardView.setVisibility(VISIBLE);
				if( getContext() == null ) return;
				Glide.with(getContext()).load(element.icon).into(titleIconImageView);
			} else {
				titleCardView.setVisibility(GONE);
			}

			titleTextView.setTypeface(stories_view.settings.font_family);
			if( element.title != null ) {
				titleTextView.setVisibility(VISIBLE);
				titleTextView.setText(element.title);
			} else {
				titleTextView.setVisibility(GONE);
			}

			subtitleTextView.setTypeface(stories_view.settings.font_family);
			if( element.subtitle != null ) {
				subtitleTextView.setVisibility(VISIBLE);
				subtitleTextView.setText(element.subtitle);
			} else {
				subtitleTextView.setVisibility(GONE);
			}
		}
	}

	private void updateElements(Story.Slide slide, String code, int story_id, int position) {

		//Скрываем все элементы
		header.setVisibility(GONE);
		button.setVisibility(GONE);
		button_products.setVisibility(GONE);
		products.setVisibility(GONE);

		//Отображаем необходимые элементы
		for( Story.Slide.Element element : slide.elements ) {
			switch( element.type ) {
				case "header":
					updateHeader(element, slide.id, code, story_id);
					break;
				case "button":
					button.setVisibility(VISIBLE);
					button.setText(element.title);
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						button.setBackgroundTintList(ColorStateList.valueOf(element.background == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.background)));
					} else {
						button.setBackgroundColor(element.background == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.background));
					}
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						button.setTextColor(ColorStateList.valueOf(element.color == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.color)));
					} else {
						button.setTextColor(element.color == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.color));
					}
					button.setTypeface(Typeface.create(stories_view.settings.button_font_family, element.text_bold ? Typeface.BOLD : Typeface.NORMAL));
					break;
				case "products":
					products_adapter.setProducts(element.products, story_id, slide.id);
					button_products.setVisibility(VISIBLE);
					button_products.setText(element.label_show);
					button_products.setTypeface(stories_view.settings.products_button_font_family);
					button_products.setOnClickListener(view -> {
						button_products.setActivated(!button_products.isActivated());
						button_products.setText(button_products.isActivated() ? element.label_hide : element.label_show);

						//Анимация появления товаров
						ConstraintSet set = new ConstraintSet();
						set.clone((ConstraintLayout) elements_layout);

						Transition transition = new ChangeBounds();
						transition.addTarget(button_products.getId());
						transition.addTarget(button.getId());

						Transition transition2 = new Slide(Gravity.BOTTOM);
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
							slide.prepared = true;
							if( page_listener != null ) {
								page_listener.onPrepared(position);
							}
							return false;
						}
					});
				case "text_block":
					var textView = new TextView(getContext());
					textView.setLayoutParams(new FrameLayout.LayoutParams(
							LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.WRAP_CONTENT));

					textView.setText(element.text_input);

					var y = screenHeight * element.y_offset / 100f;
					textView.setY(y);

					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						textView.setBackgroundTintList(ColorStateList.valueOf(element.background == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.background)));
					} else {
						textView.setBackgroundColor(element.background == null ? button.getContext().getResources().getColor(R.color.primary) : Color.parseColor(element.background));
					}
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
						textView.setTextColor(ColorStateList.valueOf(element.color == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.color)));
					} else {
						textView.setTextColor(element.color == null ? button.getContext().getResources().getColor(R.color.white) : Color.parseColor(element.color));
					}

					text_blocks_layout.addView(textView);

					break;
			}
		}
	}

	public static int getScreenHeight(Context context) {
		var activity = (Activity) context;

		if( activity == null ) return 0;

		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {
			var windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
			return windowMetrics.getBounds().height();
		} else {
			var displayMetrics = new DisplayMetrics();
			activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
			return displayMetrics.heightPixels;
		}
	}
}
