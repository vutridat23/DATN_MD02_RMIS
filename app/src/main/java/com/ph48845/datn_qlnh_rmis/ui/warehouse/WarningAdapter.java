package com.ph48845.datn_qlnh_rmis.ui.warehouse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Ingredient;

import java.util.List;

/**
 * Adapter cho danh sách cảnh báo nguyên liệu
 */
public class WarningAdapter extends RecyclerView.Adapter<WarningAdapter.ViewHolder> {
    private List<Ingredient> ingredients;

    public WarningAdapter(List<Ingredient> ingredients) {
        this.ingredients = ingredients;
    }

    public void updateList(List<Ingredient> newList) {
        this.ingredients = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warning, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Ingredient ingredient = ingredients.get(position);
        holder.bind(ingredient);
    }

    @Override
    public int getItemCount() {
        return ingredients != null ? ingredients.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIngredientName, tvCategory, tvQuantity, tvThreshold, tvStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvIngredientName = itemView.findViewById(R.id.tvIngredientName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvThreshold = itemView.findViewById(R.id.tvThreshold);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(Ingredient ingredient) {
            tvIngredientName.setText(ingredient.getName());
            tvCategory.setText("Loại: " + ingredient.getCategoryText());
            tvQuantity.setText("Số lượng: " + ingredient.getQuantity() + " " + ingredient.getUnit());
            tvThreshold.setText("Ngưỡng tối thiểu: " + ingredient.getMinThreshold() + " " + ingredient.getUnit());

            // Set status with color
            tvStatus.setText(ingredient.getStatusText());
            if ("out_of_stock".equals(ingredient.getStatus())) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_error);
            } else if ("low_stock".equals(ingredient.getStatus())) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_warning);
            } else {
                tvStatus.setBackgroundResource(R.drawable.bg_status_ok);
            }
        }
    }
}
