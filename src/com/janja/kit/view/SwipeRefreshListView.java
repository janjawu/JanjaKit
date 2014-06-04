package com.janja.kit.view;

import com.janja.kit.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

public class SwipeRefreshListView extends ListView implements OnScrollListener {

    private final static int SCROLLBACK_HEADER = 0;
    private final static int SCROLLBACK_FOOTER = 1;
    private final static int SCROLL_DURATION = 400;
    private final static int PULL_LOAD_MORE_DELTA = 50;
    private final static int AUTO_LOAD_MORE_LEAST_COUNT = 10;
    private final static float OFFSET_RADIO = 1.8f;

    private float lastY = -1;
    private Scroller scroller;
    private OnScrollListener scrollListener;
    private SwipeRefreshListViewListener swipeRefreshListener;
    private SwipeRefreshListViewHeader headerView;
    private RelativeLayout headerViewContent;
    private TextView headerTimeView;
    private int headerViewHeight;
    private boolean enablePullRefresh = true;
    private boolean pullRefreshing;
    private SwipeRefreshListViewFooter mFooterView;
    private boolean enablePullLoad = true;
    private boolean enableAutoLoad = true;
    private boolean pullLoading;
    private boolean isFooterReady;
    private int totalItemCount;
    private int defaultItemCount = 2;
    private int scrollBack;

    public SwipeRefreshListView(Context context) {
        super(context);
        initWithContext(context);
    }

