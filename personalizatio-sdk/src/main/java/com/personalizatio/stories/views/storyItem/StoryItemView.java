package com.personalizatio.stories.views.storyItem;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
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
import com.google.common.base.Strings;
import com.personalizatio.Product;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.models.elements.ButtonElement;
import com.personalizatio.stories.models.elements.HeaderElement;
import com.personalizatio.stories.models.elements.ProductElement;
import com.personalizatio.stories.models.elements.ProductsElement;
import com.personalizatio.stories.models.elements.TextBlockElement;
import com.personalizatio.stories.viewAdapters.ProductsAdapter;
import com.personalizatio.stories.models.elements.Element;
import com.personalizatio.stories.models.Slide;
import com.personalizatio.stories.views.StoriesView;
import com.personalizatio.utils.ViewUtils;

@SuppressLint("ViewConstructor")
final public class StoryItemView extends ConstraintLayout {

	public interface OnPageListener {
		void onPrev();

		void onNext();

		void onPrepared(int position);

		void onLocked(boolean lock);
	}

	private ImageView image;
	private PlayerView video;
	private ConstraintLayout product;
	private LinearLayout productPriceBox;
	private LinearLayout productDiscountBox;
	private TextView productBrand;
	private TextView productName;
	private TextView productOldPrice;
	private TextView productPrice;
	private TextView productDiscount;
	private TextView promocodeText;
	private ImageView productImage;

	private OnPageListener pageListener;
	private View prev;
	private View next;
	private final StoriesView storiesView;

	//Элементы управления
	private Button button;
	private FrameLayout textBlocksLayout;
	public ImageButton reload;
	public View reload_layout;
	private TextView reloadText;
	private ConstraintLayout header;
	private TextView titleTextView;
	private TextView subtitleTextView;
	private CardView titleCardView;
	private ImageView titleIconImageView;
	private Button buttonProducts;
	private ViewGroup elementsLayout;
	private RecyclerView products;
	private ProductsAdapter productsAdapter;

	private int viewHeight;
	private int viewTopOffset;

	private final int ELEMENTS_LAYOUT_ANIMATION_DELAY = 100;
	private final int ELEMENTS_LAYOUT_ANIMATION_DURATION = 200;

	public StoryItemView(StoriesView storiesView) {
		super(storiesView.getContext());
		this.storiesView = storiesView;

		inflate(getContext(), R.layout.story_item, this);
		setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		initViews();
		setupViews();
	}

	private void initViews() {
		image = findViewById(android.R.id.background);
		video = findViewById(android.R.id.widget_frame);

		//Переключение слайда
		prev = findViewById(R.id.reverse);
		next = findViewById(R.id.skip);

		//Элементы управления
		button = findViewById(android.R.id.button1);
		textBlocksLayout = findViewById(R.id.text_blocks_layout);
		reload = findViewById(R.id.reload);
		reload_layout = findViewById(R.id.reload_layout);
		reloadText = findViewById(R.id.reload_text);
		header = findViewById(R.id.header);
		titleTextView = findViewById(R.id.title_textView);
		subtitleTextView = findViewById(R.id.subtitle_textView);
		titleIconImageView = findViewById(R.id.title_imageView);
		titleCardView = findViewById(R.id.titleCardView);
		buttonProducts = findViewById(android.R.id.button2);
		elementsLayout = findViewById(R.id.elements_layout);

		products = findViewById(android.R.id.list);

		//Товарный слайд
		product = findViewById(R.id.product);

		productPriceBox = findViewById(R.id.product_price_box);
		productBrand = findViewById(R.id.product_brand);
		productName = findViewById(R.id.product_name);
		productOldPrice = findViewById(R.id.product_oldprice);
		productPrice = findViewById(R.id.product_price);
		productDiscount = findViewById(R.id.product_discount);
		productDiscountBox = findViewById(R.id.product_discount_box);
		productImage = findViewById(R.id.product_image);
		promocodeText = findViewById(R.id.promocode_text);
	}

	private void setupViews() {
		image.setVisibility(View.GONE);
		video.setVisibility(View.GONE);

		//Переключение слайда
		prev.setOnClickListener((View) -> {
			if( pageListener != null ) {
				pageListener.onPrev();
			}
		});
		next.setOnClickListener((View) -> {
			if( pageListener != null ) {
				pageListener.onNext();
			}
		});

		productsAdapter = new ProductsAdapter(storiesView);
		products.setAdapter(productsAdapter);

		product.setVisibility(GONE);
	}

