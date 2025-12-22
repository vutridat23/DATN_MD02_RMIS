package com.ph48845.datn_qlnh_rmis.ui.table.fragment;



import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

/**
 * Simple DialogFragment that shows auto-release notification.
 * Use AutoReleaseDialogFragment.newInstance(tableNumber).show(getSupportFragmentManager(), "autoRelease");
 */
public class AutoReleaseDialogFragment extends DialogFragment {

    private static final String ARG_TABLE_NUMBER = "arg_table_number";

    public static AutoReleaseDialogFragment newInstance(int tableNumber) {
        AutoReleaseDialogFragment f = new AutoReleaseDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TABLE_NUMBER, tableNumber);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        int table = -1;
        if (getArguments() != null) {
            table = getArguments().getInt(ARG_TABLE_NUMBER, -1);
        }
        String title = "Thông báo";
        String msg = "Bàn " + (table > 0 ? table : "") + " đã tự động hủy đặt trước.";
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    // nothing extra to do here
                });
        return builder.create();
    }
}