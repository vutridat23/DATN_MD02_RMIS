
package com.ph48845.datn_qlnh_rmis.ui.table;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.InAppNotification;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

import com.ph48845.datn_qlnh_rmis.ui.phucvu.notification.NotificationManager;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * ReservationHelper:
 * - When user confirms a reservation, server will auto-cancel (server side).
 * - Client does not schedule in-process auto-cancel; it relies on socket events from server.
 *
 * NOTE: Added local socket listener to show dialog/notification when reservation is auto-released.
 */
public class ReservationHelper {

    private final android.app.Activity host;
    private final TableRepository tableRepository;
    private final ProgressBar progressBar;

    // Format used for reservationAt (kept for potential future use)
    private static final String RESERVATION_FORMAT = "yyyy-MM-dd HH:mm";

    // Socket listening
    private final SocketManager socketManager;
    private SocketManager.OnEventListener reservationListener;
    private boolean listenerRegistered = false;

    public ReservationHelper(android.app.Activity host, TableRepository tableRepository, ProgressBar progressBar) {
        this.host = host;
        this.tableRepository = tableRepository;
        this.progressBar = progressBar;

        SocketManager sm = null;
        try {
            sm = SocketManager.getInstance();
        } catch (Exception ignored) {}
        this.socketManager = sm;
    }

