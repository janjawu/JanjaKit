package com.janja.kit.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ScrollView;

import com.janja.kit.helper.EliminationHelper;

public class EliminationScrollView extends ScrollView implements
        EliminationHelper.Callback {

    private EliminationHelper helper;
    private ViewGroup rootView;
    private EliminationScrollViewListener eliminationListener;

    public EliminationScrollView(Context context) {
        super(context);
        initWithContext(context);
    }

    public EliminationScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initWithContext(context);
    }

    public EliminationScrollView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        initWithContext(context);
    }

    private void initWithContext(Context context) {
        float densityScale = getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(context)
                .getScaledPagingTouchSlop();
        helper = new EliminationHelper(EliminationHelper.X, this, densityScale,
                pagingTouchSlop);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setScrollbarFadingEnabled(true);
        boolean hasChild = getChildCount() > 0;
        if (hasChild) {
            View child = getChildAt(0);
            if (child instanceof ViewGroup) {
                rootView = (ViewGroup) child;
            }
        }
    }

    @Override
    public void removeViewInLayout(final View view) {
        dismissChild(view);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return helper.onInterceptTouchEvent(ev)
                || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return helper.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    public void dismissChild(View v) {
        helper.dismissChild(v, 0);
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {

        if (rootView != null) {
            final float x = ev.getX() + getScrollX();
            final float y = ev.getY() + getScrollY();
            for (int i = 0; i < rootView.getChildCount(); i++) {
                View item = rootView.getChildAt(i);
                if (item.getVisibility() == View.VISIBLE && x >= item.getLeft()
                        && x < item.getRight() && y >= item.getTop()
                        && y < item.getBottom()) {
                    if (!item.isClickable()) {
                        throw new EliminationException();
                    }
                    return item;
                }
            }
        }
        return null;
    }

    @Override
    public View getChildContentView(View v) {
        return v;
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return true;
    }

    @Override
    public void onBeginDrag(View v) {
        requestDisallowInterceptTouchEvent(true);
        v.setActivated(true);
    }

    @Override
    public void onChildDismissed(View v) {
        rootView.removeView(v);
        if (eliminationListener != null) {
            eliminationListener.onChildEliminated(v);
        }
    }

    @Override
    public void onDragCancelled(View v) {
        v.setActivated(false);
    }

    public interface EliminationScrollViewListener {
        public void onChildEliminated(View v);
    }

    private class EliminationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public String toString() {
            return "Can't eliminate this item, because the item is unclickable.";
        }
    }
}
