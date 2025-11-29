package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Ingredient;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class IngredientAdapter extends RecyclerView.Adapter<IngredientAdapter.VH> {

    public interface OnTakeClickListener {
        void onTake(Ingredient ingredient, double amount, int position);
    }

    private final List<Ingredient> items = new ArrayList<>();
    private final OnTakeClickListener listener;
    private final Context context;
    private final DecimalFormat qtyFormat = new DecimalFormat("#.##");

    public IngredientAdapter(Context ctx, OnTakeClickListener listener) {
        this.listener = listener;
        this.context = ctx;
    }

    public void setItems(List<Ingredient> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void updateItem(int position, Ingredient newItem) {
        if (position >= 0 && position < items.size()) {
            items.set(position, newItem);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ingredient, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Ingredient it = items.get(position);
        holder.name.setText(it.getName() != null ? it.getName() : "—");

        String qtyText = qtyFormat.format(it.getQuantity());
        holder.qty.setText(String.format("Số lượng: %s", qtyText));

        String status = it.getStatus() != null ? it.getStatus() : "";

        if ("out_of_stock".equals(status) || it.getQuantity() <= 0.0) {
            holder.status.setText("TRẠNG THÁI: HẾT NGUYÊN LIỆU");
            holder.status.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
            holder.amountInput.setEnabled(false);
            holder.takeBtn.setEnabled(false);
        } else if ("low_stock".equals(status)) {
            holder.status.setText("TRẠNG THÁI: SẮP HẾT");
            holder.status.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            holder.amountInput.setEnabled(true);
            holder.takeBtn.setEnabled(true);
        } else {
            holder.status.setText("TRẠNG THÁI: CÒN HÀNG");
            holder.status.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.amountInput.setEnabled(true);
            holder.takeBtn.setEnabled(true);
        }

        // Load image using Glide; use android placeholder if image missing
        String imageUrl = it.getImage();
        RequestOptions options = new RequestOptions().centerCrop();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            Glide.with(context)
                    .load(imageUrl)
                    .apply(options)
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(holder.iv);
        } else {
            Glide.with(context)
                    .load(android.R.drawable.ic_menu_report_image)
                    .into(holder.iv);
        }

        // default text for amount
        holder.amountInput.setText("1");
        holder.takeBtn.setOnClickListener(v -> {
            String txt = holder.amountInput.getText().toString().trim();
            double amount;
            try {
                if (txt.isEmpty()) throw new NumberFormatException();
                amount = Double.parseDouble(txt);
                if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0.0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Nhập số lượng hợp lệ (>0)", Toast.LENGTH_SHORT).show();
                return;
            }

            // Client-side check: nếu amount > quantity hiện có => thông báo và không gọi API
            double available = it.getQuantity();
            if (amount > available + 1e-9) {
                String availText = qtyFormat.format(available);
                Toast.makeText(context,
                        "Không đủ nguyên liệu. Chỉ còn " + availText + ". Vui lòng nhập lại hoặc nhập kho.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // disable controls to avoid double click; Activity will re-enable after response or adapter update
            holder.takeBtn.setEnabled(false);
            holder.amountInput.setEnabled(false);
            listener.onTake(it, amount, position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView name, qty, status;
        EditText amountInput;
        Button takeBtn;

        public VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.ivIngredient);
            name = itemView.findViewById(R.id.tvIngredientName);
            qty = itemView.findViewById(R.id.tvIngredientQty);
            status = itemView.findViewById(R.id.tvIngredientStatus);
            amountInput = itemView.findViewById(R.id.etAmount);
            amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            takeBtn = itemView.findViewById(R.id.btnTake);
        }
    }
}