package com.janja.kit.photo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class PhotoDecodeRunnable implements Runnable {

    private static final int NUMBER_OF_DECODE_TRIES = 2;
    private static final long SLEEP_TIME_MILLISECONDS = 250;
    private static final String LOG_TAG = "PhotoDecodeRunnable";

    static final int DECODE_STATE_FAILED = -1;
    static final int DECODE_STATE_STARTED = 0;
    static final int DECODE_STATE_COMPLETED = 1;

    final TaskRunnableDecodeMethods mPhotoTask;

    public interface TaskRunnableDecodeMethods {

        void setImageDecodeThread(Thread currentThread);

        byte[] getByteBuffer();

        void handleDecodeState(int state);

        int getTargetWidth();

        int getTargetHeight();

        void setImage(Bitmap image);
    }

    public PhotoDecodeRunnable(TaskRunnableDecodeMethods downloadTask) {
        mPhotoTask = downloadTask;
    }

    @Override
    public void run() {
        mPhotoTask.setImageDecodeThread(Thread.currentThread());

        byte[] imageBuffer = mPhotoTask.getByteBuffer();

        Bitmap returnBitmap = null;

        try {

            mPhotoTask.handleDecodeState(DECODE_STATE_STARTED);

            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();

            int targetWidth = mPhotoTask.getTargetWidth();
            int targetHeight = mPhotoTask.getTargetHeight();

            if (Thread.interrupted()) {

                return;
            }
            bitmapOptions.inJustDecodeBounds = true;

            BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.length,
                    bitmapOptions);

            int hScale = bitmapOptions.outHeight / targetHeight;
            int wScale = bitmapOptions.outWidth / targetWidth;

            int sampleSize = Math.max(hScale, wScale);

            if (sampleSize > 1) {
                bitmapOptions.inSampleSize = sampleSize;
            }

            if (Thread.interrupted()) {
                return;
            }

            bitmapOptions.inJustDecodeBounds = false;

            for (int i = 0; i < NUMBER_OF_DECODE_TRIES; i++) {
                try {
                    returnBitmap = BitmapFactory.decodeByteArray(imageBuffer,
                            0, imageBuffer.length, bitmapOptions);
                } catch (Throwable e) {
                    Log.e(LOG_TAG, "Out of memory in decode stage. Throttling.");

                    java.lang.System.gc();

                    if (Thread.interrupted()) {
                        return;

                    }

                    try {
                        Thread.sleep(SLEEP_TIME_MILLISECONDS);
                    } catch (java.lang.InterruptedException interruptException) {
                        return;
                    }
                }
            }

        } finally {
            if (null == returnBitmap) {

                mPhotoTask.handleDecodeState(DECODE_STATE_FAILED);

                Log.e(LOG_TAG, "Download failed in PhotoDecodeRunnable");

            } else {
                mPhotoTask.setImage(returnBitmap);
                mPhotoTask.handleDecodeState(DECODE_STATE_COMPLETED);
            }

            mPhotoTask.setImageDecodeThread(null);
            Thread.interrupted();
        }

    }
}
