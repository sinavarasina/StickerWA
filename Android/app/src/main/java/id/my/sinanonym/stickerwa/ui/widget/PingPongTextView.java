package id.my.sinanonym.stickerwa.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * A single-line TextView that smoothly scrolls horizontally to the end when text overflows,
 * pauses briefly, scrolls back to the start, pauses briefly, and repeats in a loop.
 */
public class PingPongTextView extends AppCompatTextView {

    private static final long PAUSE_DURATION_MS = 1200L;
    private static final float MS_PER_PIXEL = 16f;
    private static final long MIN_SCROLL_DURATION_MS = 900L;

    @Nullable
    private ValueAnimator scrollAnimator;
    private boolean isAttached = false;
    private final Runnable startForwardRunnable = this::animateForward;
    private final Runnable startBackwardRunnable = this::animateBackward;

    public PingPongTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public PingPongTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PingPongTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setSingleLine(true);
        setEllipsize(null);
        setHorizontalFadingEdgeEnabled(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttached = true;
        post(this::restartPingPong);
    }

    @Override
    protected void onDetachedFromWindow() {
        isAttached = false;
        stopPingPong();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            post(this::restartPingPong);
        }
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        post(this::restartPingPong);
    }

    private int calculateMaxScroll() {
        CharSequence text = getText();
        if (text == null || text.length() == 0) {
            return 0;
        }
        int availableWidth = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (availableWidth <= 0) {
            return 0;
        }
        TextPaint paint = getPaint();
        float textWidth = paint.measureText(text, 0, text.length());
        int overflow = (int) Math.ceil(textWidth - availableWidth);
        return Math.max(overflow, 0);
    }

    public void restartPingPong() {
        stopPingPong();
        scrollTo(0, 0);
        if (!isAttached) {
            return;
        }
        int maxScroll = calculateMaxScroll();
        if (maxScroll > 0) {
            postDelayed(startForwardRunnable, PAUSE_DURATION_MS);
        }
    }

    private void stopPingPong() {
        removeCallbacks(startForwardRunnable);
        removeCallbacks(startBackwardRunnable);
        if (scrollAnimator != null) {
            scrollAnimator.removeAllListeners();
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
    }

    private void animateForward() {
        if (!isAttached) {
            return;
        }
        final int maxScroll = calculateMaxScroll();
        if (maxScroll <= 0) {
            scrollTo(0, 0);
            return;
        }
        long duration = Math.max((long) (maxScroll * MS_PER_PIXEL), MIN_SCROLL_DURATION_MS);
        scrollAnimator = ValueAnimator.ofInt(0, maxScroll);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new LinearInterpolator());
        scrollAnimator.addUpdateListener(animation -> scrollTo((int) animation.getAnimatedValue(), 0));
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAttached) {
                    postDelayed(startBackwardRunnable, PAUSE_DURATION_MS);
                }
            }
        });
        scrollAnimator.start();
    }

    private void animateBackward() {
        if (!isAttached) {
            return;
        }
        final int maxScroll = calculateMaxScroll();
        if (maxScroll <= 0) {
            scrollTo(0, 0);
            return;
        }
        long duration = Math.max((long) (maxScroll * MS_PER_PIXEL), MIN_SCROLL_DURATION_MS);
        scrollAnimator = ValueAnimator.ofInt(maxScroll, 0);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new LinearInterpolator());
        scrollAnimator.addUpdateListener(animation -> scrollTo((int) animation.getAnimatedValue(), 0));
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAttached) {
                    postDelayed(startForwardRunnable, PAUSE_DURATION_MS);
                }
            }
        });
        scrollAnimator.start();
    }
}
