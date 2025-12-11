package com.ph48845.datn_qlnh_rmis.ui.table;

import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

/**
 * TableActionsHandler: hiển thị PopupMenu cho 1 bàn và gọi các manager tương ứng.
 *
 * Chỉ sửa tối thiểu: khi long-press vào bàn trống (EMPTY hoặc AVAILABLE) chỉ hiển thị "Đặt trước".
 * Các chức năng khác giữ nguyên.
 */
public class TableActionsHandler {

    private final android.app.Activity host;
    private final TransferManager transferManager;
    private final MergeManager mergeManager;
    private final ReservationHelper reservationHelper;

    // Optional handler để xử lý yêu cầu tạm tính (được set từ Activity/Adapter khi cần)
    private TemporaryBillRequester temporaryBillRequester;

    public TableActionsHandler(android.app.Activity host,
                               TransferManager transferManager,
                               MergeManager mergeManager,
                               ReservationHelper reservationHelper) {
        this.host = host;
        this.transferManager = transferManager;
        this.mergeManager = mergeManager;
        this.reservationHelper = reservationHelper;
    }

    /**
     * Đăng ký handler để xử lý "Yêu Cầu Tạm TÍnh".
     * Nếu không set, khi người dùng chọn mục đó sẽ hiện Toast báo chưa cấu hình.
     */
    public void setTemporaryBillRequester(TemporaryBillRequester requester) {
        this.temporaryBillRequester = requester;
    }

    /**
     * Hiện popup menu thông thường (bây giờ có thêm mục "Yêu Cầu Tạm TÍnh").
     * Dùng cho click bình thường.
     */
    public void showTableActionsMenu(View anchor, TableItem table) {
        PopupMenu popup = new PopupMenu(host, anchor);
        popup.getMenu().add(0, 1, 0, "Chuyển bàn");
        popup.getMenu().add(0, 2, 1, "Gộp bàn");

        try {
            if (table.getStatus() == TableItem.Status.RESERVED)
                popup.getMenu().add(0, 3, 2, "Hủy đặt trước");
        } catch (Exception ignored) {}
        try {
            if (table.getStatus() == TableItem.Status.AVAILABLE)
                popup.getMenu().add(0, 4, 3, "Đặt trước");
        } catch (Exception ignored) {}

        // THÊM mục "Yêu Cầu Tạm TÍnh" trong menu chính
        popup.getMenu().add(0, 5, 4, "Yêu Cầu Tạm TÍnh");

        popup.setOnMenuItemClickListener((MenuItem item) -> {
            int id = item.getItemId();
            switch (id) {
                case 1:
                    transferManager.showTransferDialog(table);
                    return true;
                case 2:
                    // multi-select merge
                    mergeManager.showMergeDialog(table);
                    return true;
                case 3:
                    reservationHelper.cancelReservation(table);
                    return true;
                case 4:
                    reservationHelper.showReservationDialogWithPickers(table);
                    return true;
                case 5:
                    if (temporaryBillRequester != null) {
                        temporaryBillRequester.requestTemporaryBill(table);
                    } else {
                        Toast.makeText(host, "Chưa cấu hình xử lý Yêu Cầu Tạm TÍnh", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    /**
     * Hiển thị popup menu khi người dùng long-press vào bàn.
     * Nếu bàn trống (EMPTY/AVAILABLE) CHỈ hiện "Đặt trước".
     * Nếu bàn không trống, gọi showTableActionsMenu để hiển thị menu đầy đủ.
     */
    public void showTableActionsMenuForLongPress(View anchor, TableItem table) {
        boolean isEmpty = false;
        try {
            TableItem.Status st = table.getStatus();
            isEmpty = (st == TableItem.Status.AVAILABLE);
        } catch (Exception ignored) {}

        if (isEmpty) {
            // Only show "Đặt trước"
            PopupMenu popup = new PopupMenu(host, anchor);
            popup.getMenu().add(0, 4, 0, "Đặt trước");
            popup.setOnMenuItemClickListener((MenuItem item) -> {
                int id = item.getItemId();
                if (id == 4) {
                    reservationHelper.showReservationDialogWithPickers(table);
                    return true;
                }
                return false;
            });
            popup.show();
        } else {
            // For non-empty tables, reuse the full menu
            showTableActionsMenu(anchor, table);
        }
    }

    /**
     * Interface để Activity/Adapter hiện thực xử lý "Yêu Cầu Tạm TÍnh".
     * Ví dụ: mở dialog tạm tính, gửi yêu cầu đến server, v.v.
     */
    public interface TemporaryBillRequester {
        void requestTemporaryBill(TableItem table);
    }
}