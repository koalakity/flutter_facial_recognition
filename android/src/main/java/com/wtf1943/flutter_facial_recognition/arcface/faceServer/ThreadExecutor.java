package com.wtf1943.flutter_facial_recognition.arcface.faceServer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadExecutor {
    /**
     * 特征提取线程池
     */
    private ExecutorService frExecutor;

    private int frQueueSize = 5;
    /**
     * 活体检测线程池
     */
    private ExecutorService flExecutor;

    private int flQueueSize = 5;
    /**
     * 特征提取线程队列
     */
    private LinkedBlockingQueue<Runnable> frThreadQueue = null;
    /**
     * 活体检测线程队列
     */
    private LinkedBlockingQueue<Runnable> flThreadQueue = null;

    public ThreadExecutor(){
        frThreadQueue = new LinkedBlockingQueue<>(frQueueSize);
        frExecutor = new ThreadPoolExecutor(1, frQueueSize, 0, TimeUnit.MILLISECONDS, frThreadQueue);
        flThreadQueue = new LinkedBlockingQueue<Runnable>(flQueueSize);
        flExecutor = new ThreadPoolExecutor(1, flQueueSize, 0, TimeUnit.MILLISECONDS, flThreadQueue);
    }

    public ExecutorService getFrExecutor() {
        return frExecutor;
    }

    public ExecutorService getFlExecutor() {
        return flExecutor;
    }

    public LinkedBlockingQueue<Runnable> getFrThreadQueue() {
        return frThreadQueue;
    }

    public LinkedBlockingQueue<Runnable> getFlThreadQueue() {
        return flThreadQueue;
    }
}
