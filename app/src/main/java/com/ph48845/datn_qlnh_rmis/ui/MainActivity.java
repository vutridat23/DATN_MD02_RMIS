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
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;

import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MenuActivity";
    private RecyclerView recyclerView;
    private ThuNganAdapter menuAdapter;
    private ProgressBar progressBar;
    private MenuRepository menuRepository; // Khai báo Repository
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

        // Khởi tạo Adapter với danh sách rỗng (để tránh lỗi NullPointer)
        menuAdapter = new ThuNganAdapter(new ArrayList<>());
        recyclerView.setAdapter(menuAdapter);
        btnMenu.setOnClickListener(v -> createSampleMenu());

        // 2. Gọi API để lấy dữ liệu thông qua Repository
        fetchMenuItems();
    }

    private void fetchMenuItems() {
        progressBar.setVisibility(View.VISIBLE); // Hiển thị loading

        // Gọi phương thức từ Repository, truyền vào Callback để xử lý kết quả
        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {

            @Override
            public void onSuccess(List<MenuItem> items) {
                // Chạy trên luồng UI (vì onResponse của Retrofit đã được thực hiện trên luồng chính,
                // nhưng tốt nhất nên đảm bảo nếu Repository có xử lý bất đồng bộ khác)
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE); // Ẩn loading

                    // Cập nhật dữ liệu vào Adapter
                    menuAdapter.setMenuItems(items);
                    Log.d(TAG, "Lấy thành công " + items.size() + " món ăn.");
                });
            }

            @Override
            public void onError(String message) {
                // Xử lý lỗi (lỗi mạng, lỗi HTTP, lỗi logic server)
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE); // Ẩn loading
                    Toast.makeText(MainActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Lỗi tải dữ liệu Menu: " + message);
                });
            }
        });
    }


    private void createSampleMenu() {
        // Danh sách món mẫu
        List<MenuItem> sampleMenus = new ArrayList<>();
        sampleMenus.add(new MenuItem("Cà phê sữa đá", 25000, "Đồ uống", "available"));
        sampleMenus.add(new MenuItem("Trà đào cam sả", 35000, "Đồ uống", "available"));
        sampleMenus.add(new MenuItem("Bánh mì trứng ốp la", 30000, "Món ăn", "available"));
        sampleMenus.add(new MenuItem("Cơm gà xối mỡ", 45000, "Món ăn", "available"));
        sampleMenus.add(new MenuItem("Nước suối", 10000, "Đồ uống", "available"));

        // Gọi repository để tạo từng món
        for (MenuItem item : sampleMenus) {
            menuReposi1tory.createMenuItem(item, new MenuRepository.RepositoryCallback<MenuItem>() {
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




}