	/**
	 * Слушатель переключения слайда
	 * @param listener OnPageListener
	 */
	public void setOnPageListener(OnPageListener listener) {
		pageListener = listener;
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
	@SuppressLint("ResourceAsColor")
    public void update(Slide slide, int position, String code, int story_id) {
		slide.setPrepared(false);

		setBackgroundColor(ViewUtils.getColor(getContext(), slide.getBackgroundColor(), android.R.color.black));

		video.setVisibility(GONE);

		setupReloadView(slide, position, code, story_id);

		setButtonOnClickListener(slide, code, story_id);

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
		setupElements(slide, code, story_id, position);
	}

	public ImageView getImage() {
		return image;
	}

	public PlayerView getVideo() {
		return video;
	}

	private void onPreparedSlide(Slide slide, int position) {
		slide.setPrepared(true);
		reload_layout.setVisibility(GONE);
		elementsLayout.setVisibility(VISIBLE);
		if (!buttonProducts.isActivated() && pageListener != null) {
			pageListener.onPrepared(position);
		}
	}

	private void setButtonOnClickListener(Slide slide, String code, int storyId) {
		button.setOnClickListener(view -> {
			try {
				Product product = null;
				String link = null;
				//Сначала ищем элемент с товаром
				for (var element : slide.getElements()) {
					if (element instanceof ProductElement productElement) {
						product = productElement.getItem();
					} else if (element instanceof ButtonElement buttonElement) {
						link = buttonElement.getLink();
					}
				}
				Log.d(SDK.TAG, "open link: " + link + (product != null ? " with product: `" + product.getId() + "`" : ""));
				//Вызываем колбек клика
				var clickListener = storiesView.getClickListener();
				if( clickListener == null
						|| product == null && clickListener.onClick(link)
						|| product != null && clickListener.onClick(product) ) {
					getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
				}
				SDK.track_story("click", code, storyId, slide.getId());
			} catch(ActivityNotFoundException | NullPointerException e) {
				Log.e(SDK.TAG, e.getMessage(), e);
				Toast.makeText(getContext(), "Unknown error", Toast.LENGTH_SHORT).show();
			}
		});
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

	public void updateProduct(ProductElement element, RequestListener<Drawable> listener) {
		var item = element.getItem();

		product.setVisibility(VISIBLE);

		Glide.with(getContext()).load(item.getImage()).listener(listener).override(Target.SIZE_ORIGINAL).into(productImage);

		setupDefaultTextView(productBrand, Strings.isNullOrEmpty(item.getBrand()), item.getBrand());
		setupDefaultTextView(productName, item.getName());
		setupDefaultTextView(productPrice, item.getPrice());
		setupDefaultTextView(productOldPrice, Strings.isNullOrEmpty(item.getOldPrice()), item.getOldPrice());
		setupDefaultTextView(promocodeText, element.getTitle() != null && !Strings.isNullOrEmpty(item.getPromocode()), element.getTitle());
		productOldPrice.setPaintFlags(productOldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		productDiscountBox.setVisibility(Strings.isNullOrEmpty(item.getDiscountPercent()) && Strings.isNullOrEmpty(item.getPromocode()) ? GONE : VISIBLE);

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
		ViewCompat.setBackground(productPriceBox, shape_box_drawable);

		//Блок скидки или промокода
		var productDiscountText = "";
		int shapeDiscountColor = 0;
		if (!Strings.isNullOrEmpty(item.getPromocode())) {
			productDiscountText = item.getPromocode();
			shapeDiscountColor = R.color.product_promocode_color;
			productPrice.setText(item.getPriceWithPromocode());
		} else if (!Strings.isNullOrEmpty(item.getDiscountPercent())) {
			productDiscountText = "-" + item.getDiscountPercent() + "%";
			shapeDiscountColor = R.color.product_discount_color;
		}
		setupDefaultTextView(productDiscount, productDiscountText);
		if(shapeDiscountColor != 0) {
			shape_discount_drawable.setFillColor(ContextCompat.getColorStateList(getContext(), shapeDiscountColor));
		}

		ViewCompat.setBackground(productDiscountBox, shape_discount_drawable);
	}

	public void release() {
		video.setPlayer(null);
	}

	public void setHeadingVisibility(int visibility) {
		elementsLayout.animate().alpha(visibility == GONE ? 0 : 1).setStartDelay(visibility == GONE ? ELEMENTS_LAYOUT_ANIMATION_DELAY : 0).setDuration(ELEMENTS_LAYOUT_ANIMATION_DURATION);
	}

	private void updateHeader(HeaderElement element, String slide_id, String code, int story_id) {
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

		var title = element.getTitle();
		setupDefaultTextView(titleTextView, title != null, title);

		var subtitle = element.getSubtitle();
		setupDefaultTextView(subtitleTextView, subtitle != null, subtitle);
	}

	private void setupElements(Slide slide, String code, int story_id, int position) {
		//Скрываем все элементы
		header.setVisibility(GONE);
		button.setVisibility(GONE);
		buttonProducts.setVisibility(GONE);
		products.setVisibility(GONE);

		//Отображаем необходимые элементы
		for (Element element : slide.getElements()) {
			if (element instanceof HeaderElement headerElement) {
				updateHeader(headerElement, slide.getId(), code, story_id);
			} else if (element instanceof TextBlockElement textBlockElement) {
				updateTextBlock(textBlockElement);
			} else if (element instanceof ButtonElement buttonElement) {
				updateButton(buttonElement);
			}
			else if (element instanceof ProductsElement productsElement) {
				updateProducts(productsElement, slide.getId(), story_id);
			} else if (element instanceof ProductElement productElement) {
				updateProduct(productElement, new RequestListener<>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
						return false;
					}

					@Override
					public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
						slide.setPrepared(true);
						if( pageListener != null ) {
							pageListener.onPrepared(position);
						}
						return false;
					}
				});
			}
		}
	}

