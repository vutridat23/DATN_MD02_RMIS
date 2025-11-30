package com.ph48845.datn_qlnh_rmis.ui.table;



import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReservationHelper: dialog đặt trước (nhập name/phone/date/time) và hủy đặt trước.
 *
 * Thêm: tự động hủy đặt trước nếu sau X phút kể từ thời điểm reservationAt (hoặc nếu reservationAt <= now thì từ lúc đặt)
 * khách không đến => sẽ tự gọi cancelReservation() và chuyển trạng thái bàn về "available".
 *
 * Cách hoạt động:
 * - Khi set reservation thành công sẽ gọi scheduleAutoCancelForReservation(...) để lập lịch hủy tự động.
 * - Khi hủy đặt trước thủ công sẽ huỷ task đã lên lịch nếu có.
 *
 * Lưu ý: cơ chế này hoạt động trong tiến trình app đang chạy. Nếu cần đảm bảo hủy trên server ngay cả khi app bị kill,
 * cần bổ sung xử lý phía server hoặc sử dụng AlarmManager/WorkManager để lập lịch hệ thống.
 */
public class ReservationHelper {

    private final android.app.Activity host;
    private final TableRepository tableRepository;
    private final ProgressBar progressBar;

    // handler trên main looper để lập lịch hủy
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // map để quản lý các Runnable đã lập lịch theo tableId (để huỷ khi cần)
    private final Map<String, Runnable> scheduledCancelMap = new ConcurrentHashMap<>();

    // định dạng reservationAt (đồng bộ với nơi khác trong app)
    private static final String RESERVATION_FORMAT = "yyyy-MM-dd HH:mm";

    // --- Thay đổi theo yêu cầu: tự huỷ sau 2 phút ---
    private static final long AUTO_CANCEL_MINUTES = 2L;
    // -------------------------------------------------

    public ReservationHelper(android.app.Activity host, TableRepository tableRepository, ProgressBar progressBar) {
        this.host = host;
        this.tableRepository = tableRepository;
        this.progressBar = progressBar;
    }

