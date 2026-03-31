package com.example.smartschoolfinder.ui;

import android.content.Intent;
import android.animation.Animator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartschoolfinder.R;
import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    // Keep splash long enough for text/brand to be readable.
    private static final long SPLASH_MIN_DURATION_MS = 2400L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startUptimeMs;
    private boolean hasNavigated = false;
    private boolean capShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        startUptimeMs = SystemClock.uptimeMillis();

        // Restore original: single Lottie splash (purple bg + School Explorer text animation).
        LottieAnimationView lottieView = findViewById(R.id.lottieSplash);
        View cap = findViewById(R.id.ivSplashCap);

        // Keep Lottie as-is: do NOT fade the whole container (text timing is inside the composition).
        if (cap != null) {
            cap.setVisibility(View.INVISIBLE);
            cap.setAlpha(0f);
            cap.setScaleX(0.9f);
            cap.setScaleY(0.9f);
        }

        if (lottieView != null) {
            lottieView.setClipToCompositionBounds(false);
            lottieView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lottieView.setScaleX(1.0f);
            lottieView.setScaleY(1.0f);
            lottieView.setSafeMode(true);
            // Start immediately (earlier than waiting for auto-play on attach/layout).
            lottieView.cancelAnimation();
            lottieView.setProgress(0f);
            lottieView.playAnimation();
            lottieView.setFailureListener(result -> scheduleNavigateWithMinDelay());
            lottieView.addAnimatorListener(new Animator.AnimatorListener() {
                @Override public void onAnimationStart(Animator animation) {
                    // Start hat slightly after Lottie begins, so it follows the text.
                    startHatEntranceAnimAfterLottieStarts(cap);
                }
                @Override public void onAnimationEnd(Animator animation) { scheduleNavigateWithMinDelay(); }
                @Override public void onAnimationCancel(Animator animation) { scheduleNavigateWithMinDelay(); }
                @Override public void onAnimationRepeat(Animator animation) {}
            });
        }

        // Fallback: if animator callbacks don't arrive, still navigate after min duration.
        scheduleNavigateWithMinDelay();
    }

    private void startHatEntranceAnimAfterLottieStarts(View cap) {
        if (capShown || cap == null) {
            return;
        }
        capShown = true;
        handler.postDelayed(() -> {
            if (cap == null) return;
            cap.setVisibility(View.VISIBLE);
            cap.animate()
                    .alpha(1f)
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                    .setDuration(420)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
                }, 1000L);
    }

    private void scheduleNavigateWithMinDelay() {
        if (hasNavigated) {
            return;
        }
        long elapsed = SystemClock.uptimeMillis() - startUptimeMs;
        long remaining = Math.max(0L, SPLASH_MIN_DURATION_MS - elapsed);
        handler.removeCallbacks(this::navigateNext);
        handler.postDelayed(this::navigateNext, remaining);
    }

    private void navigateNext() {
        if (hasNavigated) {
            return;
        }
        hasNavigated = true;
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        // Prevent returning to splash via back button.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.ssf_fade_in, R.anim.ssf_fade_out);
        finish();
    }
}

