package com.ph48845.datn_qlnh_rmis. ui. table;

import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis. ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.fragment.SplitItemsDialogFragment;
import com.ph48845.datn_qlnh_rmis.ui.table.fragment.SplitOrderDialogFragment;
// ❌ XÓA import này
// import com.ph48845.datn_qlnh_rmis. ui.table.fragment.SplitOrderAndTableDialogFragment;
import com.ph48845.datn_qlnh_rmis. ui.table.fragment.TableCheckRequestDialogFragment;

/**
 * TableActionsHandler:  hiển thị PopupMenu cho 1 bàn và gọi các manager tương ứng.
 *
 * CẬP NHẬT:
 * - ❌ ĐÃ XÓA:  "Tách hóa đơn + bàn" (case 8)
 * - Chỉ còn 2 options tách:
 *   1. Tách bàn không tách hóa đơn → SplitOrderDialogFragment (di chuyển TẤT CẢ orders)
 *   2. Tách hóa đơn trong 1 bàn → SplitItemsDialogFragment (tách món)
 */
public class TableActionsHandler {

    private final android.app.Activity host;
    private final TransferManager transferManager;
    private final MergeManager mergeManager;
    private final ReservationHelper reservationHelper;

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

    /**
     * Hiển thị PopupMenu với tất cả các actions có thể cho bàn
     * ✅ ĐÃ XÓA: Case 8 - Tách hóa đơn + bàn
     */
    public void showTableActionsMenu(View anchor, TableItem table) {
        PopupMenu popup = new PopupMenu(host, anchor);

        // Chuyển bàn
        popup.getMenu().add(0, 1, 0, "Chuyển bàn");

        // Gộp bàn
        popup. getMenu().add(0, 2, 1, "Gộp bàn");

        // Hủy đặt trước (chỉ hiện khi bàn RESERVED)
        try {
            if (table.getStatus() == TableItem.Status. RESERVED) {
                popup. getMenu().add(0, 3, 2, "Hủy đặt trước");
            }
        } catch (Exception ignored) {}

        // Đặt trước (chỉ hiện khi bàn AVAILABLE)
        try {
            if (table.getStatus() == TableItem.Status.AVAILABLE) {
                popup.getMenu().add(0, 4, 3, "Đặt trước");
            }
        } catch (Exception ignored) {}

        // --- 2 OPTIONS TÁCH (ĐÃ XÓA CASE 8) ---
        // 1. Tách bàn không tách hóa đơn (di chuyển TẤT CẢ orders sang bàn khác)
        popup.getMenu().add(0, 6, 4, "Tách bàn (không tách hóa đơn)");

        // 2. Tách hóa đơn trong 1 bàn (tách món từ orders)
        popup.getMenu().add(0, 7, 5, "Tách hóa đơn (cùng bàn)");

        // ❌ ĐÃ XÓA: Case 8 - Tách hóa đơn + bàn

        // Yêu cầu tạm tính
        popup.getMenu().add(0, 5, 6, "Yêu cầu tạm tính");

        // Yêu cầu kiểm tra bàn
        popup.getMenu().add(0, 9, 7, "Yêu cầu kiểm tra bàn");

        popup.setOnMenuItemClickListener((MenuItem item) -> {
            int id = item.getItemId();
            switch (id) {
                case 1:
                    // Chuyển bàn
                    transferManager.showTransferDialog(table);
                    return true;

                case 2:
                    // Gộp bàn
                    mergeManager.showMergeDialog(table);
                    return true;

                case 3:
                    // Hủy đặt trước
                    reservationHelper.cancelReservation(table);
                    return true;

                case 4:
                    // Đặt trước
                    reservationHelper.showReservationDialogWithPickers(table);
                    return true;

                case 5:
                    // Yêu cầu tạm tính
                    handleTemporaryBillRequest(table);
                    return true;

                case 6:
                    // Tách bàn không tách hóa đơn (di chuyển TẤT CẢ orders)
                    if (host instanceof FragmentActivity) {
                        try {
                            SplitOrderDialogFragment f = SplitOrderDialogFragment.newInstance(
                                    table.getId(),
                                    table.getTableNumber()
                            );
                            f.show(((FragmentActivity) host).getSupportFragmentManager(), "splitOrder");
                        } catch (Exception e) {
                            Toast. makeText(host,
                                    "Không thể mở dialog tách bàn:  " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(host, "Tách bàn không khả dụng", Toast.LENGTH_SHORT).show();
                    }
                    return true;

                case 7:
                    // Tách hóa đơn trong 1 bàn (chọn món từ nhiều orders)
                    if (host instanceof FragmentActivity) {
                        try {
                            SplitItemsDialogFragment f = SplitItemsDialogFragment. newInstance(
                                    table.getId(),
                                    table.getTableNumber()
                            );
                            f.show(((FragmentActivity) host).getSupportFragmentManager(), "splitItems");
                        } catch (Exception e) {
                            Toast. makeText(host,
                                    "Không thể mở dialog tách hóa đơn: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(host, "Tách hóa đơn không khả dụng", Toast.LENGTH_SHORT).show();
                    }
                    return true;

                // ❌ ĐÃ XÓA: case 8 - Tách hóa đơn + bàn

                case 9:
                    // Yêu cầu kiểm tra bàn
                    if (host instanceof FragmentActivity) {
                        try {
                            TableCheckRequestDialogFragment f = TableCheckRequestDialogFragment.newInstance(
                                    table. getId(),
                                    table. getTableNumber()
                            );
                            f.show(((FragmentActivity) host).getSupportFragmentManager(), "tableCheckRequest");
                        } catch (Exception e) {
                            Toast. makeText(host,
                                    "Không thể mở dialog:  " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(host, "Chức năng không khả dụng", Toast. LENGTH_SHORT).show();
                    }
                    return true;

                default:
                    return false;
            }
        });
        popup.show();
    }

    /**
     * Hiển thị menu khi long-press vào bàn
     * - Nếu bàn AVAILABLE: chỉ hiện "Đặt trước"
     * - Nếu bàn có khách:  hiện full menu
     */
    public void showTableActionsMenuForLongPress(View anchor, TableItem table) {
        boolean isEmpty = false;
        try {
            TableItem.Status st = table.getStatus();
            isEmpty = (st == TableItem.Status. AVAILABLE);
        } catch (Exception ignored) {}

        if (isEmpty) {
            // Bàn trống:  chỉ hiện "Đặt trước"
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
            // Bàn có khách: hiện full menu
            showTableActionsMenu(anchor, table);
        }
    }

    /**
     * Xử lý yêu cầu tạm tính
     */
    private void handleTemporaryBillRequest(TableItem table) {
        if (temporaryBillRequester != null) {
            temporaryBillRequester.requestTemporaryBill(table);
        } else {
            // Fallback: mở dialog trực tiếp
            if (host instanceof FragmentActivity) {
                try {
                    TemporaryBillDialogFragment f = TemporaryBillDialogFragment.newInstance(
                            table,
                            updatedOrder -> {
                                if (host instanceof MainActivity) {
                                    ((MainActivity) host).fetchTablesFromServer();
                                }
                            }
                    );
                    f.show(((FragmentActivity) host).getSupportFragmentManager(), "tempBill");
                } catch (Exception e) {
                    Toast.makeText(host,
                            "Không thể mở dialog tạm tính: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(host, "Chưa cấu hình xử lý Yêu Cầu Tạm Tính", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Interface callback cho Yêu cầu tạm tính
     */
    public interface TemporaryBillRequester {
        void requestTemporaryBill(TableItem table);
    }
}