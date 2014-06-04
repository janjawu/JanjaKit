package com.janja.kit.photo;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;
import java.net.URL;

import com.janja.kit.photo.PhotoDecodeRunnable.TaskRunnableDecodeMethods;
import com.janja.kit.photo.PhotoDownloadRunnable.TaskRunnableDownloadMethods;

public class PhotoTask implements TaskRunnableDownloadMethods,
        TaskRunnableDecodeMethods {

    private WeakReference<PhotoView> mImageWeakRef;
    private URL imageURL;
    private int targetHeight;
    private int targetWidth;
    private boolean cacheEnabled;
    private Runnable downloadRunnable;
    private Runnable decodeRunnable;
    private Bitmap decodedImage;
    private Thread currentThread;
    private static PhotoManager sPhotoManager;

    protected Thread threadThis;
    protected byte[] imageBuffer;

    PhotoTask() {
        downloadRunnable = new PhotoDownloadRunnable(this);
        decodeRunnable = new PhotoDecodeRunnable(this);
        sPhotoManager = PhotoManager.getInstance();
    }

    void initializeDownloaderTask(PhotoManager photoManager,
            PhotoView photoView, boolean cacheFlag) {
        sPhotoManager = photoManager;
        imageURL = photoView.getLocation();
        mImageWeakRef = new WeakReference<PhotoView>(photoView);
        cacheEnabled = cacheFlag;
        targetWidth = photoView.getWidth();
        targetHeight = photoView.getHeight();
    }

    @Override
    public byte[] getByteBuffer() {
        return imageBuffer;
    }

    void recycle() {
        if (null != mImageWeakRef) {
            mImageWeakRef.clear();
            mImageWeakRef = null;
        }
        imageBuffer = null;
        decodedImage = null;
    }

    @Override
    public int getTargetWidth() {
        return targetWidth;
    }

    @Override
    public int getTargetHeight() {
        return targetHeight;
    }

    boolean isCacheEnabled() {
        return cacheEnabled;
    }

    @Override
    public URL getImageURL() {
        return imageURL;
    }

    @Override
    public void setByteBuffer(byte[] imageBuffer) {
        this.imageBuffer = imageBuffer;
    }

    void handleState(int state) {
        sPhotoManager.handleState(this, state);
    }

    Bitmap getImage() {
        return decodedImage;
    }

    Runnable getHTTPDownloadRunnable() {
        return downloadRunnable;
    }

    Runnable getPhotoDecodeRunnable() {
        return decodeRunnable;
    }

    public PhotoView getPhotoView() {
        if (null != mImageWeakRef) {
            return mImageWeakRef.get();
        }
        return null;
    }

    public Thread getCurrentThread() {
        synchronized (sPhotoManager) {
            return currentThread;
        }
    }

    public void setCurrentThread(Thread thread) {
        synchronized (sPhotoManager) {
            currentThread = thread;
        }
    }

    @Override
    public void setImage(Bitmap decodedImage) {
        this.decodedImage = decodedImage;
    }

    @Override
    public void setDownloadThread(Thread currentThread) {
        setCurrentThread(currentThread);
    }

    @Override
    public void handleDownloadState(int state) {
        int outState;
        switch (state) {
            case PhotoDownloadRunnable.HTTP_STATE_COMPLETED:
                outState = PhotoManager.DOWNLOAD_COMPLETE;
                break;
            case PhotoDownloadRunnable.HTTP_STATE_FAILED:
                outState = PhotoManager.DOWNLOAD_FAILED;
                break;
            default:
                outState = PhotoManager.DOWNLOAD_STARTED;
                break;
        }
        handleState(outState);
    }

    @Override
    public void setImageDecodeThread(Thread currentThread) {
        setCurrentThread(currentThread);
    }

    @Override
    public void handleDecodeState(int state) {
        int outState;
        switch (state) {
            case PhotoDecodeRunnable.DECODE_STATE_COMPLETED:
                outState = PhotoManager.TASK_COMPLETE;
                break;
            case PhotoDecodeRunnable.DECODE_STATE_FAILED:
                outState = PhotoManager.DOWNLOAD_FAILED;
                break;
            default:
                outState = PhotoManager.DECODE_STARTED;
                break;
        }
        handleState(outState);
    }
}
