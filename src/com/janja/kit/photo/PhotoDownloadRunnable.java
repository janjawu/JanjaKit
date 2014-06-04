package com.janja.kit.photo;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class PhotoDownloadRunnable implements Runnable {

    private static final int READ_SIZE = 1024 * 2;

    static final int HTTP_STATE_FAILED = -1;
    static final int HTTP_STATE_STARTED = 0;
    static final int HTTP_STATE_COMPLETED = 1;

    final TaskRunnableDownloadMethods photoDownTask;

    interface TaskRunnableDownloadMethods {
        void setDownloadThread(Thread currentThread);

        byte[] getByteBuffer();

        void setByteBuffer(byte[] buffer);

        void handleDownloadState(int state);

        URL getImageURL();
    }

    PhotoDownloadRunnable(TaskRunnableDownloadMethods photoTask) {
        photoDownTask = photoTask;
    }

    @Override
    public void run() {
        photoDownTask.setDownloadThread(Thread.currentThread());
        android.os.Process
                .setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        byte[] byteBuffer = photoDownTask.getByteBuffer();

        try {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            if (null == byteBuffer) {
                photoDownTask.handleDownloadState(HTTP_STATE_STARTED);
                InputStream byteStream = null;
                try {
                    HttpURLConnection httpConn = (HttpURLConnection) photoDownTask
                            .getImageURL().openConnection();
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }

                    byteStream = httpConn.getInputStream();
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }

                    int contentSize = httpConn.getContentLength();

                    if (-1 == contentSize) {
                        byte[] tempBuffer = new byte[READ_SIZE];
                        int bufferLeft = tempBuffer.length;
                        int bufferOffset = 0;
                        int readResult = 0;

                        outer: do {
                            while (bufferLeft > 0) {
                                readResult = byteStream.read(tempBuffer,
                                        bufferOffset, bufferLeft);
                                if (readResult < 0) {
                                    break outer;
                                }

                                bufferOffset += readResult;
                                bufferLeft -= readResult;
                                if (Thread.interrupted()) {
                                    throw new InterruptedException();
                                }
                            }
                            bufferLeft = READ_SIZE;

                            int newSize = tempBuffer.length + READ_SIZE;
                            byte[] expandedBuffer = new byte[newSize];
                            System.arraycopy(tempBuffer, 0, expandedBuffer, 0,
                                    tempBuffer.length);
                            tempBuffer = expandedBuffer;
                        } while (true);

                        byteBuffer = new byte[bufferOffset];
                        System.arraycopy(tempBuffer, 0, byteBuffer, 0,
                                bufferOffset);
                    } else {
                        byteBuffer = new byte[contentSize];
                        int remainingLength = contentSize;
                        int bufferOffset = 0;

                        while (remainingLength > 0) {
                            int readResult = byteStream.read(byteBuffer,
                                    bufferOffset, remainingLength);
                            if (readResult < 0) {
                                throw new EOFException();
                            }
                            bufferOffset += readResult;
                            remainingLength -= readResult;
                            if (Thread.interrupted()) {
                                throw new InterruptedException();
                            }
                        }
                    }
                    if (Thread.interrupted()) {

                        throw new InterruptedException();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                } finally {
                    if (null != byteStream) {
                        try {
                            byteStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            photoDownTask.setByteBuffer(byteBuffer);
            photoDownTask.handleDownloadState(HTTP_STATE_COMPLETED);
        } catch (InterruptedException e1) {
        } finally {
            if (null == byteBuffer) {
                photoDownTask.handleDownloadState(HTTP_STATE_FAILED);
            }
            photoDownTask.setDownloadThread(null);
            Thread.interrupted();
        }
    }
}
