package com.ph48845.datn_qlnh_rmis.ui.table;

import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.fragment.SplitItemsDialogFragment;
import com.ph48845.datn_qlnh_rmis.ui.table.fragment.SplitOrderDialogFragment;

/**
 * TableActionsHandler: hiển thị PopupMenu cho 1 bàn và gọi các manager tương ứng.
 *
 * Thay đổi:
 * - Thêm mục "Tách bàn" (đã có) và "Tách Hóa Đơn" (mới).
 * - Nếu temporaryBillRequester không được set thì fallback mở TemporaryBillDialogFragment trực tiếp.
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

    public void setTemporaryBillRequester(TemporaryBillRequester requester) {
        this.temporaryBillRequester = requester;
    }

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

        // Tách bàn (mục cũ)
        popup.getMenu().add(0, 6, 4, "Tách bàn");

        // Tách Hóa Đơn (mục mới)
        popup.getMenu().add(0, 7, 5, "Tách Hóa Đơn");

        // Yêu Cầu Tạm Tính
        popup.getMenu().add(0, 5, 6, "Yêu Cầu Tạm Tính");

        popup.setOnMenuItemClickListener((MenuItem item) -> {
            int id = item.getItemId();
            switch (id) {
                case 1:
                    transferManager.showTransferDialog(table);
                    return true;
                case 2:
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
                        if (host instanceof FragmentActivity) {
                            try {
                                TemporaryBillDialogFragment f = TemporaryBillDialogFragment.newInstance(table, updatedOrder -> {
                                    if (host instanceof MainActivity) ((MainActivity) host).fetchTablesFromServer();
                                });
                                f.show(((FragmentActivity) host).getSupportFragmentManager(), "tempBill");
                            } catch (Exception e) {
                                Toast.makeText(host, "Không thể mở dialog tạm tính: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(host, "Chưa cấu hình xử lý Yêu Cầu Tạm TÍnh", Toast.LENGTH_SHORT).show();
                        }
                    }
                    return true;
                case 6:
                    if (host instanceof FragmentActivity) {
                        try {
                            SplitOrderDialogFragment f = SplitOrderDialogFragment.newInstance(table.getId(), table.getTableNumber());
                            f.show(((FragmentActivity) host).getSupportFragmentManager(), "splitOrder");
                        } catch (Exception e) {
                            Toast.makeText(host, "Không thể mở dialog tách bàn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(host, "Tách bàn không khả dụng trên Activity này", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case 7:
                    // MỚI: Tách Hóa Đơn -> mở dialog chọn món across orders
                    if (host instanceof FragmentActivity) {
                        try {
                            SplitItemsDialogFragment f = SplitItemsDialogFragment.newInstance(table.getId(), table.getTableNumber());
                            f.show(((FragmentActivity) host).getSupportFragmentManager(), "splitItems");
                        } catch (Exception e) {
                            Toast.makeText(host, "Không thể mở dialog tách hóa đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(host, "Tách hóa đơn không khả dụng trên Activity này", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    public void showTableActionsMenuForLongPress(View anchor, TableItem table) {
        boolean isEmpty = false;
        try {
            TableItem.Status st = table.getStatus();
            isEmpty = (st == TableItem.Status.AVAILABLE);
        } catch (Exception ignored) {}

        if (isEmpty) {
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
            showTableActionsMenu(anchor, table);
        }
    }

    public interface TemporaryBillRequester {
        void requestTemporaryBill(TableItem table);
    }
}