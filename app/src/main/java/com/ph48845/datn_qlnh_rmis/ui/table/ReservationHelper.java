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

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * ReservationHelper:
 * - When user confirms a reservation, server will auto-cancel (server side).
 * - Client does not schedule in-process auto-cancel; it relies on socket events from server.
 */
public class ReservationHelper {

    private final android.app.Activity host;
    private final TableRepository tableRepository;
    private final ProgressBar progressBar;

    // Format used for reservationAt (kept for potential future use)
    private static final String RESERVATION_FORMAT = "yyyy-MM-dd HH:mm";

    public ReservationHelper(android.app.Activity host, TableRepository tableRepository, ProgressBar progressBar) {
        this.host = host;
        this.tableRepository = tableRepository;
        this.progressBar = progressBar;
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