    public SwipeRefreshListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initWithContext(context);
    }

    public SwipeRefreshListView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        initWithContext(context);
    }

    private void initWithContext(Context context) {
        scroller = new Scroller(context, new DecelerateInterpolator());
        super.setOnScrollListener(this);

        headerView = new SwipeRefreshListViewHeader(context);
        headerViewContent = (RelativeLayout) headerView
                .findViewById(R.id.swipe_refresh_listview_header_content);
        headerTimeView = (TextView) headerView
                .findViewById(R.id.swipe_refresh_listview_header_time);
        addHeaderView(headerView);

        mFooterView = new SwipeRefreshListViewFooter(context);
        setPullLoadEnable(enablePullLoad);

        headerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        headerViewHeight = headerViewContent.getHeight();
                        getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                    }
                });
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (isFooterReady == false) {
            isFooterReady = true;
            addFooterView(mFooterView);
        }
        super.setAdapter(adapter);
    }

    public void setPullRefreshEnable(boolean enable) {
        enablePullRefresh = enable;
        if (!enablePullRefresh) {
            headerViewContent.setVisibility(View.INVISIBLE);
        } else {
            headerViewContent.setVisibility(View.VISIBLE);
        }
    }

    public void setPullLoadEnable(boolean enable) {
        enablePullLoad = enable;
        if (!enablePullLoad) {
            mFooterView.hide();
            mFooterView.setOnClickListener(null);
        } else {
            pullLoading = false;
            mFooterView.show();
            mFooterView.setState(SwipeRefreshListViewFooter.STATE_NORMAL);
            mFooterView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startLoadMore();
                }
            });
        }
    }

    public void setAutoLoadEnable(boolean enable) {
        enableAutoLoad = enable;
    }

    public void stopRefresh() {
        if (pullRefreshing == true) {
            pullRefreshing = false;
            resetHeaderHeight();
        }
    }

    public void stopLoadMore() {
        if (pullLoading == true) {
            pullLoading = false;
            mFooterView.setState(SwipeRefreshListViewFooter.STATE_NORMAL);
        }
    }

    public void setRefreshTime(String time) {
        headerTimeView.setText(time);
    }

    private void invokeOnScrolling() {
        if (scrollListener instanceof OnSwipeRefreshScrollListener) {
            OnSwipeRefreshScrollListener l = (OnSwipeRefreshScrollListener) scrollListener;
            l.onSwipeRefreshScrolling(this);
        }
    }

    private void updateHeaderHeight(float delta) {
        headerView.setVisiableHeight((int) delta
                + headerView.getVisiableHeight());
        if (enablePullRefresh && !pullRefreshing) {
            if (headerView.getVisiableHeight() > headerViewHeight) {
                headerView.setState(SwipeRefreshListViewHeader.STATE_READY);
            } else {
                headerView.setState(SwipeRefreshListViewHeader.STATE_NORMAL);
            }
        }
        setSelection(0);
    }

    private void resetHeaderHeight() {
        int height = headerView.getVisiableHeight();

        if (height == 0)
            return;

        if (pullRefreshing && height <= headerViewHeight) {
            return;
        }

        int finalHeight = 0;
        if (pullRefreshing && height > headerViewHeight) {
            finalHeight = headerViewHeight;
        }
        scrollBack = SCROLLBACK_HEADER;
        scroller.startScroll(0, height, 0, finalHeight - height,
                SCROLL_DURATION);
        invalidate();
    }

    private void updateFooterHeight(float delta) {
        int maxMargin = PULL_LOAD_MORE_DELTA + 5;
        int height = mFooterView.getBottomMargin() + (int) delta;
        if (height >= maxMargin) {
            height = maxMargin;
        }
        if (enablePullLoad && !pullLoading) {
            if (height > PULL_LOAD_MORE_DELTA) {
                mFooterView.setState(SwipeRefreshListViewFooter.STATE_READY);
            } else {
                mFooterView.setState(SwipeRefreshListViewFooter.STATE_NORMAL);
            }
        }
        mFooterView.setBottomMargin(height);
    }

    private void resetFooterHeight() {
        int bottomMargin = mFooterView.getBottomMargin();
        if (bottomMargin > 0) {
            scrollBack = SCROLLBACK_FOOTER;
            scroller.startScroll(0, bottomMargin, 0, -bottomMargin,
                    SCROLL_DURATION);
            invalidate();
        }
    }

    private void startLoadMore() {
        pullLoading = true;
        mFooterView.setState(SwipeRefreshListViewFooter.STATE_LOADING);
        if (swipeRefreshListener != null) {
            swipeRefreshListener.onLoadMore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (lastY == -1) {
            lastY = ev.getRawY();
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastY = ev.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                final float deltaY = ev.getRawY() - lastY;
                lastY = ev.getRawY();
                if (getFirstVisiblePosition() == 0
                        && (headerView.getVisiableHeight() > 0 || deltaY > 0)) {
                    updateHeaderHeight(deltaY / OFFSET_RADIO);
                    invokeOnScrolling();
                } else if (getLastVisiblePosition() == totalItemCount - 1
                        && (mFooterView.getBottomMargin() > 0 || deltaY < 0)) {
                    updateFooterHeight(-deltaY / OFFSET_RADIO);
                }
                break;
            default:
                lastY = -1;
                if (getFirstVisiblePosition() == 0) {
                    if (enablePullRefresh
                            && headerView.getVisiableHeight() > headerViewHeight) {
                        pullRefreshing = true;
                        headerView
                                .setState(SwipeRefreshListViewHeader.STATE_REFRESHING);
                        if (swipeRefreshListener != null) {
                            swipeRefreshListener.onRefresh();
                        }
                    }
                    resetHeaderHeight();
                } else if (getLastVisiblePosition() >= totalItemCount
                        - AUTO_LOAD_MORE_LEAST_COUNT) {
                    if (enablePullLoad && !pullLoading) {
                        if (enableAutoLoad) {
                            startLoadMore();
                        } else {
                            if (getLastVisiblePosition() == totalItemCount - 1
                                    && mFooterView.getBottomMargin() > PULL_LOAD_MORE_DELTA) {
                                startLoadMore();
                            }
                        }
                    }
                    resetFooterHeight();
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            if (scrollBack == SCROLLBACK_HEADER) {
                headerView.setVisiableHeight(scroller.getCurrY());
            } else {
                mFooterView.setBottomMargin(scroller.getCurrY());
            }
            postInvalidate();
            invokeOnScrolling();
        }
        super.computeScroll();
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        scrollListener = l;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollListener != null) {
            scrollListener.onScrollStateChanged(view, scrollState);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        if (getLastVisiblePosition() >= defaultItemCount
                && getLastVisiblePosition() >= totalItemCount
                        - AUTO_LOAD_MORE_LEAST_COUNT) {
            if (enablePullLoad && !pullLoading) {
                if (enableAutoLoad) {
                    startLoadMore();
                }
            }
        }

        this.totalItemCount = totalItemCount;
        if (scrollListener != null) {
            scrollListener.onScroll(view, firstVisibleItem, visibleItemCount,
                    totalItemCount);
        }
    }

    public void setSwipeRefreshListViewListener(SwipeRefreshListViewListener l) {
        swipeRefreshListener = l;
    }

    public interface OnSwipeRefreshScrollListener extends OnScrollListener {
        public void onSwipeRefreshScrolling(View view);
    }

    public interface SwipeRefreshListViewListener {
        public void onRefresh();

        public void onLoadMore();
    }
}
