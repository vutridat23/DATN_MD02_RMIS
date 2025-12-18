package com.ph48845.datn_qlnh_rmis.ui.shift;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Shift;

import java.util.List;

/**
 * Adapter cho danh sách nhân viên trong ca làm việc
 */
public class EmployeeShiftAdapter extends RecyclerView.Adapter<EmployeeShiftAdapter.ViewHolder> {
    private List<Shift.ShiftEmployee> employees;

    public EmployeeShiftAdapter(List<Shift.ShiftEmployee> employees) {
        this.employees = employees;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employee_shift, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shift.ShiftEmployee employee = employees.get(position);
        holder.bind(employee);
    }

    @Override
    public int getItemCount() {
        return employees != null ? employees.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmployeeName, tvEmployeeRole, tvEmployeeStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvEmployeeName = itemView.findViewById(R.id.tvEmployeeName);
            tvEmployeeRole = itemView.findViewById(R.id.tvEmployeeRole);
            tvEmployeeStatus = itemView.findViewById(R.id.tvEmployeeStatus);
        }

        void bind(Shift.ShiftEmployee employee) {
            tvEmployeeName.setText(employee.getEmployeeName());
            tvEmployeeRole.setText(employee.getEmployeeRole());
            tvEmployeeStatus.setText(employee.getStatusText());

            // Set status background color
            switch (employee.getStatus()) {
                case "present":
                    tvEmployeeStatus.setBackgroundResource(R.drawable.bg_status_ok);
                    break;
                case "late":
                    tvEmployeeStatus.setBackgroundResource(R.drawable.bg_status_warning);
                    break;
                case "absent":
                    tvEmployeeStatus.setBackgroundResource(R.drawable.bg_status_error);
                    break;
                default: // scheduled
                    tvEmployeeStatus.setBackgroundResource(R.drawable.bg_status_ok);
                    break;
            }
        }
    }
}
