package com.ph48845.datn_qlnh_rmis.ui.shift;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Shift;

import java.util.List;

/**
 * Adapter cho danh sách ca làm việc
 */
public class ShiftAdapter extends RecyclerView.Adapter<ShiftAdapter.ViewHolder> {
    private List<Shift> shifts;

    public ShiftAdapter(List<Shift> shifts) {
        this.shifts = shifts;
    }

    public void updateList(List<Shift> newList) {
        this.shifts = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shift, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shift shift = shifts.get(position);
        holder.bind(shift);
    }

    @Override
    public int getItemCount() {
        return shifts != null ? shifts.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvShiftName, tvShiftTime, tvStatus, tvEmployeeCount;
        RecyclerView rvEmployees;
        EmployeeShiftAdapter employeeAdapter;

        ViewHolder(View itemView) {
            super(itemView);
            tvShiftName = itemView.findViewById(R.id.tvShiftName);
            tvShiftTime = itemView.findViewById(R.id.tvShiftTime);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvEmployeeCount = itemView.findViewById(R.id.tvEmployeeCount);
            rvEmployees = itemView.findViewById(R.id.rvEmployees);

            // Setup employee RecyclerView
            rvEmployees.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
        }

        void bind(Shift shift) {
            tvShiftName.setText(shift.getName());
            tvShiftTime.setText(shift.getStartTime() + " - " + shift.getEndTime());
            tvStatus.setText(shift.getStatusText());

            // Set status background color
            switch (shift.getStatus()) {
                case "ongoing":
                    tvStatus.setBackgroundResource(R.drawable.bg_status_ok);
                    break;
                case "completed":
                    tvStatus.setBackgroundResource(R.drawable.bg_status_warning);
                    break;
                case "cancelled":
                    tvStatus.setBackgroundResource(R.drawable.bg_status_error);
                    break;
                default: // scheduled
                    tvStatus.setBackgroundResource(R.drawable.bg_status_ok);
                    break;
            }

            // Employee list
            if (shift.getEmployees() != null && !shift.getEmployees().isEmpty()) {
                employeeAdapter = new EmployeeShiftAdapter(shift.getEmployees());
                rvEmployees.setAdapter(employeeAdapter);
                rvEmployees.setVisibility(View.VISIBLE);

                int presentCount = shift.getPresentCount();
                int totalCount = shift.getEmployeeCount();
                tvEmployeeCount.setText(String.format("Tổng: %d nhân viên (%d có mặt)", totalCount, presentCount));
            } else {
                rvEmployees.setVisibility(View.GONE);
                tvEmployeeCount.setText("Chưa có nhân viên");
            }
        }
    }
}
