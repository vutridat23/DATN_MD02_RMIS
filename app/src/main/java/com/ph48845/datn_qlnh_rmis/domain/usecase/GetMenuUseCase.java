package com.ph48845.datn_qlnh_rmis.domain.usecase;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.respository.MenuRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UseCase để lấy danh sách menu
 * Xử lý business logic và gọi Repository để lấy dữ liệu
 */
public class GetMenuUseCase {
    private final MenuRepository menuRepository;
    private final ExecutorService executorService;

    public GetMenuUseCase(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Interface để callback kết quả
     */
    public interface Callback {
        void onSuccess(List<MenuItem> menuItems);
        void onError(String error);
    }

    /**
     * Lấy tất cả menu items
     * Chạy trên background thread và callback về main thread
     */
    public void execute(Callback callback) {
        executorService.execute(() -> {
            try {
                List<MenuItem> menuItems = menuRepository.getAllMenu();
                
                if (menuItems != null && !menuItems.isEmpty()) {
                    // Validate và filter nếu cần
                    List<MenuItem> validatedItems = validateMenuItems(menuItems);
                    
                    // Callback về main thread (giả định sẽ được handle ở ViewModel)
                    if (callback != null) {
                        callback.onSuccess(validatedItems);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("Danh sách menu trống");
                    }
                }
            } catch (Exception e) {
                Log.e("GetMenuUseCase", "❌ Lỗi lấy menu: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Lỗi khi tải danh sách menu: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Lấy menu theo category
     */
    public void executeByCategory(String category, Callback callback) {
        executorService.execute(() -> {
            try {
                List<MenuItem> allItems = menuRepository.getAllMenu();
                
                if (allItems != null) {
                    // Filter theo category
                    List<MenuItem> filteredItems = new java.util.ArrayList<>();
                    for (MenuItem item : allItems) {
                        if (category == null || category.isEmpty() || 
                            item.getCategory() != null && item.getCategory().equals(category)) {
                            filteredItems.add(item);
                        }
                    }
                    
                    if (callback != null) {
                        callback.onSuccess(filteredItems);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("Không tìm thấy món ăn với loại: " + category);
                    }
                }
            } catch (Exception e) {
                Log.e("GetMenuUseCase", "❌ Lỗi lấy menu theo category: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Lỗi khi tải menu: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Validate menu items - đảm bảo dữ liệu hợp lệ
     */
    private List<MenuItem> validateMenuItems(List<MenuItem> items) {
        List<MenuItem> validated = new java.util.ArrayList<>();
        
        for (MenuItem item : items) {
            // Kiểm tra các trường bắt buộc
            if (item != null && 
                item.getName() != null && !item.getName().trim().isEmpty() &&
                item.getPrice() >= 0) {
                validated.add(item);
            } else {
                Log.w("GetMenuUseCase", "⚠️ Bỏ qua menu item không hợp lệ: " + item);
            }
        }
        
        return validated;
    }

    /**
     * Cleanup executor khi không cần dùng nữa
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
