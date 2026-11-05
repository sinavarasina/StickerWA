package id.my.sinanonym.stickerwa.core;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Lightweight background task executor replacing deprecated AsyncTask.
 */
public final class AppExecutors {
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onResult(T result);
    }

    public interface ErrorCallback {
        void onError(@NonNull Exception exception);
    }

    private AppExecutors() {}

    @NonNull
    public static <T> Future<?> execute(
            @NonNull final Callable<T> backgroundTask,
            @NonNull final Callback<T> onSuccess) {
        return execute(backgroundTask, onSuccess, null);
    }

    @NonNull
    public static <T> Future<?> execute(
            @NonNull final Callable<T> backgroundTask,
            @NonNull final Callback<T> onSuccess,
            @Nullable final ErrorCallback onError) {
        return IO_EXECUTOR.submit(() -> {
            try {
                final T result = backgroundTask.call();
                if (!Thread.currentThread().isInterrupted()) {
                    MAIN_HANDLER.post(() -> onSuccess.onResult(result));
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted() && onError != null) {
                    MAIN_HANDLER.post(() -> onError.onError(e));
                }
            }
        });
    }

    public static void cancel(@Nullable Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
