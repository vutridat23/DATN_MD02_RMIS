package com.ph48845.datn_qlnh_rmis.ui.phucvu.notification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.InAppNotification;

public class InAppNotificationView extends FrameLayout {

    private CardView cardNotification;
    private ImageView ivIcon;
    private TextView tvTitle;
    private TextView tvMessage;
    private ImageButton btnClose;

    private InAppNotification currentNotification;
    private OnNotificationClickListener clickListener;
    private Handler hideHandler;
    private Runnable hideRunnable;

    public interface OnNotificationClickListener {
        void onNotificationClick(InAppNotification notification);
        void onNotificationDismissed(InAppNotification notification);
    }

    public InAppNotificationView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public InAppNotificationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_in_app_notification, this, true);

        cardNotification = findViewById(R.id.card_notification);
        ivIcon = findViewById(R.id.iv_notification_icon);
        tvTitle = findViewById(R.id.tv_notification_title);
        tvMessage = findViewById(R.id.tv_notification_message);
        btnClose = findViewById(R.id.btn_notification_close);

        hideHandler = new Handler(Looper.getMainLooper());

        cardNotification.setOnClickListener(v -> {
            if (clickListener != null && currentNotification != null) {
                try {
                    clickListener.onNotificationClick(currentNotification);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            hide(true);
        });

        btnClose.setOnClickListener(v -> hide(true));

        setupSwipeToDismiss();
    }

    public void show(InAppNotification notification) {
        if (notification == null) return;

        this.currentNotification = notification;

        try {
            tvTitle.setText(notification.getTitle());
            tvMessage.setText(notification.getMessage());

            if (notification.getIconRes() != 0) {
                ivIcon.setImageResource(notification.getIconRes());
            } else {
                ivIcon.setImageResource(getDefaultIcon(notification.getType()));
            }

            cardNotification.setCardBackgroundColor(getColorForType(notification.getType()));

            if (hideRunnable != null) {
                hideHandler.removeCallbacks(hideRunnable);
                hideRunnable = null;
            }

            setVisibility(VISIBLE);

            // Ensure animation starts after layout (so we can use actual height)
            cardNotification.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    cardNotification.getViewTreeObserver().removeOnPreDrawListener(this);
                    float startY = -cardNotification.getHeight() - dpToPx(8);
                    cardNotification.setTranslationY(startY);
                    ObjectAnimator slideDown = ObjectAnimator.ofFloat(cardNotification, "translationY", startY, 0f);
                    slideDown.setDuration(400);
                    slideDown.setInterpolator(new DecelerateInterpolator());
                    slideDown.start();
                    return true;
                }
            });

            if (notification.getDuration() > 0) {
                hideRunnable = () -> hide(false);
                hideHandler.postDelayed(hideRunnable, notification.getDuration());
            }
        } catch (Throwable t) {
            t.printStackTrace();
            // fallback: ensure visible
            setVisibility(VISIBLE);
        }
    }

    public void hide(boolean userDismissed) {
        try {
            if (hideRunnable != null) {
                hideHandler.removeCallbacks(hideRunnable);
                hideRunnable = null;
            }

            ObjectAnimator slideUp = ObjectAnimator.ofFloat(cardNotification, "translationY", cardNotification.getTranslationY(), -cardNotification.getHeight() - dpToPx(8));
            slideUp.setDuration(300);
            slideUp.setInterpolator(new DecelerateInterpolator());
            slideUp.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    try {
                        setVisibility(GONE);
                        if (clickListener != null && currentNotification != null) {
                            try {
                                clickListener.onNotificationDismissed(currentNotification);
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    } finally {
                        currentNotification = null;
                    }
                }
            });
            slideUp.start();
        } catch (Throwable t) {
            t.printStackTrace();
            setVisibility(GONE);
            if (clickListener != null && currentNotification != null) {
                try { clickListener.onNotificationDismissed(currentNotification); } catch (Throwable ignored) {}
            }
            currentNotification = null;
        }
    }

    private void setupSwipeToDismiss() {
        cardNotification.setOnTouchListener(new SwipeToDismissTouchListener(
                cardNotification,
                null,
                new SwipeToDismissTouchListener.DismissCallbacks() {
                    @Override
                    public boolean canDismiss(Object token) {
                        return true;
                    }

                    @Override
                    public void onDismiss(View view, Object token) {
                        hide(true);
                    }
                }
        ));
    }

    private int getColorForType(InAppNotification.Type type) {
        switch (type) {
            case SUCCESS:
            case ORDER_READY:
                return Color.parseColor("#4CAF50");
            case WARNING:
            case CHECK_ITEMS:
            case INGREDIENT_LOW:
                return Color.parseColor("#FF9800");
            case ERROR:
                return Color.parseColor("#F44336");
            case ORDER_NEW:
                return Color.parseColor("#9C27B0");
            case ORDER_UPDATED:
            case TABLE_UPDATED:
            case TEMP_CALC:
                return Color.parseColor("#2196F3");
            case MENU_UPDATED:
                return Color.parseColor("#00BCD4");
            case INFO:
            default:
                return Color.parseColor("#2196F3");
        }
    }

    private int getDefaultIcon(InAppNotification.Type type) {
        switch (type) {
            case SUCCESS:
            case ORDER_READY:
                return android.R.drawable.ic_menu_agenda;
            case WARNING:
            case ERROR:
                return android.R.drawable.ic_dialog_alert;
            case ORDER_NEW:
            case ORDER_UPDATED:
            case TABLE_UPDATED:
                return android.R.drawable.ic_menu_info_details;
            case CHECK_ITEMS:
                return android.R.drawable.ic_menu_search;
            case TEMP_CALC:
                return android.R.drawable.ic_menu_view;
            case MENU_UPDATED:
                return android.R.drawable.ic_menu_edit;
            case INGREDIENT_LOW:
                return android.R.drawable.ic_dialog_alert;
            case INFO:
            default:
                return android.R.drawable.ic_dialog_info;
        }
    }

    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.clickListener = listener;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}