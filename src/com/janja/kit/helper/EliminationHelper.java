package com.janja.kit.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.RectF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.LinearInterpolator;

public class EliminationHelper {
    private static final boolean SLOW_ANIMATIONS = false;
    private static final boolean CONSTRAIN_SWIPE = true;
    private static final boolean FADE_OUT_DURING_SWIPE = true;
    private static final boolean DISMISS_IF_SWIPED_FAR_ENOUGH = true;

    public static final int X = 0;
    public static final int Y = 1;

    private static LinearInterpolator sLinearInterpolator = new LinearInterpolator();

    private float SWIPE_ESCAPE_VELOCITY = 100f;
    private int DEFAULT_ESCAPE_ANIMATION_DURATION = 200;
    private int MAX_ESCAPE_ANIMATION_DURATION = 400;
    private int MAX_DISMISS_VELOCITY = 2000;

    private int SNAP_ANIM_LEN = SLOW_ANIMATIONS ? 1000 : 150;
    private float ALPHA_FADE_START = 0f;
    private float ALPHA_FADE_END = 0.5f;
    private float minAlpha = 0f;

    private float pagingTouchSlop;
    private Callback callback;
    private Handler handler;
    private int swipeDirection;
    private VelocityTracker velocityTracker;

    private float initialTouchPos;
    private boolean dragging;
    private View currView;
    private View currAnimView;
    private boolean canCurrViewBeDimissed;
    private float densityScale;

    private boolean longPressSent;
    private View.OnLongClickListener longPressListener;
    private Runnable watchLongPress;
    private long longPressTimeout;

    public EliminationHelper(int swipeDirection, Callback callback,
            float densityScale, float pagingTouchSlop) {

        this.callback = callback;
        this.swipeDirection = swipeDirection;
        this.velocityTracker = VelocityTracker.obtain();
        this.densityScale = densityScale;
        this.pagingTouchSlop = pagingTouchSlop;

        handler = new Handler();
        longPressTimeout = (long) (ViewConfiguration.getLongPressTimeout() * 1.5f);
    }

    public void recycle() {
        velocityTracker.recycle();
    }

    public void setLongPressListener(View.OnLongClickListener listener) {
        longPressListener = listener;
    }

    public void setDensityScale(float densityScale) {
        this.densityScale = densityScale;
    }

    public void setPagingTouchSlop(float pagingTouchSlop) {
        this.pagingTouchSlop = pagingTouchSlop;
    }

    private float getPos(MotionEvent ev) {
        return swipeDirection == X ? ev.getX() : ev.getY();
    }

    private float getTranslation(View v) {
        return swipeDirection == X ? v.getTranslationX() : v.getTranslationY();
    }

    private float getVelocity(VelocityTracker vt) {
        return swipeDirection == X ? vt.getXVelocity() : vt.getYVelocity();
    }

