package com.personalizatio.stories.viewAdapters;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.common.base.Strings;
import com.personalizatio.Product;
import com.personalizatio.R;
import com.personalizatio.SDK;
import com.personalizatio.stories.views.StoriesView;

import java.util.ArrayList;
import java.util.List;

final public class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.ViewHolder> {

	private List<Product> products = new ArrayList<>();
	private int storyId;
	private String slideId;
	private final StoriesView storiesView;

	public ProductsAdapter(StoriesView view) {
		storiesView = view;
	}

	public void setProducts(List<Product> products, int storyId, String slideId) {
		this.products = products;
		this.storyId = storyId;
		this.slideId = slideId;
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.product, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		holder.bind(products.get(position));
	}

	@Override
	public int getItemCount() {
		return products.size();
	}

	class ViewHolder extends RecyclerView.ViewHolder {

		public final ImageView image;
		public final TextView name;
		public final TextView oldprice;
		public final TextView discount;
		public final TextView price;

		public ViewHolder(@NonNull View view) {
			super(view);
			image = view.findViewById(android.R.id.icon);
			name = view.findViewById(android.R.id.text1);
			oldprice = view.findViewById(R.id.oldprice);
			oldprice.setPaintFlags(oldprice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			discount = view.findViewById(R.id.discount);
			price = view.findViewById(R.id.price);
		}

		public void bind(Product product) {
			Glide.with(image.getContext()).load(product.getImage()).into(image);
			name.setText(product.getName());
			var settings = storiesView.getSettings();
			name.setTypeface(settings.font_family);
			discount.setTypeface(settings.font_family);
			oldprice.setTypeface(settings.font_family);
			price.setTypeface(settings.font_family);
			if (Strings.isNullOrEmpty(product.getOldPrice())) {
				discount.setVisibility(View.GONE);
				oldprice.setVisibility(View.GONE);
			} else {
				discount.setVisibility(View.VISIBLE);
				oldprice.setVisibility(View.VISIBLE);
				oldprice.setText(product.getOldPrice());
				discount.setText((String.format("-%s%%", product.getDiscount())));
			}
			price.setText(product.getPrice());
			itemView.setOnClickListener(view -> {
				Log.d(SDK.TAG, "click: " + product.getName() + ", " + product.getUrl());
				try {
					if( storiesView.getClickListener() == null || storiesView.getClickListener().onClick(product) ) {
						itemView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Strings.isNullOrEmpty(product.getDeeplink()) ? product.getUrl() : product.getDeeplink())));
					}
					SDK.track_story("click", storiesView.getCode(), storyId, slideId);
				} catch(ActivityNotFoundException e) {
					Log.e(SDK.TAG, e.getMessage(), e);
					Toast.makeText(itemView.getContext(), "Unknown error", Toast.LENGTH_SHORT).show();
				}
			});
		}
	}
}
