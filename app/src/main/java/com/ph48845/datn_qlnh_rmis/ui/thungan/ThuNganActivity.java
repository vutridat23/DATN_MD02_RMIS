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

        // --- SỬA LỖI 1: CONSTRUCTOR ORDERITEM (Giả định: thêm constructor 5 tham số) ---
        // Ghi chú: Các tham số là: (menuItemId, name, price, quantity, status)
        // Lớp OrderItem hiện tại của bạn có 4 tham số (String, String, int, double),
        // nhưng code này đang truyền 5 tham số và price/quantity bị đảo ngược thứ tự so với OrderItem chuẩn (price là double, quantity là int).
        // TÔI ĐÃ ĐẢO NGƯỢC THỨ TỰ (price, quantity) cho đúng với định nghĩa chuẩn của OrderItem, và giả định bạn đã thêm constructor 5 tham số.

        // SỬA: Điều chỉnh tham số cho đúng kiểu (price là double) và thứ tự (đã đảo price và quantity)
        items.add(new Order.OrderItem("1", "Phở bò", 60000.0, 1, "READY"));
        items.add(new Order.OrderItem("2", "Bún chả", 45000.0, 2, "READY"));
        items.add(new Order.OrderItem("3", "Coca", 20000.0, 1, "READY"));
        order.setItems(items);

        viewModel.setOrder(order);

        // --- SỬA LỖI 2: CONSTRUCTOR THUNGANADAPTER ---
        // Cần truyền Context (this) làm tham số đầu tiên.
        adapter = new ThuNganAdapter(this, items); // 2 tham số: Context và List
        recyclerView.setAdapter(adapter);

        double total = 0;
        for (Order.OrderItem i : items) total += i.getPrice() * i.getQuantity();

        double giamGia = viewModel.calculateDiscount(total, discountPercent);
        double thanhTien = viewModel.calculateFinal(total, giamGia);

        txtTongCong.setText(String.format("%.0fđ", total));
        txtGiamGia.setText(String.format("%.0fđ", giamGia));
        txtThanhTien.setText(String.format("%.0fđ", thanhTien));

        btnThanhToan.setOnClickListener(v -> {
            // --- SỬA LỖI 3: PHƯƠNG THỨC SETPAID ---
            // Lớp Order không có setPaid(boolean), thay bằng setOrderStatus(String)
            order.setOrderStatus("Paid");
            // TODO: Gọi OrderRepository để cập nhật trạng thái thanh toán
        });
    }
}