package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;

import java.util.ArrayList;

public class ThuNganActivity extends AppCompatActivity {

    private ThuNganViewModel viewModel;
    private ThuNganAdapter adapter;
    private TextView txtTongCong, txtGiamGia, txtThanhTien;
    private double discountPercent = 10.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        viewModel = new ViewModelProvider(this).get(ThuNganViewModel.class);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewMonAn);
        txtTongCong = findViewById(R.id.txtTongCong);
        txtGiamGia = findViewById(R.id.txtGiamGia);
        txtThanhTien = findViewById(R.id.txtThanhTien);
        Button btnThanhToan = findViewById(R.id.btnThanhToan);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Dummy data test
        Order order = new Order();
        ArrayList<Order.OrderItem> items = new ArrayList<>();
        items.add(new Order.OrderItem("1", "Phở bò", 60000, 1, "READY"));
        items.add(new Order.OrderItem("2", "Bún chả", 45000, 2, "READY"));
        items.add(new Order.OrderItem("3", "Coca", 20000, 1, "READY"));
        order.setItems(items);

        viewModel.setOrder(order);

        adapter = new ThuNganAdapter(items);
        recyclerView.setAdapter(adapter);

        double total = 0;
        for (Order.OrderItem i : items) total += i.getPrice() * i.getQuantity();

        double giamGia = viewModel.calculateDiscount(total, discountPercent);
        double thanhTien = viewModel.calculateFinal(total, giamGia);

        txtTongCong.setText(String.format("%.0fđ", total));
        txtGiamGia.setText(String.format("%.0fđ", giamGia));
        txtThanhTien.setText(String.format("%.0fđ", thanhTien));

        btnThanhToan.setOnClickListener(v -> {
            order.setPaid(true);
            // TODO: Gọi OrderRepository để cập nhật trạng thái thanh toán
        });
    }
}
