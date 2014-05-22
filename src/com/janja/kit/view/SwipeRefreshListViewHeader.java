package com.janja.kit.view;

import com.janja.kit.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SwipeRefreshListViewHeader extends LinearLayout {
    private LinearLayout container;
    private ImageView arrowImageView;
    private ProgressBar progressBar;
    private TextView hintTextView;
    private int headerState = STATE_NORMAL;

    private Animation rotateUpAnim;
    private Animation rotateDownAnim;

    private final int ROTATE_ANIM_DURATION = 180;

    public final static int STATE_NORMAL = 0;
    public final static int STATE_READY = 1;
    public final static int STATE_REFRESHING = 2;

    public SwipeRefreshListViewHeader(Context context) {
        super(context);
        initView(context);
    }

    public SwipeRefreshListViewHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    private void initView(Context context) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0);
        container = (LinearLayout) LayoutInflater.from(context).inflate(
                R.layout.swipe_refresh_listview_header, null);
        addView(container, lp);
        setGravity(Gravity.BOTTOM);

        arrowImageView = (ImageView) findViewById(R.id.swipe_refresh_listview_header_arrow);
        hintTextView = (TextView) findViewById(R.id.swipe_refresh_listview_header_hint_textview);
        progressBar = (ProgressBar) findViewById(R.id.swipe_refresh_listview_header_progressbar);

        rotateUpAnim = new RotateAnimation(0.0f, -180.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        rotateUpAnim.setDuration(ROTATE_ANIM_DURATION);
        rotateUpAnim.setFillAfter(true);
        rotateDownAnim = new RotateAnimation(-180.0f, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        rotateDownAnim.setDuration(ROTATE_ANIM_DURATION);
        rotateDownAnim.setFillAfter(true);
    }

    public void setState(int state) {
        if (state == headerState)
            return;

        if (state == STATE_REFRESHING) {
            arrowImageView.clearAnimation();
            arrowImageView.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            arrowImageView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.INVISIBLE);
        }

        switch (state) {
            case STATE_NORMAL:
                if (headerState == STATE_READY) {
                    arrowImageView.startAnimation(rotateDownAnim);
                }
                if (headerState == STATE_REFRESHING) {
                    arrowImageView.clearAnimation();
                }
                hintTextView
                        .setText(R.string.swipe_refresh_listview_header_hint_normal);
                break;
            case STATE_READY:
                if (headerState != STATE_READY) {
                    arrowImageView.clearAnimation();
                    arrowImageView.startAnimation(rotateUpAnim);
                    hintTextView
                            .setText(R.string.swipe_refresh_listview_header_hint_ready);
                }
                break;
            case STATE_REFRESHING:
                hintTextView
                        .setText(R.string.swipe_refresh_listview_header_hint_loading);
                break;
            default:
        }

        headerState = state;
    }

    public void setVisiableHeight(int height) {
        if (height < 0)
            height = 0;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) container
                .getLayoutParams();
        lp.height = height;
        container.setLayoutParams(lp);
    }

    public int getVisiableHeight() {
        return container.getHeight();
    }

}
