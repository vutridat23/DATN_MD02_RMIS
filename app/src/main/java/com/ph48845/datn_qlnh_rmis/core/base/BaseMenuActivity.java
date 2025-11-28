package com.ph48845.datn_qlnh_rmis.core.base;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.auth.LoginActivity;

public class BaseMenuActivity extends AppCompatActivity {

    public void showMoodDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mood, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();

        ImageView happy = dialogView.findViewById(R.id.happy);
        ImageView neutral = dialogView.findViewById(R.id.neutral);
        ImageView sad = dialogView.findViewById(R.id.sad);

        happy.setOnClickListener(v -> {
            Toast.makeText(this, "Chúc bạn một ngày tốt lành!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        neutral.setOnClickListener(v -> showMoodFeedbackDialog(dialog));
        sad.setOnClickListener(v -> showMoodFeedbackDialog(dialog));
    }


    // Tạo icon bo tròn + viền
    public ImageView createRoundedIcon(int drawableRes, LinearLayout.LayoutParams params) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(drawableRes);
        iv.setLayoutParams(params);
        iv.setBackgroundResource(R.drawable.circle_background_dialog); // shape oval + viền nhẹ
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(15, 15, 15, 15);
        return iv;
    }

    // Dialog nhập nguyên nhân
    public void showMoodFeedbackDialog(AlertDialog parentDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mood_feedback, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_round);

        EditText etReason = dialogView.findViewById(R.id.etReason);
        Button okBtn = dialogView.findViewById(R.id.btnOk);
        Button cancelBtn = dialogView.findViewById(R.id.btnCancel);

        okBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Chúc bạn một ngày tốt lành!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            parentDialog.dismiss();
        });

        cancelBtn.setOnClickListener(v -> {
            dialog.dismiss();
            parentDialog.dismiss();
            Toast.makeText(this, "Chúc bạn một ngày tốt lành!", Toast.LENGTH_SHORT).show();
        });
    }


    public void showContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_contact, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_contact);

        TextView tvPhone = dialogView.findViewById(R.id.tvPhone);
        Button btnCall = dialogView.findViewById(R.id.btnCall);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        btnCall.setOnClickListener(v -> {
            callPhoneNumber(tvPhone.getText().toString().replace("Số điện thoại: ", "").trim());
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
    }


    public String pendingPhone = "";

    public ActivityResultLauncher<String> callPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    makeCall(pendingPhone);
                } else {
                    Toast.makeText(this, "Bạn cần cấp quyền để gọi điện", Toast.LENGTH_SHORT).show();
                }
            });


    // Thực hiện cuộc gọi trực tiếp
    public void callPhoneNumber(String phone) {
        pendingPhone = phone;

        // Nếu đã được cấp quyền → gọi ngay
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {

            makeCall(phone);

        } else {
            // Nếu chưa được cấp quyền → xin quyền
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
        }
    }

    public void makeCall(String phone) {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + phone));
        startActivity(intent);
    }


    public void logout() {
        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();  // xóa isLoggedIn, userId, userRole, fullName

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
