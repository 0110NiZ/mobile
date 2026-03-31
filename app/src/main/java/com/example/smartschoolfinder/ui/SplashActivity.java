package com.example.smartschoolfinder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartschoolfinder.R;
import com.airbnb.lottie.LottieAnimationView;
import android.animation.Animator;

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

        // If the animation cannot be parsed, don't crash the app.
        LottieAnimationView lottieView = findViewById(R.id.lottieSplash);
        if (lottieView != null) {
            // Avoid clipping text layers that slightly exceed comp bounds.
            lottieView.setClipToCompositionBounds(false);
            // Use FIT_CENTER so "School Explorer" is fully visible on all screens.
            // Keep Galada font from assets/fonts/Galada.ttf (Lottie will load it automatically).
            lottieView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lottieView.setScaleX(1.0f);
            lottieView.setScaleY(1.0f);
            lottieView.setSafeMode(true);
            lottieView.setFailureListener(result -> scheduleNavigateWithMinDelay());
            lottieView.addAnimatorListener(new Animator.AnimatorListener() {
                @Override public void onAnimationStart(Animator animation) {}
                @Override public void onAnimationEnd(Animator animation) {
                    scheduleNavigateWithMinDelay();
                }
                @Override public void onAnimationCancel(Animator animation) {
                    scheduleNavigateWithMinDelay();
                }
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

