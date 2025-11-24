package com.ph48845.datn_qlnh_rmis.ui.phucvu;



import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.MenuAdapter;

import java.util.List;

/**
 * Handles long-press on a menu RecyclerView item to show/edit a persistent note.
 */
public class MenuLongPressHandler {

    public interface NoteStore {
        String getNoteForMenu(String menuId);
        void putNoteForMenu(String menuId, String note);
    }

    private final Context ctx;
    private final RecyclerView rvMenu;
    private final MenuAdapter menuAdapter;
    private final NoteStore noteStore;

    public MenuLongPressHandler(Context ctx, RecyclerView rvMenu, MenuAdapter menuAdapter, NoteStore noteStore) {
        this.ctx = ctx;
        this.rvMenu = rvMenu;
        this.menuAdapter = menuAdapter;
        this.noteStore = noteStore;
    }

    public void setup() {
        final GestureDetector gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                View child = rvMenu.findChildViewUnder(e.getX(), e.getY());
                if (child == null) return;
                int pos = rvMenu.getChildAdapterPosition(child);
                if (pos == RecyclerView.NO_POSITION) return;

                List<MenuItem> menus = null;
                try { menus = menuAdapter != null ? menuAdapter.getItems() : null; } catch (Exception ignored) {}
                if (menus == null || pos < 0 || pos >= menus.size()) return;
                MenuItem menu = menus.get(pos);
                if (menu == null) return;

                showMenuItemNoteDialog(menu);
            }
        });

        rvMenu.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                try {
                    gestureDetector.onTouchEvent(e);
                } catch (Exception ignored) {}
                return false;
            }
            @Override public void onTouchEvent(RecyclerView rv, MotionEvent e) {}
            @Override public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    private void showMenuItemNoteDialog(MenuItem menu) {
        if (menu == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Ghi chú cho: " + (menu.getName() != null ? menu.getName() : ""));

        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(1);
        input.setMaxLines(5);

        String prev = "";
        try { if (noteStore != null) prev = noteStore.getNoteForMenu(menu.getId()); } catch (Exception ignored) {}
        input.setText(prev);

        builder.setView(input);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String note = input.getText() != null ? input.getText().toString().trim() : "";
            if (noteStore != null) noteStore.putNoteForMenu(menu.getId(), note);
            if (note != null && !note.isEmpty()) {
                Toast.makeText(ctx, "Đã lưu ghi chú cho " + menu.getName(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "Đã xóa ghi chú cho " + menu.getName(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}