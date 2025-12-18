package com.ph48845.datn_qlnh_rmis.ui.employee;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.User;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter cho danh sách nhân viên
 */
public class EmployeeAdapter extends RecyclerView.Adapter<EmployeeAdapter.ViewHolder> {
    private List<User> employees;
    private Context context;

    public EmployeeAdapter(List<User> employees, Context context) {
        this.employees = employees;
        this.context = context;
    }

    public void updateList(List<User> newList) {
        this.employees = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employee, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User employee = employees.get(position);
        holder.bind(employee, context);
    }

    @Override
    public int getItemCount() {
        return employees != null ? employees.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmployeeName, tvEmployeeRole, tvEmployeePhone, tvEmployeeStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvEmployeeName = itemView.findViewById(R.id.tvEmployeeName);
            tvEmployeeRole = itemView.findViewById(R.id.tvEmployeeRole);
            tvEmployeePhone = itemView.findViewById(R.id.tvEmployeePhone);
            tvEmployeeStatus = itemView.findViewById(R.id.tvEmployeeStatus);
        }

        void bind(User employee, Context context) {
            tvEmployeeName.setText(employee.getName() != null ? employee.getName() : employee.getUsername());
            tvEmployeeRole.setText(getRoleText(employee.getRole()));
            tvEmployeePhone.setText(employee.getPhoneNumber() != null ? employee.getPhoneNumber() : "Chưa cập nhật");

            // Set status
            if (employee.isActive()) {
                tvEmployeeStatus.setText("Hoạt động");
                tvEmployeeStatus.setBackgroundResource(R.drawable.bg_status_ok);
            } else {
                tvEmployeeStatus.setText("Ngưng");
                tvEmployeeStatus.setBackgroundResource(R.drawable.bg_status_error);
            }

            // Click để xem chi tiết
            itemView.setOnClickListener(v -> showEmployeeDetails(context, employee));
        }

        private String getRoleText(String role) {
            if (role == null)
                return "Phục vụ";
            switch (role) {
                case "admin":
                    return "ADMIN";
                case "kitchen":
                    return "Bếp";
                case "cashier":
                    return "Thu ngân";
                case "waiter":
                    return "Phục vụ";
                default:
                    return "Phục vụ";
            }
        }

        private void showEmployeeDetails(Context context, User employee) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Thông tin nhân viên");

            StringBuilder details = new StringBuilder();
            details.append("Tên: ").append(employee.getName() != null ? employee.getName() : employee.getUsername())
                    .append("\n");
            details.append("Username: ").append(employee.getUsername()).append("\n");
            details.append("Vai trò: ").append(getRoleText(employee.getRole())).append("\n");
            details.append("Số điện thoại: ")
                    .append(employee.getPhoneNumber() != null ? employee.getPhoneNumber() : "Chưa cập nhật")
                    .append("\n");
            details.append("Email: ").append(employee.getEmail() != null ? employee.getEmail() : "Chưa cập nhật")
                    .append("\n");
            details.append("Trạng thái: ").append(employee.isActive() ? "Hoạt động" : "Ngưng hoạt động").append("\n\n");

            // Mức lương (giả định dựa trên role)
            details.append("─────────────────────\n");
            details.append("THÔNG TIN LƯƠNG:\n");
            details.append("─────────────────────\n");

            NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
            double salary = getSalaryByRole(employee.getRole());

            details.append("Lương cơ bản: ").append(vnFormat.format(salary)).append(" đ/tháng\n");
            details.append("Phụ cấp: ").append(vnFormat.format(salary * 0.1)).append(" đ/tháng\n");
            details.append("Tổng lương: ").append(vnFormat.format(salary * 1.1)).append(" đ/tháng");

            builder.setMessage(details.toString());
            builder.setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        }

        private double getSalaryByRole(String role) {
            if (role == null)
                return 5000000;
            switch (role) {
                case "admin":
                    return 15000000;
                case "kitchen":
                    return 8000000;
                case "cashier":
                    return 7000000;
                case "waiter":
                    return 5000000;
                default:
                    return 5000000;
            }
        }
    }
}
