package com.janja.kit.photo;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.net.URL;

import com.janja.kit.R;

public class PhotoView extends ImageView {

    private boolean cacheFlag;
    private boolean isDrawn;
    private WeakReference<View> thisView;
    private int hideShowResId = -1;
    private URL imageURL;
    private PhotoTask downloadThread;

    public PhotoView(Context context) {
        super(context);
    }

    public PhotoView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        getAttributes(attributeSet);
    }

    public PhotoView(Context context, AttributeSet attributeSet,
            int defaultStyle) {
        super(context, attributeSet, defaultStyle);
        getAttributes(attributeSet);
    }

    private void getAttributes(AttributeSet attributeSet) {
        TypedArray attributes = getContext().obtainStyledAttributes(
                attributeSet, R.styleable.ImageDownloaderView);

        hideShowResId = attributes.getResourceId(
                R.styleable.ImageDownloaderView_hideShowSibling, -1);

        attributes.recycle();
    }

    private void showView(int visState) {
        if (thisView != null) {
            View localView = thisView.get();
            if (localView != null)
                localView.setVisibility(visState);
        }
    }

    public void clearImage() {
        setImageDrawable(null);
        showView(View.VISIBLE);
    }

    final URL getLocation() {
        return imageURL;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if ((this.hideShowResId != -1) && ((getParent() instanceof View))) {
            View localView = ((View) getParent())
                    .findViewById(this.hideShowResId);
            if (localView != null) {
                this.thisView = new WeakReference<View>(localView);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        setImageURL(null, false, null);
        Drawable localDrawable = getDrawable();

        if (localDrawable != null)
            localDrawable.setCallback(null);

        if (thisView != null) {
            thisView.clear();
            thisView = null;
        }

        this.downloadThread = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if ((!isDrawn) && (imageURL != null)) {
            downloadThread = PhotoManager.startDownload(this, cacheFlag);
            isDrawn = true;
        }
        super.onDraw(canvas);
    }

    public void setHideView(View view) {
        this.thisView = new WeakReference<View>(view);
    }

    @Override
    public void setImageBitmap(Bitmap paramBitmap) {
        super.setImageBitmap(paramBitmap);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        int viewState;

        if (drawable == null) {
            viewState = View.VISIBLE;
        } else {
            viewState = View.INVISIBLE;
        }
        showView(viewState);

        super.setImageDrawable(drawable);
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
    }

    @Override
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
    }

    public void setImageURL(URL pictureURL, boolean cacheFlag,
            Drawable imageDrawable) {
        if (imageURL != null) {
            if (!imageURL.equals(pictureURL)) {
                PhotoManager.removeDownload(downloadThread, imageURL);
            } else {
                return;
            }
        }

        setImageDrawable(imageDrawable);

        imageURL = pictureURL;

        if ((isDrawn) && (pictureURL != null)) {
            this.cacheFlag = cacheFlag;
            downloadThread = PhotoManager.startDownload(this, cacheFlag);
        }
    }

    public void setStatusDrawable(Drawable drawable) {
        if (thisView == null) {
            setImageDrawable(drawable);
        }
    }

    public void setStatusResource(int resId) {
        if (thisView == null) {
            setImageResource(resId);
        }
    }
}