    public void showReservationDialogWithPickers(TableItem table) {
        if (table == null) return;
        LinearLayout layout = new LinearLayout(host);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * host.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText etName = new EditText(host); etName.setHint("Tên khách"); layout.addView(etName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final EditText etPhone = new EditText(host); etPhone.setHint("Số điện thoại"); etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE); layout.addView(etPhone, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final EditText etDate = new EditText(host); etDate.setHint("Chọn ngày (yyyy-MM-dd)"); etDate.setFocusable(false); etDate.setClickable(true); layout.addView(etDate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final EditText etTime = new EditText(host); etTime.setHint("Chọn giờ (HH:mm)"); etTime.setFocusable(false); etTime.setClickable(true); layout.addView(etTime, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Calendar selectedCal = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        etDate.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new DatePickerDialog(host, (view, y, m, d) -> { selectedCal.set(Calendar.YEAR, y); selectedCal.set(Calendar.MONTH, m); selectedCal.set(Calendar.DAY_OF_MONTH, d); etDate.setText(dateFormat.format(selectedCal.getTime())); }, selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        etTime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new TimePickerDialog(host, (view, h, m) -> { selectedCal.set(Calendar.HOUR_OF_DAY, h); selectedCal.set(Calendar.MINUTE, m); selectedCal.set(Calendar.SECOND, 0); etTime.setText(timeFormat.format(selectedCal.getTime())); }, selectedCal.get(Calendar.HOUR_OF_DAY), selectedCal.get(Calendar.MINUTE), true).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("Đặt trước - Bàn " + table.getTableNumber())
                .setView(layout)
                .setPositiveButton("Đặt", null)
                .setNegativeButton("Hủy", (d,w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String dateStr = etDate.getText().toString().trim();
            String timeStr = etTime.getText().toString().trim();
            if (name.isEmpty() || phone.isEmpty() || dateStr.isEmpty() || timeStr.isEmpty()) { Toast.makeText(host, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show(); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            progressBar.setVisibility(android.view.View.VISIBLE);
            String reservationAt = dateStr + " " + timeStr;
            Map<String,Object> body = new HashMap<>();
            body.put("status", "reserved");
            body.put("reservationName", name);
            body.put("reservationPhone", phone);
            body.put("reservationAt", reservationAt);

            tableRepository.updateTable(table.getId(), body, new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem updatedTable) {
                    host.runOnUiThread(() -> {
                        progressBar.setVisibility(android.view.View.GONE);
                        Toast.makeText(host, "Đặt trước thành công", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // Lập lịch tự động huỷ: sau AUTO_CANCEL_MINUTES phút kể từ reservationAt (nếu reservationAt hợp lệ)
                        scheduleAutoCancelForReservation(updatedTable);
                        if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                    });
                }
                @Override
                public void onError(String message) {
                    host.runOnUiThread(() -> {
                        progressBar.setVisibility(android.view.View.GONE);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(host, "Không thể đặt trước: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }));
        dialog.show();
    }

    /**
     * Lên lịch tự động huỷ reservation.
     * Quy tắc: nếu reservationAt parse được, sẽ đặt thời điểm huỷ = reservationAt + AUTO_CANCEL_MINUTES.
     * Nếu reservationAt không parse được hoặc reservationAt <= now, sẽ đặt huỷ sau AUTO_CANCEL_MINUTES kể từ bây giờ.
     */
    private void scheduleAutoCancelForReservation(TableItem table) {
        if (table == null || table.getId() == null) return;
        // Huỷ task cũ nếu có
        cancelScheduledAutoCancel(table.getId());

        long now = System.currentTimeMillis();
        long cancelAtMillis = now + AUTO_CANCEL_MINUTES * 60 * 1000L; // default: AUTO_CANCEL_MINUTES từ bây giờ

        String reservAtStr = table.getReservationAt();
        if (reservAtStr != null && !reservAtStr.trim().isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat(RESERVATION_FORMAT, Locale.getDefault());
            try {
                long reservMillis = sdf.parse(reservAtStr.trim()).getTime();
                long candidate = reservMillis + AUTO_CANCEL_MINUTES * 60 * 1000L; // reservationAt + AUTO_CANCEL_MINUTES
                // nếu candidate in the future, dùng candidate; nếu candidate already passed, nếu reservationAt trước now thì schedule AUTO_CANCEL_MINUTES từ now
                if (candidate > now) cancelAtMillis = candidate;
                else cancelAtMillis = now + AUTO_CANCEL_MINUTES * 60 * 1000L;
            } catch (ParseException ignored) {
                // parse fail -> keep default cancelAtMillis (AUTO_CANCEL_MINUTES từ now)
            } catch (Exception ignored) {}
        }

        long delay = cancelAtMillis - now;
        if (delay < 0) delay = 0;

        Runnable task = () -> {
            // khi tới thời điểm, kiểm tra lại trạng thái bàn trên server trước khi huỷ
            tableRepository.getTableById(table.getId(), new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem fresh) {
                    // nếu vẫn reserved và reservationAt khớp -> huỷ
                    if (fresh != null) {
                        boolean isReserved = false;
                        try { isReserved = fresh.getStatus() == TableItem.Status.RESERVED; } catch (Exception ignored) {}
                        String freshReserv = fresh.getReservationAt() != null ? fresh.getReservationAt().trim() : "";
                        String originalReserv = table.getReservationAt() != null ? table.getReservationAt().trim() : "";
                        if (isReserved && (!originalReserv.isEmpty() ? originalReserv.equals(freshReserv) : true)) {
                            // gọi huỷ đặt trước
                            cancelReservation(fresh);
                        } else {
                            // nếu trạng thái đã thay đổi, không làm gì
                        }
                    }
                    // remove scheduled map entry
                    scheduledCancelMap.remove(table.getId());
                }

                @Override
                public void onError(String message) {
                    // nếu không lấy được info, không cố gắng hủy ngay; nhưng vẫn remove entry để tránh leak
                    scheduledCancelMap.remove(table.getId());
                }
            });
        };

        scheduledCancelMap.put(table.getId(), task);
        mainHandler.postDelayed(task, delay);
    }

    /**
     * Huỷ task đã lập lịch (nếu có) cho tableId
     */
    private void cancelScheduledAutoCancel(String tableId) {
        if (tableId == null) return;
        Runnable r = scheduledCancelMap.remove(tableId);
        if (r != null) {
            mainHandler.removeCallbacks(r);
        }
    }

    public void cancelReservation(TableItem table) {
        if (table == null || table.getId() == null || table.getId().trim().isEmpty()) { Toast.makeText(host, "Bàn không hợp lệ", Toast.LENGTH_SHORT).show(); return; }
        progressBar.setVisibility(android.view.View.VISIBLE);
        Map<String,Object> body = new HashMap<>();
        body.put("status","available");
        body.put("reservationName","");
        body.put("reservationPhone","");
        body.put("reservationAt","");
        tableRepository.updateTable(table.getId(), body, new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem result) {
                host.runOnUiThread(() -> {
                    // Huỷ scheduled auto-cancel nếu có
                    cancelScheduledAutoCancel(table.getId());
                    progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(host, "Hủy đặt trước thành công", Toast.LENGTH_SHORT).show();
                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                });
            }
            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(host, "Không thể hủy đặt trước: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}