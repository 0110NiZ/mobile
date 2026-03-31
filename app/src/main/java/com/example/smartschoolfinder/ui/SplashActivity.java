package com.example.smartschoolfinder.ui;

import android.content.Intent;
import android.animation.Animator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartschoolfinder.R;
import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    // Keep splash long enough for text/brand to be readable.
    private static final long SPLASH_MIN_DURATION_MS = 2400L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startUptimeMs;
    private boolean hasNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        startUptimeMs = SystemClock.uptimeMillis();

        // Restore original: single Lottie splash (purple bg + School Explorer text animation).
        LottieAnimationView lottieView = findViewById(R.id.lottieSplash);
        if (lottieView != null) {
            lottieView.setClipToCompositionBounds(false);
            lottieView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lottieView.setScaleX(1.0f);
            lottieView.setScaleY(1.0f);
            lottieView.setSafeMode(true);
            lottieView.setFailureListener(result -> scheduleNavigateWithMinDelay());
            lottieView.addAnimatorListener(new Animator.AnimatorListener() {
                @Override public void onAnimationStart(Animator animation) {}
                @Override public void onAnimationEnd(Animator animation) { scheduleNavigateWithMinDelay(); }
                @Override public void onAnimationCancel(Animator animation) { scheduleNavigateWithMinDelay(); }
                @Override public void onAnimationRepeat(Animator animation) {}
            });
        }

        // Fallback: if animator callbacks don't arrive, still navigate after min duration.
        scheduleNavigateWithMinDelay();
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

