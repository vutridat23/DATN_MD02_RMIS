package com.ph48845.datn_qlnh_rmis.ui.phucvu.notification;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.ph48845.datn_qlnh_rmis.data.model.InAppNotification;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;

public class NotificationManager {

    private static final String TAG = "NotificationManager";
    private static NotificationManager instance;

    private InAppNotificationView notificationView;
    private Queue<InAppNotification> notificationQueue;
    private boolean isShowing;

    // store last provided click listener so we can re-init if needed
    private InAppNotificationView.OnNotificationClickListener initialListener;

    // keep weak reference to activity so show() can try to re-init if notificationView is null
    private WeakReference<Activity> activityRef;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private NotificationManager() {
        notificationQueue = new LinkedList<>();
        isShowing = false;
    }

    public static synchronized NotificationManager getInstance() {
        if (instance == null) instance = new NotificationManager();
        return instance;
    }

    /**
     * Initialize notification overlay attached to provided Activity.
     * Safe to call multiple times. Will log detailed status for debugging.
     */
    public void init(final Activity activity, InAppNotificationView.OnNotificationClickListener listener) {
        if (activity == null) {
            Log.w(TAG, "init: activity is null");
            return;
        }

        // store references (weak)
        activityRef = new WeakReference<>(activity);
        initialListener = listener;

        // ensure run on UI thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> initInternal(activity, listener));
        } else {
            initInternal(activity, listener);
        }
    }

    private synchronized void initInternal(Activity activity, InAppNotificationView.OnNotificationClickListener listener) {
        try {
            if (notificationView != null) {
                Log.d(TAG, "init: notificationView already exists, skipping add");
                // Update listener if provided
                if (listener != null) notificationView.setOnNotificationClickListener(listener);
                return;
            }

            View root = activity.findViewById(android.R.id.content);
            if (!(root instanceof ViewGroup)) {
                Log.w(TAG, "init: root is not ViewGroup or is null: " + (root == null ? "null" : root.getClass().getName()));
                return;
            }

            ViewGroup rootView = (ViewGroup) root;
            notificationView = new InAppNotificationView(activity);
            if (listener != null) {
                notificationView.setOnNotificationClickListener(listener);
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
            );

            rootView.addView(notificationView, lp);
            Log.d(TAG, "init: notificationView added to root (class=" + rootView.getClass().getSimpleName() + ")");
        } catch (Throwable e) {
            Log.w(TAG, "init: failed to add notificationView", e);
            notificationView = null;
        }
    }

    /**
     * Show a notification. This method is safe to call from any thread.
     * Will attempt to re-init notificationView from stored activityRef if needed.
     */
    public void show(final InAppNotification notification) {
        if (notification == null) {
            Log.w(TAG, "show: notification is null -> ignore");
            return;
        }

        Log.d(TAG, "show: called for [" + notification.getTitle() + "] on thread=" + Thread.currentThread().getName());

        // ensure UI thread for view operations
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> showInternal(notification));
        } else {
            showInternal(notification);
        }
    }

    private synchronized void showInternal(InAppNotification notification) {
        Log.d(TAG, "showInternal: checking notificationView");
        if (notificationView == null) {
            Log.w(TAG, "showInternal: notificationView is null - attempting re-init from stored Activity");

            Activity act = activityRef != null ? activityRef.get() : null;
            if (act != null) {
                Log.d(TAG, "showInternal: re-init using stored Activity");
                initInternal(act, initialListener);
            } else {
                Log.w(TAG, "showInternal: no stored Activity available to re-init");
            }

            if (notificationView == null) {
                Log.w(TAG, "showInternal: still null after re-init attempt -> dropping notification: " + notification.getTitle());
                return;
            }
        }

        // now we have a notificationView
        try {
            Log.d(TAG, "showInternal: enqueue notification -> " + notification.getTitle());
            notificationQueue.offer(notification);
            if (!isShowing) {
                showNext();
            } else {
                Log.d(TAG, "showInternal: another notification is showing, queued");
            }
        } catch (Throwable t) {
            Log.w(TAG, "showInternal: failed to enqueue/show notification", t);
        }
    }

    private synchronized void showNext() {
        if (notificationView == null) {
            Log.w(TAG, "showNext: notificationView null, cannot show next");
            isShowing = false;
            notificationQueue.clear();
            return;
        }
        if (notificationQueue.isEmpty()) {
            isShowing = false;
            Log.d(TAG, "showNext: queue empty");
            return;
        }
        final InAppNotification next = notificationQueue.poll();
        if (next == null) {
            isShowing = false;
            Log.d(TAG, "showNext: polled null");
            return;
        }
        isShowing = true;
        Log.d(TAG, "showNext: showing -> " + next.getTitle());
        try {
            notificationView.show(next);
        } catch (Throwable t) {
            Log.w(TAG, "showNext: exception during notificationView.show", t);
            isShowing = false;
            // try next one
            mainHandler.post(this::showNext);
        }
    }

    public synchronized void clear() {
        Log.d(TAG, "clear: clearing queue and hiding view");
        notificationQueue.clear();
        if (notificationView != null) {
            try { notificationView.hide(true); } catch (Throwable ignored) {}
        }
        isShowing = false;
    }

    public synchronized void destroy() {
        Log.d(TAG, "destroy: cleaning up notificationView");
        clear();
        if (notificationView != null) {
            try {
                View parent = (View) notificationView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(notificationView);
                    Log.d(TAG, "destroy: removed notificationView from parent");
                }
            } catch (Throwable t) {
                Log.w(TAG, "destroy: error removing view", t);
            }
        }
        notificationView = null;
        initialListener = null;
        activityRef = null;
    }
}