	private void updateProducts(ProductsElement element, String slideId, int storyId) {
		productsAdapter.setProducts(element.getProducts(), storyId, slideId);
		setupTextView(buttonProducts, true, element.getLabelShow(), storiesView.getSettings().products_button_font_family);
		buttonProducts.setOnClickListener(view -> {
			buttonProducts.setActivated(!buttonProducts.isActivated());
			buttonProducts.setText(buttonProducts.isActivated() ? element.getLabelHide() : element.getLabelShow());

			//Анимация появления товаров
			ConstraintSet set = new ConstraintSet();
			set.clone((ConstraintLayout) elementsLayout);

			Transition transition = new ChangeBounds();
			transition.addTarget(buttonProducts.getId());
			transition.addTarget(button.getId());

			Transition transition2 = new androidx.transition.Slide(Gravity.BOTTOM);
			transition2.addTarget(products.getId());

			TransitionSet transitions = new TransitionSet();
			transitions.addTransition(transition);
			transitions.addTransition(transition2);

			TransitionManager.beginDelayedTransition(elementsLayout, transitions);
			products.setVisibility(buttonProducts.isActivated() ? VISIBLE : GONE);
			//--->

			if( pageListener != null ) {
				pageListener.onLocked(buttonProducts.isActivated());
			}
		});
		if( pageListener != null ) {
			pageListener.onLocked(buttonProducts.isActivated());
		}
	}

	@SuppressLint("ResourceAsColor")
	private void updateButton(ButtonElement element) {
		button.setVisibility(VISIBLE);
		button.setText(element.getTitle());

		ViewUtils.setBackgroundColor(getContext(), button, element.getBackground(), R.color.primary);
		ViewUtils.setTextColor(getContext(), button, element.getColor(), R.color.white);

		button.setTypeface(Typeface.create(storiesView.getSettings().button_font_family, element.getTextBold() ? Typeface.BOLD : Typeface.NORMAL));
	}

	private void updateTextBlock(TextBlockElement element) {
		var textBlockView = new TextBlockView(getContext());

		textBlockView.updateView(element, viewHeight, viewTopOffset);

		textBlocksLayout.addView(textBlockView);
	}

	private void setupReloadView(Slide slide, int position, String code, int storyId) {
		reload_layout.setVisibility(GONE);

		var settings = storiesView.getSettings();
		setupTextView(reloadText, settings.failed_load_text, settings.failed_load_font_family);
		reloadText.setTextSize(storiesView.getSettings().failed_load_size);
		reloadText.setTextColor(Color.parseColor(storiesView.getSettings().failed_load_color));

		reload.setOnClickListener((View) -> update(slide, position, code, storyId));
	}

	private void setupDefaultTextView(TextView textView, boolean visibility, String text) {
		setupTextView(textView, visibility, text, storiesView.getSettings().font_family);
	}

	private void setupTextView(TextView textView, boolean visibility, String text, Typeface typeface) {
		textView.setVisibility(visibility ? VISIBLE : GONE);

		if (visibility) {
			setupTextView(textView, text, typeface);
		}
	}

	private void setupDefaultTextView(TextView textView, String text) {
		setupTextView(textView, text, storiesView.getSettings().font_family);
	}

	private void setupTextView(TextView textView, String text, Typeface typeface) {
		textView.setText(text);
		textView.setTypeface(typeface);
	}
}