    private ObjectAnimator createTranslationAnimation(View v, float newPos) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(v,
                swipeDirection == X ? "translationX" : "translationY", newPos);
        return anim;
    }

    private float getPerpendicularVelocity(VelocityTracker vt) {
        return swipeDirection == X ? vt.getYVelocity() : vt.getXVelocity();
    }

    private void setTranslation(View v, float translate) {
        if (swipeDirection == X) {
            v.setTranslationX(translate);
        } else {
            v.setTranslationY(translate);
        }
    }

    private float getSize(View v) {
        return swipeDirection == X ? v.getMeasuredWidth() : v
                .getMeasuredHeight();
    }

    public void setMinAlpha(float minAlpha) {
        this.minAlpha = minAlpha;
    }

    private float getAlphaForOffset(View view) {
        float viewSize = getSize(view);
        final float fadeSize = ALPHA_FADE_END * viewSize;
        float result = 1.0f;
        float pos = getTranslation(view);
        if (pos >= viewSize * ALPHA_FADE_START) {
            result = 1.0f - (pos - viewSize * ALPHA_FADE_START) / fadeSize;
        } else if (pos < viewSize * (1.0f - ALPHA_FADE_START)) {
            result = 1.0f + (viewSize * ALPHA_FADE_START + pos) / fadeSize;
        }
        return Math.max(minAlpha, result);
    }

    private void updateAlphaFromOffset(View animView, boolean dismissable) {
        if (FADE_OUT_DURING_SWIPE && dismissable) {
            float alpha = getAlphaForOffset(animView);
            if (alpha != 0f && alpha != 1f) {
                animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            } else {
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            animView.setAlpha(getAlphaForOffset(animView));
        }
        invalidateGlobalRegion(animView);
    }

    public static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(view, new RectF(view.getLeft(), view.getTop(),
                view.getRight(), view.getBottom()));
    }

    public static void invalidateGlobalRegion(View view, RectF childBounds) {
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left),
                    (int) Math.floor(childBounds.top),
                    (int) Math.ceil(childBounds.right),
                    (int) Math.ceil(childBounds.bottom));
        }
    }

    public void removeLongPressCallback() {
        if (watchLongPress != null) {
            handler.removeCallbacks(watchLongPress);
            watchLongPress = null;
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                longPressSent = false;
                currView = callback.getChildAtPosition(ev);
                velocityTracker.clear();
                if (currView != null) {
                    currAnimView = callback.getChildContentView(currView);
                    canCurrViewBeDimissed = callback
                            .canChildBeDismissed(currView);
                    velocityTracker.addMovement(ev);
                    initialTouchPos = getPos(ev);

                    if (longPressListener != null) {
                        if (watchLongPress == null) {
                            watchLongPress = new Runnable() {
                                @Override
                                public void run() {
                                    if (currView != null && !longPressSent) {
                                        longPressSent = true;
                                        currView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                                        longPressListener.onLongClick(currView);
                                    }
                                }
                            };
                        }
                        handler.postDelayed(watchLongPress, longPressTimeout);
                    }

                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (currView != null && !longPressSent) {
                    velocityTracker.addMovement(ev);
                    float pos = getPos(ev);
                    float delta = pos - initialTouchPos;
                    if (Math.abs(delta) > pagingTouchSlop) {
                        callback.onBeginDrag(currView);
                        dragging = true;
                        initialTouchPos = getPos(ev)
                                - getTranslation(currAnimView);

                        removeLongPressCallback();
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                currView = null;
                currAnimView = null;
                longPressSent = false;
                removeLongPressCallback();
                break;
        }
        return dragging;
    }

    public void dismissChild(final View view, float velocity) {
        final View animView = callback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = callback
                .canChildBeDismissed(view);
        float newPos;

        if (velocity < 0
                || (velocity == 0 && getTranslation(animView) < 0)
                || (velocity == 0 && getTranslation(animView) == 0 && swipeDirection == Y)) {
            newPos = -getSize(animView);
        } else {
            newPos = getSize(animView);
        }
        int duration = MAX_ESCAPE_ANIMATION_DURATION;
        if (velocity != 0) {
            duration = Math
                    .min(duration, (int) (Math.abs(newPos
                            - getTranslation(animView)) * 1000f / Math
                            .abs(velocity)));
        } else {
            duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
        }

        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator anim = createTranslationAnimation(animView, newPos);
        anim.setInterpolator(sLinearInterpolator);
        anim.setDuration(duration);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                callback.onChildDismissed(view);
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                updateAlphaFromOffset(animView, canAnimViewBeDismissed);
            }
        });
        anim.start();
    }

    public void snapChild(final View view, float velocity) {
        final View animView = callback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = callback
                .canChildBeDismissed(animView);
        ObjectAnimator anim = createTranslationAnimation(animView, 0);
        int duration = SNAP_ANIM_LEN;
        anim.setDuration(duration);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                updateAlphaFromOffset(animView, canAnimViewBeDismissed);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animator) {
                updateAlphaFromOffset(animView, canAnimViewBeDismissed);
            }
        });
        anim.start();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (longPressSent) {
            return true;
        }

        if (!dragging) {
            removeLongPressCallback();
            return false;
        }

        velocityTracker.addMovement(ev);
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (currView != null) {
                    float delta = getPos(ev) - initialTouchPos;
                    if (CONSTRAIN_SWIPE
                            && !callback.canChildBeDismissed(currView)) {
                        float size = getSize(currAnimView);
                        float maxScrollDistance = 0.15f * size;
                        if (Math.abs(delta) >= size) {
                            delta = delta > 0 ? maxScrollDistance
                                    : -maxScrollDistance;
                        } else {
                            delta = maxScrollDistance
                                    * (float) Math.sin((delta / size)
                                            * (Math.PI / 2));
                        }
                    }
                    setTranslation(currAnimView, delta);

                    updateAlphaFromOffset(currAnimView, canCurrViewBeDimissed);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currView != null) {
                    float maxVelocity = MAX_DISMISS_VELOCITY * densityScale;
                    velocityTracker.computeCurrentVelocity(1000 /* px/sec */,
                            maxVelocity);
                    float escapeVelocity = SWIPE_ESCAPE_VELOCITY * densityScale;
                    float velocity = getVelocity(velocityTracker);
                    float perpendicularVelocity = getPerpendicularVelocity(velocityTracker);

                    boolean childSwipedFarEnough = DISMISS_IF_SWIPED_FAR_ENOUGH
                            && Math.abs(getTranslation(currAnimView)) > 0.4 * getSize(currAnimView);
                    boolean childSwipedFastEnough = (Math.abs(velocity) > escapeVelocity)
                            && (Math.abs(velocity) > Math
                                    .abs(perpendicularVelocity))
                            && (velocity > 0) == (getTranslation(currAnimView) > 0);

                    boolean dismissChild = callback
                            .canChildBeDismissed(currView)
                            && (childSwipedFastEnough || childSwipedFarEnough);

                    if (dismissChild) {
                        dismissChild(currView, childSwipedFastEnough ? velocity
                                : 0f);
                    } else {
                        callback.onDragCancelled(currView);
                        snapChild(currView, velocity);
                    }
                }
                break;
        }
        return true;
    }

    public interface Callback {
        View getChildAtPosition(MotionEvent ev);

        View getChildContentView(View v);

        boolean canChildBeDismissed(View v);

        void onBeginDrag(View v);

        void onChildDismissed(View v);

        void onDragCancelled(View v);
    }
}
