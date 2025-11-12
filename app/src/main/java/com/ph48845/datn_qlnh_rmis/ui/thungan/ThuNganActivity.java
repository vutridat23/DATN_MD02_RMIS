package com.ph48845.datn_qlnh_rmis.ui.thungan;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
// SỬA LỖI: Import lớp Item lồng bên trong Order
import com.ph48845.datn_qlnh_rmis.data.model.Order.Item;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter; // Import Adapter từ package con
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganViewModel;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// KHÔNG CÒN implements ThuNganAdapter.OnItemInteractionListener vì Adapter đã sửa
public class ThuNganActivity extends AppCompatActivity {

    private ThuNganViewModel thuNganViewModel;
    private ThuNganAdapter thuNganAdapter;

    // Khai báo Views
    private RecyclerView rcvTongcong; // Dựa trên ID bạn có thể dùng trong XML
    private TextView txtTongcong, txtGiamGia, txtThanhTien;

    private final double discountPercent = 10.0;
    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        // Cấu hình định dạng tiền tệ
        currencyFormatter.setMaximumFractionDigits(0);

        // 1. Khởi tạo ViewModel
        thuNganViewModel = new ViewModelProvider(this).get(ThuNganViewModel.class);

        // 2. Ánh xạ Views
        // SỬ DỤNG ID THỰC TẾ TRONG LAYOUT CỦA BẠN (Dòng 30-35)
        rcvTongcong = findViewById(R.id.recyclerViewMonAn); // Giả sử ID RecyclerView
        txtTongcong = findViewById(R.id.txtTongCong);
        txtGiamGia = findViewById(R.id.txtGiamGia);
        txtThanhTien = findViewById(R.id.txtThanhTien);
        // ... (findViewById cho các views khác)

        // 3. KHỞI TẠO ADAPTER ĐÚNG CÁCH (Lỗi dòng 41, 50, 51 đã được sửa)
        // Adapter mới không cần Listener, chỉ cần danh sách Item
        thuNganAdapter = new ThuNganAdapter(new ArrayList<>());

        // Thiết lập RecyclerView
        rcvTongcong.setLayoutManager(new LinearLayoutManager(this));
        rcvTongcong.setAdapter(thuNganAdapter);

        // 4. Dummy data (Dữ liệu mẫu để kiểm tra)
        setupDummyData();

        // 5. Quan sát LiveData (Observe)
        thuNganViewModel.getOrder().observe(this, this::updateUI);
    }

    /**
     * Hàm mẫu để tạo dữ liệu cho Order và tính toán tổng tiền
     */
    private void setupDummyData() {
        // TẠO LỚP ITEM: Lỗi Cannot resolve symbol 'OrderItem' đã được sửa
        List<Item> items = new ArrayList<>();
        // SỬA LỖI: Gọi constructor của Item (String menuItem, int quantity, double price)
        items.add(new Item("Phở bò", 1, 60000.0)); // Lỗi dòng 43 đã được sửa
        items.add(new Item("Bún chả", 2, 90000.0));
        items.add(new Item("Coca", 1, 20000.0));

        // TẠO LỚP ORDER
        // Lỗi: Constructor của Order yêu cầu 11 tham số.
        // Nếu không dùng constructor 11 tham số, bạn cần tạo constructor rỗng/đơn giản.
        // TẠM THỜI: SỬ DỤNG HÀM TẠO MẶC ĐỊNH VÀ SETTER (GIẢ ĐỊNH BẠN ĐÃ THÊM HÀM TẠO RỖNG VÀ SETTER)
        Order order = new Order(); // GIẢ ĐỊNH Order có hàm tạo rỗng

        // SỬA LỖI: Method 'setItems' (Lỗi dòng 46 đã được sửa)
        order.setItems(items); // PHẢI CÓ PHƯƠNG THỨC setItems trong lớp Order

        // Tính toán tổng tiền
        double total = calculateTotal(items);
        order.setTotalAmount(total); // PHẢI CÓ PHƯƠNG THỨC setTotalAmount trong Order

        // Đặt Order vào ViewModel
        thuNganViewModel.setOrder(order);
    }

    /**
     * Tính tổng tiền từ danh sách Item
     */
    private double calculateTotal(List<Item> items) {
        double total = 0;
        // Lỗi getPrice/getQuantity đã được sửa vì Item đã có các method này
        for (Item item : items) {
            total += item.getPrice() * item.getQuantity();
        }
        return total;
    }


    /**
     * Hàm cập nhật toàn bộ giao diện dựa trên đối tượng Order mới nhất
     */
    private void updateUI(Order order) {
        if (order == null) return;

        // 1. Cập nhật RecyclerView (Danh sách món ăn)
        // PHẢI CÓ phương thức getItems() trong lớp Order
        thuNganAdapter.updateList(order.getItems());

        // 2. Cập nhật thông tin tổng tiền (Lỗi getPaid đã được loại bỏ)
        // PHẢI CÓ phương thức getTotalAmount() trong lớp Order
        double total = order.getTotalAmount();
        double discount = thuNganViewModel.calculateDiscount(total, discountPercent);
        double finalAmount = thuNganViewModel.calculateFinal(total, discount);

        // Hiển thị lên TextViews
        txtTongcong.setText(currencyFormatter.format(total));
        txtGiamGia.setText(currencyFormatter.format(discount));
        txtThanhTien.setText(currencyFormatter.format(finalAmount));
    }
}