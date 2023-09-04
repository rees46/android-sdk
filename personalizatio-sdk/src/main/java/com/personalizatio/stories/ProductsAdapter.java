package com.personalizatio.stories;

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
import com.personalizatio.R;
import com.personalizatio.SDK;

import java.util.ArrayList;

final class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.ViewHolder> {

	private ArrayList<Product> products = new ArrayList<>();
	private String code;
	private int story_id;
	private String slide_id;

	public void setProducts(ArrayList<Product> products, String code, int story_id, String slide_id) {
		this.products = products;
		this.code = code;
		this.story_id = story_id;
		this.slide_id = slide_id;
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
			Glide.with(image.getContext()).load(product.image).into(image);
			name.setText(product.name);
			if( product.oldprice == null ) {
				discount.setVisibility(View.GONE);
				oldprice.setVisibility(View.GONE);
			} else {
				discount.setVisibility(View.VISIBLE);
				oldprice.setVisibility(View.VISIBLE);
				oldprice.setText(product.oldprice);
				discount.setText((String.format("-%s%%", product.discount)));
			}
			price.setText(product.price);
			itemView.setOnClickListener(view -> {
				Log.d(SDK.TAG, "click: " + product.name + ", " + product.url);
				try {
					itemView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(product.url)));
					SDK.track_story("click", code, story_id, slide_id);
				} catch(ActivityNotFoundException e) {
					Log.e(SDK.TAG, e.getMessage(), e);
					Toast.makeText(itemView.getContext(), "Unknown error", Toast.LENGTH_SHORT).show();
				}
			});
		}
	}
}
