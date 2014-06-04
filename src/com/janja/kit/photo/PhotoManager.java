package com.janja.kit.photo;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;

import java.net.URL;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.janja.kit.R;

public class PhotoManager {
    public static final int DOWNLOAD_FAILED = -1;
    public static final int DOWNLOAD_STARTED = 1;
    public static final int DOWNLOAD_COMPLETE = 2;
    public static final int DECODE_STARTED = 3;
    public static final int TASK_COMPLETE = 4;

    private static final int IMAGE_CACHE_SIZE = 1024 * 1024 * 4;
    private static final int KEEP_ALIVE_TIME = 1;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT;
    private static final int CORE_POOL_SIZE = 8;
    private static final int MAXIMUM_POOL_SIZE = 8;
    private static int NUMBER_OF_CORES = Runtime.getRuntime()
            .availableProcessors();

    private final LruCache<URL, byte[]> photoCache;
    private final BlockingQueue<Runnable> downloadWorkQueue;
    private final BlockingQueue<Runnable> decodeWorkQueue;
    private final Queue<PhotoTask> photoTaskWorkQueue;
    private final ThreadPoolExecutor downloadThreadPool;
    private final ThreadPoolExecutor decodeThreadPool;
    private Handler handler;
    private static PhotoManager sInstance = null;

    static {
        KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
        sInstance = new PhotoManager();
    }

    private PhotoManager() {
        downloadWorkQueue = new LinkedBlockingQueue<Runnable>();
        decodeWorkQueue = new LinkedBlockingQueue<Runnable>();
        photoTaskWorkQueue = new LinkedBlockingQueue<PhotoTask>();
        downloadThreadPool = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT,
                downloadWorkQueue);

        decodeThreadPool = new ThreadPoolExecutor(NUMBER_OF_CORES,
                NUMBER_OF_CORES, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT,
                decodeWorkQueue);

        photoCache = new LruCache<URL, byte[]>(IMAGE_CACHE_SIZE) {
            @Override
            protected int sizeOf(URL paramURL, byte[] paramArrayOfByte) {
                return paramArrayOfByte.length;
            }
        };

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                PhotoTask photoTask = (PhotoTask) inputMessage.obj;
                PhotoView localView = photoTask.getPhotoView();
                if (localView != null) {
                    URL localURL = localView.getLocation();
                    if (photoTask.getImageURL() == localURL)
                        switch (inputMessage.what) {
                            case DOWNLOAD_STARTED:
                                localView
                                        .setStatusResource(R.drawable.imagedownloading);
                                break;
                            case DOWNLOAD_COMPLETE:
                                localView
                                        .setStatusResource(R.drawable.decodequeued);
                                break;
                            case DECODE_STARTED:
                                localView
                                        .setStatusResource(R.drawable.decodedecoding);
                                break;
                            case TASK_COMPLETE:
                                localView.setImageBitmap(photoTask.getImage());
                                recycleTask(photoTask);
                                break;
                            case DOWNLOAD_FAILED:
                                localView
                                        .setStatusResource(R.drawable.imagedownloadfailed);
                                recycleTask(photoTask);
                                break;
                            default:
                                super.handleMessage(inputMessage);
                        }
                }
            }
        };
    }

    public static PhotoManager getInstance() {
        return sInstance;
    }

    public void handleState(PhotoTask photoTask, int state) {
        switch (state) {
            case TASK_COMPLETE:
                if (photoTask.isCacheEnabled()) {
                    photoCache.put(photoTask.getImageURL(),
                            photoTask.getByteBuffer());
                }
                Message completeMessage = handler.obtainMessage(state,
                        photoTask);
                completeMessage.sendToTarget();
                break;
            case DOWNLOAD_COMPLETE:
                decodeThreadPool.execute(photoTask.getPhotoDecodeRunnable());
            default:
                handler.obtainMessage(state, photoTask).sendToTarget();
                break;
        }

    }

    public static void cancelAll() {
        PhotoTask[] taskArray = new PhotoTask[sInstance.downloadWorkQueue
                .size()];
        sInstance.downloadWorkQueue.toArray(taskArray);
        int taskArraylen = taskArray.length;
        synchronized (sInstance) {
            for (int taskArrayIndex = 0; taskArrayIndex < taskArraylen; taskArrayIndex++) {
                Thread thread = taskArray[taskArrayIndex].threadThis;
                if (null != thread) {
                    thread.interrupt();
                }
            }
        }
    }

    static public void removeDownload(PhotoTask downloaderTask, URL pictureURL) {
        if (downloaderTask != null
                && downloaderTask.getImageURL().equals(pictureURL)) {
            synchronized (sInstance) {
                Thread thread = downloaderTask.getCurrentThread();
                if (null != thread)
                    thread.interrupt();
            }
            sInstance.downloadThreadPool.remove(downloaderTask
                    .getHTTPDownloadRunnable());
        }
    }

    static public PhotoTask startDownload(PhotoView imageView, boolean cacheFlag) {
        PhotoTask downloadTask = sInstance.photoTaskWorkQueue.poll();
        if (null == downloadTask) {
            downloadTask = new PhotoTask();
        }

        downloadTask.initializeDownloaderTask(PhotoManager.sInstance,
                imageView, cacheFlag);

        downloadTask.setByteBuffer(sInstance.photoCache.get(downloadTask
                .getImageURL()));

        if (null == downloadTask.getByteBuffer()) {
            sInstance.downloadThreadPool.execute(downloadTask
                    .getHTTPDownloadRunnable());
            imageView.setStatusResource(R.drawable.imagequeued);
        } else {
            sInstance.handleState(downloadTask, DOWNLOAD_COMPLETE);
        }
        return downloadTask;
    }

    void recycleTask(PhotoTask downloadTask) {
        downloadTask.recycle();
        photoTaskWorkQueue.offer(downloadTask);
    }
}
