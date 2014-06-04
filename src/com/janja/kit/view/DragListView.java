package com.janja.kit.view;

import com.janja.kit.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

public class DragListView extends ListView {

    private ImageView dragView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private int dragPos;
    private int srcDragPos;
    private int dragPointX;
    private int dragPointY;
    private int xOffset;
    private int yOffset;
    private DragListener dragListener;
    private DropListener dropListener;
    private RemoveListener removeListener;
    private int upperBound;
    private int lowerBound;
    private int height;
    private GestureDetector gestureDetector;
    private static final int FLING = 0;
    private static final int SLIDE = 1;
    private static final int TRASH = 2;
    private int removeMode = -1;
    private Rect tempRect = new Rect();
    private Bitmap dragBitmap;
    private final int touchSlop;
    private int dragWidth;
    private int itemHeightNormal;
    private int itemHeightExpanded;
    private int itemHeightHalf;
    private Drawable trashcan;

    public DragListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        Resources res = getResources();
        dragWidth = res.getDimensionPixelSize(R.dimen.drag_width);
        itemHeightNormal = res.getDimensionPixelSize(R.dimen.normal_height);
        itemHeightHalf = itemHeightNormal / 2;
        itemHeightExpanded = res.getDimensionPixelSize(R.dimen.expanded_height);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (removeListener != null && gestureDetector == null) {
            if (removeMode == FLING) {
                gestureDetector = new GestureDetector(getContext(),
                        new SimpleOnGestureListener() {
                            @Override
                            public boolean onFling(MotionEvent e1,
                                    MotionEvent e2, float velocityX,
                                    float velocityY) {
                                if (dragView != null) {
                                    if (velocityX > 1000) {
                                        Rect r = tempRect;
                                        dragView.getDrawingRect(r);
                                        if (e2.getX() > r.right * 2 / 3) {
                                            stopDragging();
                                            removeListener.remove(srcDragPos);
                                            unExpandViews(true);
                                        }
                                    }
                                    return true;
                                }
                                return false;
                            }
                        });
            }
        }
        if (dragListener != null || dropListener != null) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    int x = (int) ev.getX();
                    int y = (int) ev.getY();
                    int itemnum = pointToPosition(x, y);
                    if (itemnum == AdapterView.INVALID_POSITION) {
                        break;
                    }
                    ViewGroup item = (ViewGroup) getChildAt(itemnum
                            - getFirstVisiblePosition());
                    dragPointX = x - item.getLeft();
                    dragPointY = y - item.getTop();
                    xOffset = ((int) ev.getRawX()) - x;
                    yOffset = ((int) ev.getRawY()) - y;

