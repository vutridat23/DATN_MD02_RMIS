package com.ph48845.datn_qlnh_rmis.ui.table;


import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

/**
 * TableActionsHandler: hiển thị PopupMenu cho 1 bàn và gọi các manager tương ứng.
 */
public class TableActionsHandler {

    private final android.app.Activity host;
    private final TransferManager transferManager;
    private final MergeManager mergeManager;
    private final ReservationHelper reservationHelper;

    public TableActionsHandler(android.app.Activity host,
                               TransferManager transferManager,
                               MergeManager mergeManager,
                               ReservationHelper reservationHelper) {
        this.host = host;
        this.transferManager = transferManager;
        this.mergeManager = mergeManager;
        this.reservationHelper = reservationHelper;
    }

    public void showTableActionsMenu(View anchor, TableItem table) {
        PopupMenu popup = new PopupMenu(host, anchor);
        popup.getMenu().add(0, 1, 0, "Chuyển bàn");
        popup.getMenu().add(0, 2, 1, "Gộp bàn");
        try {
            if (table.getStatus() == TableItem.Status.RESERVED) popup.getMenu().add(0, 3, 2, "Hủy đặt trước");
        } catch (Exception ignored) {}
        try {
            if (table.getStatus() == TableItem.Status.EMPTY || table.getStatus() == TableItem.Status.AVAILABLE) popup.getMenu().add(0, 4, 3, "Đặt trước");
        } catch (Exception ignored) {}

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
                default:
                    return false;
            }
        });
        popup.show();
    }
}
