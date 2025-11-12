package com.ph48845.datn_qlnh_rmis.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
// ✅ Thay thế ThuNganAdapter bằng MenuAdapter mới
import com.ph48845.datn_qlnh_rmis.ui.menu.MenuAdapter; // SỬA ĐƯỜNG DẪN NÀY CHO PHÙ HỢP

import java.util.ArrayList;
import java.util.List;

// ✅ Implement Listener để xử lý sự kiện click từ MenuAdapter
public class MainActivity extends AppCompatActivity implements MenuAdapter.OnMenuItemClickListener {

    private static final String TAG = "MenuActivity";
    private RecyclerView recyclerView;
    private MenuAdapter menuAdapter; // ✅ ĐÃ SỬA: Dùng MenuAdapter
    private ProgressBar progressBar;
    private MenuRepository menuRepository;
    private OrderRepository orderRepository;
    private Button btnMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo Repository
        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        btnMenu = findViewById(R.id.btn_create_menu);

        recyclerView = findViewById(R.id.recycler_menu);
        progressBar = findViewById(R.id.progress_bar_loading);

        // 1. Cấu hình RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ✅ KHỞI TẠO MenuAdapter với danh sách rỗng và 'this' là Listener
        // Constructor: MenuAdapter(List<MenuItem> items, OnMenuItemClickListener listener)
        menuAdapter = new MenuAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(menuAdapter);

        btnMenu.setOnClickListener(v -> createSampleMenu());

        // 2. Gọi API để lấy dữ liệu
        fetchMenuItems();
    }

    private void fetchMenuItems() {
        progressBar.setVisibility(View.VISIBLE);

        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {

            @Override
            public void onSuccess(List<MenuItem> items) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    // ✅ ĐÃ SỬA: Gọi phương thức updateList() của MenuAdapter
                    menuAdapter.updateList(items);
                    Log.d(TAG, "Lấy thành công " + items.size() + " món ăn.");
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Lỗi tải dữ liệu Menu: " + message);
                });
            }
        });
    }

    private void createSampleMenu() {
        // (Giữ nguyên code tạo mẫu món ăn)
        List<MenuItem> sampleMenus = new ArrayList<>();
        sampleMenus.add(new MenuItem("Cà phê sữa đá", 25000, "Đồ uống", "available"));
        sampleMenus.add(new MenuItem("Trà đào cam sả", 35000, "Đồ uống", "available"));
        sampleMenus.add(new MenuItem("Bánh mì trứng ốp la", 30000, "Món ăn", "available"));
        sampleMenus.add(new MenuItem("Cơm gà xối mỡ", 45000, "Món ăn", "available"));
        sampleMenus.add(new MenuItem("Nước suối", 10000, "Đồ uống", "available"));

        // Gọi repository để tạo từng món
        for (MenuItem item : sampleMenus) {
            menuRepository.createMenuItem(item, new MenuRepository.RepositoryCallback<MenuItem>() {

                @Override
                public void onSuccess(MenuItem data) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Đã thêm: " + data.getName(), Toast.LENGTH_SHORT).show();
                        Log.d("MENU", "Thêm thành công: " + data.getName());
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Lỗi thêm món: " + message, Toast.LENGTH_SHORT).show();
                        Log.e("MENU", "Lỗi khi thêm món: " + message);
                    });
                }
            });
        }
    }

    // --- ✅ TRIỂN KHAI PHƯƠNG THỨC TỪ INTERFACE ---

    @Override
    public void onMenuItemClick(MenuItem item) {
        // Xử lý khi người dùng chọn món
        Toast.makeText(this, "Đã chọn món: " + item.getName(), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Món đã chọn: " + item.getName() + ", ID: " + item.getId());

        // Logic tiếp theo: Thêm món này vào hóa đơn hiện tại (cần gọi ViewModel hoặc Repository)
    }
}