    /**
     * Start listening for auto-release events so we can show dialog/notification even
     * when ReservationHelper is used outside MainActivity flow.
     * Safe to call multiple times.
     */
    public void startListening() {
        try {
            if (socketManager == null) return;
            if (listenerRegistered) return;

            reservationListener = new SocketManager.OnEventListener() {
                @Override
                public void onTableUpdated(JSONObject payload) {
                    if (payload == null) return;
                    String evt = payload.optString("eventName", "");
                    if (!"table_auto_released".equals(evt)) return;

                    int tblNum = -1;
                    if (payload.has("tableNumber"))
                        tblNum = payload.optInt("tableNumber", -1);
                    else if (payload.has("table"))
                        tblNum = payload.optInt("table", -1);

                    final int shownNum = tblNum;

                    // Always try to show an in-app notification (non-blocking)
                    try {
                        InAppNotification notification = new InAppNotification.Builder(
                                InAppNotification.Type.WARNING,
                                "⏰ Hủy đặt bàn tự động",
                                "Bàn " + (shownNum > 0 ? shownNum : "") + " đã hết thời gian đặt trước"
                        )
                                .actionData("table:" + shownNum)
                                .duration(8000)
                                .build();
                        NotificationManager.getInstance().show(notification);
                    } catch (Exception e) {
                        // ignore
                    }

                    // Decide whether we can show dialog now
                    boolean canShowDialog = true;
                    try {
                        if (host.isFinishing()) canShowDialog = false;
                    } catch (Exception ignored) {}
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        try {
                            if (host.isDestroyed()) canShowDialog = false;
                        } catch (Exception ignored) {}
                    }

                    boolean hasFocus = false;
                    try {
                        hasFocus = host.hasWindowFocus();
                    } catch (Exception ignored) {}

                    // If host is MainActivity, prefer using its helper to manage pending dialog logic
                    if (host instanceof MainActivity) {
                        MainActivity ma = (MainActivity) host;
                        boolean maVisible = false;
                        try {
                            // Use public accessor instead of direct field access
                            maVisible = ma.isActivityVisible() || hasFocus;
                        } catch (Exception ignored) {}

                        if (canShowDialog && maVisible) {
                            host.runOnUiThread(() -> {
                                try {
                                    new AlertDialog.Builder(host)
                                            .setTitle("Thông báo")
                                            .setMessage("Bàn " + (shownNum > 0 ? shownNum : "") + " đã tự động hủy đặt trước.")
                                            .setCancelable(false)
                                            .setPositiveButton("OK", null)
                                            .show();
                                } catch (Exception ex) {
                                    // fallback toast
                                    try { Toast.makeText(host, "Bàn " + shownNum + " đã tự hủy đặt trước", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
                                } finally {
                                    try { ma.fetchTablesFromServer(); } catch (Exception ignored) {}
                                }
                            });
                        } else {
                            // let MainActivity handle pending behavior — use its public setter
                            try {
                                ma.setPendingAutoReleasedTable(shownNum);
                                // ensure tables refreshed
                                ma.runOnUiThread(() -> ma.fetchTablesFromServer());
                            } catch (Exception ex) {
                                // fallback: show toast
                                host.runOnUiThread(() -> {
                                    try { Toast.makeText(host, "Bàn " + shownNum + " đã tự hủy đặt trước", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
                                    try { if (host instanceof MainActivity) ((MainActivity) host).fetchTablesFromServer(); } catch (Exception ignored) {}
                                });
                            }
                        }
                    } else {
                        // Generic Activity host: show dialog if possible, otherwise toast
                        if (canShowDialog && (hasFocus)) {
                            host.runOnUiThread(() -> {
                                try {
                                    new AlertDialog.Builder(host)
                                            .setTitle("Thông báo")
                                            .setMessage("Bàn " + (shownNum > 0 ? shownNum : "") + " đã tự hủy đặt trước.")
                                            .setCancelable(false)
                                            .setPositiveButton("OK", null)
                                            .show();
                                } catch (Exception ex) {
                                    try { Toast.makeText(host, "Bàn " + shownNum + " đã tự hủy đặt trước", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
                                }
                                // refresh if possible
                                try {
                                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity)
                                        ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                } catch (Exception ignored) {}
                            });
                        } else {
                            host.runOnUiThread(() -> {
                                try { Toast.makeText(host, "Bàn " + shownNum + " đã tự hủy đặt trước", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
                            });
                        }
                    }
                }

                @Override
                public void onOrderCreated(JSONObject payload) {
                    // Không cần xử lý order events trong ReservationHelper
                }

                @Override
                public void onOrderUpdated(JSONObject payload) {
                    // Không cần xử lý order events trong ReservationHelper
                }

                @Override
                public void onConnect() { }

                @Override
                public void onDisconnect() { }

                @Override
                public void onError(Exception e) { }
            };

            socketManager.setOnEventListener(reservationListener);
            listenerRegistered = true;
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Stop listening to socket events (call from Activity.onDestroy)
     */
    public void stopListening() {
        try {
            if (socketManager == null || reservationListener == null || !listenerRegistered) return;
            socketManager.setOnEventListener(null); // Unregister bằng cách set null
            listenerRegistered = false;
        } catch (Exception e) {
            // ignore
        }
    }

    public void showReservationDialogWithPickers(TableItem table) {
        if (table == null) return;

        // Inflate dialog_reservation.xml
        LayoutInflater inflater = LayoutInflater.from(host);
        View layout = inflater.inflate(R.layout.dialog_reservation, null);

        final EditText etName = layout.findViewById(R.id.et_res_name);
        final EditText etPhone = layout.findViewById(R.id.et_res_phone);
        final EditText etDate = layout.findViewById(R.id.et_res_date);
        final EditText etTime = layout.findViewById(R.id.et_res_time);

        // make date/time pickers work exactly as before
        final Calendar selectedCal = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new DatePickerDialog(host, (view, y, m, d) -> {
                selectedCal.set(Calendar.YEAR, y);
                selectedCal.set(Calendar.MONTH, m);
                selectedCal.set(Calendar.DAY_OF_MONTH, d);
                etDate.setText(dateFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        etTime.setFocusable(false);
        etTime.setClickable(true);
        etTime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new TimePickerDialog(host, (view, h, m) -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, h);
                selectedCal.set(Calendar.MINUTE, m);
                selectedCal.set(Calendar.SECOND, 0);
                etTime.setText(timeFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.HOUR_OF_DAY), selectedCal.get(Calendar.MINUTE), true).show();
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
            if (name.isEmpty() || phone.isEmpty() || dateStr.isEmpty() || timeStr.isEmpty()) {
                Toast.makeText(host, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

            String reservationAt = dateStr + " " + timeStr;
            Map<String,Object> body = new HashMap<>();
            body.put("reservationName", name);
            body.put("reservationPhone", phone);
            body.put("reservationAt", reservationAt);

            // Use reserve endpoint on server (server will set status reserved and schedule auto-release)
            tableRepository.reserveTable(table.getId(), body, new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem updatedTable) {
                    host.runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(host, "Đặt trước thành công", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) {
                            ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                        }

                        // Emit socket event locally to ensure other clients get update (best-effort)
                        try {
                            if (socketManager != null) {
                                JSONObject payload = new JSONObject();
                                try {
                                    payload.put("_id", updatedTable.getId());
                                } catch (Exception ignored) {}
                                try {
                                    payload.put("tableNumber", updatedTable.getTableNumber());
                                } catch (Exception ignored) {}
                                try {
                                    Object st = updatedTable.getStatus();
                                    String statusStr = (st != null) ? st.toString().toLowerCase() : "reserved";
                                    payload.put("status", statusStr);
                                } catch (Exception ignored) {}
                                try {
                                    // reservation fields may not exist on model getters; guard with try-catch
                                    try {
                                        payload.put("reservationName", updatedTable.getReservationName());
                                    } catch (Exception ignored) {}
                                    try {
                                        payload.put("reservationPhone", updatedTable.getReservationPhone());
                                    } catch (Exception ignored) {}
                                    try {
                                        payload.put("reservationAt", updatedTable.getReservationAt());
                                    } catch (Exception ignored) {}
                                } catch (Exception ignored) {}
                                try {
                                    payload.put("eventName", "table_reserved");
                                } catch (Exception ignored) {}

                                try {
                                    socketManager.connect();
                                } catch (Exception ignored) {}

                                try {
                                    socketManager.emitEvent("table_reserved", payload);
                                } catch (Exception e) {
                                    Log.w("ReservationHelper", "emit table_reserved failed", e);
                                }
                                try {
                                    socketManager.emitEvent("table_updated", payload);
                                } catch (Exception e) {
                                    Log.w("ReservationHelper", "emit table_updated failed", e);
                                }
                            }
                        } catch (Exception e) {
                            Log.w("ReservationHelper", "Failed to emit reservation socket event", e);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    host.runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(host, "Không thể đặt trước: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }));
        dialog.show();
    }

    /**
     * Cancel reservation now (manual). Client uses API to set status available.
     */
    public void cancelReservation(TableItem table) {
        if (table == null || table.getId() == null || table.getId().trim().isEmpty()) {
            Toast.makeText(host, "Bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (progressBar != null) progressBar.setVisibility(android.view.View.VISIBLE);

        Map<String,Object> body = new HashMap<>();
        body.put("status","available");
        body.put("reservationName","");
        body.put("reservationPhone","");
        body.put("reservationAt","");

        tableRepository.updateTable(table.getId(), body, new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem result) {
                host.runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(host, "Hủy đặt trước thành công", Toast.LENGTH_SHORT).show();
                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) {
                        ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                    }
                });
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(host, "Không thể hủy đặt trước: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}