                    if (x < dragWidth) {
                        item.setDrawingCacheEnabled(true);
                        Bitmap bitmap = Bitmap.createBitmap(item
                                .getDrawingCache());
                        startDragging(bitmap, x, y);
                        dragPos = itemnum;
                        srcDragPos = dragPos;
                        height = getHeight();
                        upperBound = Math.min(y - touchSlop, height / 3);
                        lowerBound = Math.max(y + touchSlop, height * 2 / 3);
                        return false;
                    }
                    stopDragging();
                    break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    private int myPointToPosition(int x, int y) {

        if (y < 0) {
            int pos = myPointToPosition(x, y + itemHeightNormal);
            if (pos > 0) {
                return pos - 1;
            }
        }

        Rect frame = tempRect;
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            child.getHitRect(frame);
            if (frame.contains(x, y)) {
                return getFirstVisiblePosition() + i;
            }
        }
        return INVALID_POSITION;
    }

    private int getItemForPosition(int y) {
        int adjustedy = y - dragPointY - itemHeightHalf;
        int pos = myPointToPosition(0, adjustedy);
        if (pos >= 0) {
            if (pos <= srcDragPos) {
                pos += 1;
            }
        } else if (adjustedy < 0) {
            pos = 0;
        }
        return pos;
    }

    private void adjustScrollBounds(int y) {
        if (y >= height / 3) {
            upperBound = height / 3;
        }
        if (y <= height * 2 / 3) {
            lowerBound = height * 2 / 3;
        }
    }

    private void unExpandViews(boolean deletion) {
        for (int i = 0;; i++) {
            View v = getChildAt(i);
            if (v == null) {
                if (deletion) {
                    int position = getFirstVisiblePosition();
                    int y = getChildAt(0).getTop();
                    setAdapter(getAdapter());
                    setSelectionFromTop(position, y);
                }
                try {
                    layoutChildren();
                    v = getChildAt(i);
                } catch (IllegalStateException ex) {
                }

                if (v == null) {
                    return;
                }
            }
            ViewGroup.LayoutParams params = v.getLayoutParams();
            params.height = itemHeightNormal;
            v.setLayoutParams(params);
            v.setVisibility(View.VISIBLE);
        }
    }

    private void doExpansion() {
        int childnum = dragPos - getFirstVisiblePosition();
        if (dragPos > srcDragPos) {
            childnum++;
        }
        int numheaders = getHeaderViewsCount();

        View first = getChildAt(srcDragPos - getFirstVisiblePosition());
        for (int i = 0;; i++) {
            View vv = getChildAt(i);
            if (vv == null) {
                break;
            }

            int height = itemHeightNormal;
            int visibility = View.VISIBLE;
            if (dragPos < numheaders && i == numheaders) {
                if (vv.equals(first)) {
                    visibility = View.INVISIBLE;
                } else {
                    height = itemHeightExpanded;
                }
            } else if (vv.equals(first)) {
                if (dragPos == srcDragPos
                        || getPositionForView(vv) == getCount() - 1) {
                    visibility = View.INVISIBLE;
                } else {
                    height = 1;
                }
            } else if (i == childnum) {
                if (dragPos >= numheaders && dragPos < getCount() - 1) {
                    height = itemHeightExpanded;
                }
            }
            ViewGroup.LayoutParams params = vv.getLayoutParams();
            params.height = height;
            vv.setLayoutParams(params);
            vv.setVisibility(visibility);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        if ((dragListener != null || dropListener != null) && dragView != null) {
            int action = ev.getAction();
            switch (action) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect r = tempRect;
                    dragView.getDrawingRect(r);
                    stopDragging();
                    if (removeMode == SLIDE && ev.getX() > r.right * 3 / 4) {
                        if (removeListener != null) {
                            removeListener.remove(srcDragPos);
                        }
                        unExpandViews(true);
                    } else {
                        if (dropListener != null && dragPos >= 0
                                && dragPos < getCount()) {
                            dropListener.drop(srcDragPos, dragPos);
                        }
                        unExpandViews(false);
                    }
                    break;

                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    int x = (int) ev.getX();
                    int y = (int) ev.getY();
                    dragView(x, y);
                    int itemnum = getItemForPosition(y);
                    if (itemnum >= 0) {
                        if (action == MotionEvent.ACTION_DOWN
                                || itemnum != dragPos) {
                            if (dragListener != null) {
                                dragListener.drag(dragPos, itemnum);
                            }
                            dragPos = itemnum;
                            doExpansion();
                        }
                        int speed = 0;
                        adjustScrollBounds(y);
                        if (y > lowerBound) {
                            if (getLastVisiblePosition() < getCount() - 1) {
                                speed = y > (height + lowerBound) / 2 ? 16 : 4;
                            } else {
                                speed = 1;
                            }
                        } else if (y < upperBound) {
                            speed = y < upperBound / 2 ? -16 : -4;
                            if (getFirstVisiblePosition() == 0
                                    && getChildAt(0).getTop() >= getPaddingTop()) {
                                speed = 0;
                            }
                        }
                        if (speed != 0) {
                            smoothScrollBy(speed, 30);
                        }
                    }
                    break;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }

    private void startDragging(Bitmap bm, int x, int y) {
        stopDragging();

        windowParams = new WindowManager.LayoutParams();
        windowParams.gravity = Gravity.TOP;
        windowParams.x = 0;
        windowParams.y = y - dragPointY + yOffset;

        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        windowParams.format = PixelFormat.TRANSLUCENT;
        windowParams.windowAnimations = 0;

        Context context = getContext();
        ImageView v = new ImageView(context);

        v.setBackgroundResource(R.drawable.drag_listview_item_press);
        v.setPadding(0, 0, 0, 0);
        v.setImageBitmap(bm);
        dragBitmap = bm;

        windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(v, windowParams);
        dragView = v;
    }

    private void dragView(int x, int y) {
        if (removeMode == SLIDE) {
            float alpha = 1.0f;
            int width = dragView.getWidth();
            if (x > width / 2) {
                alpha = ((float) (width - x)) / (width / 2);
            }
            windowParams.alpha = alpha;
        }

        if (removeMode == FLING || removeMode == TRASH) {
            windowParams.x = x - dragPointX + xOffset;
        } else {
            windowParams.x = 0;
        }
        windowParams.y = y - dragPointY + yOffset;
        windowManager.updateViewLayout(dragView, windowParams);

        if (trashcan != null) {
            int width = dragView.getWidth();
            if (y > getHeight() * 3 / 4) {
                trashcan.setLevel(2);
            } else if (width > 0 && x > width / 4) {
                trashcan.setLevel(1);
            } else {
                trashcan.setLevel(0);
            }
        }
    }

    private void stopDragging() {
        if (dragView != null) {
            dragView.setVisibility(GONE);
            WindowManager wm = (WindowManager) getContext().getSystemService(
                    Context.WINDOW_SERVICE);
            wm.removeView(dragView);
            dragView.setImageDrawable(null);
            dragView = null;
        }
        if (dragBitmap != null) {
            dragBitmap.recycle();
            dragBitmap = null;
        }
        if (trashcan != null) {
            trashcan.setLevel(0);
        }
    }

    public void setTrashcan(Drawable trash) {
        trashcan = trash;
        removeMode = TRASH;
    }

    public void setDragListener(DragListener l) {
        dragListener = l;
    }

    public void setDropListener(DropListener l) {
        dropListener = l;
    }

    public void setRemoveListener(RemoveListener l) {
        removeListener = l;
    }

    public interface DragListener {
        void drag(int from, int to);
    }

    public interface DropListener {
        void drop(int from, int to);
    }

    public interface RemoveListener {
        void remove(int which);
    }
}