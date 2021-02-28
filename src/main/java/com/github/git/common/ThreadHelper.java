package com.github.git.common;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author imyuyu
 */
public class ThreadHelper {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static <T> Future<T> submit(Callable<T> tCallable){
        return executorService.submit(tCallable);
    }

    public static void submit(Runnable runnable){
        executorService.submit(runnable);
    }

}
