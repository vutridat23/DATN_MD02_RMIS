package com.ph48845.datn_qlnh_rmis.ui.phucvu.notification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

public class SwipeToDismissTouchListener implements View.OnTouchListener {

    private int mSlop;
    private int mMinFlingVelocity;
    private int mMaxFlingVelocity;
    private long mAnimationTime;

    private View mView;
    private DismissCallbacks mCallbacks;
    private int mViewHeight = 1;

    private float mDownY;
    private boolean mSwiping;
    private Object mToken;
    private VelocityTracker mVelocityTracker;
    private float mTranslationY;

    public interface DismissCallbacks {
        boolean canDismiss(Object token);
        void onDismiss(View view, Object token);
    }

    public SwipeToDismissTouchListener(View view, Object token, DismissCallbacks callbacks) {
        ViewConfiguration vc = ViewConfiguration.get(view.getContext());
        mSlop = vc.getScaledTouchSlop();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity() * 16;
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mAnimationTime = view.getContext().getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        mView = view;
        mToken = token;
        mCallbacks = callbacks;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        motionEvent.offsetLocation(0, mTranslationY);

        if (mViewHeight < 2) {
            mViewHeight = mView.getHeight();
        }

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:  {
                mDownY = motionEvent.getRawY();
                if (mCallbacks != null && mCallbacks.canDismiss(mToken)) {
                    mVelocityTracker = VelocityTracker.obtain();
                    mVelocityTracker.addMovement(motionEvent);
                }
                // return false to allow other handlers initially
                return false;
            }

            case MotionEvent.ACTION_UP: {
                if (mVelocityTracker == null) {
                    break;
                }

                float deltaY = motionEvent.getRawY() - mDownY;
                mVelocityTracker.addMovement(motionEvent);
                mVelocityTracker.computeCurrentVelocity(1000);
                float velocityY = mVelocityTracker.getYVelocity();
                float absVelocityY = Math.abs(velocityY);
                boolean dismiss = false;
                boolean dismissUp = false;

                if (Math.abs(deltaY) > mViewHeight / 2 && mSwiping) {
                    dismiss = true;
                    dismissUp = deltaY < 0;
                } else if (mMinFlingVelocity <= absVelocityY && absVelocityY <= mMaxFlingVelocity
                        && mSwiping) {
                    dismiss = true;
                    dismissUp = mVelocityTracker.getYVelocity() < 0;
                }

                if (dismiss) {
                    mView.animate()
                            .translationY(dismissUp ? -mViewHeight : mViewHeight)
                            .alpha(0)
                            .setDuration(mAnimationTime)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    performDismiss();
                                }
                            });
                } else if (mSwiping) {
                    mView.animate()
                            .translationY(0)
                            .alpha(1)
                            .setDuration(mAnimationTime)
                            .setListener(null);
                }

                mVelocityTracker.recycle();
                mVelocityTracker = null;
                mTranslationY = 0;
                mDownY = 0;
                mSwiping = false;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mVelocityTracker == null) {
                    break;
                }

                mView.animate()
                        .translationY(0)
                        .alpha(1)
                        .setDuration(mAnimationTime)
                        .setListener(null);
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                mTranslationY = 0;
                mDownY = 0;
                mSwiping = false;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker == null) {
                    break;
                }

                mVelocityTracker.addMovement(motionEvent);
                float deltaY = motionEvent.getRawY() - mDownY;

                if (Math.abs(deltaY) > mSlop) {
                    mSwiping = true;
                    if (mView.getParent() != null) mView.getParent().requestDisallowInterceptTouchEvent(true);

                    MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
                            (motionEvent.getActionIndex() << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                    mView.onTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                }

                if (mSwiping) {
                    mTranslationY = deltaY;
                    mView.setTranslationY(deltaY);
                    mView.setAlpha(Math.max(0f, Math.min(1f,
                            1f - 2f * Math.abs(deltaY) / mViewHeight)));
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private void performDismiss() {
        final ViewGroup.LayoutParams lp = mView.getLayoutParams();
        final int originalHeight = mView.getHeight();

        ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(mAnimationTime);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                try {
                    if (mCallbacks != null) mCallbacks.onDismiss(mView, mToken);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    mView.setAlpha(1f);
                    mView.setTranslationY(0);
                    lp.height = originalHeight;
                    mView.setLayoutParams(lp);
                }
            }
        });

        animator.addUpdateListener(valueAnimator -> {
            lp.height = (Integer) valueAnimator.getAnimatedValue();
            mView.setLayoutParams(lp);
        });

        animator.start();
    }
}