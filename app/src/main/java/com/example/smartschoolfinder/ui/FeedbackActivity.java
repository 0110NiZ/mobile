package com.example.smartschoolfinder.ui;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.data.FeedbackRepository;
import com.example.smartschoolfinder.network.ApiCallback;

public class FeedbackActivity extends AppCompatActivity {
    private RatingBar ratingFeedback;
    private EditText etFeedbackComment;
    private Button btnSubmitFeedback;
    private FeedbackRepository feedbackRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        feedbackRepository = new FeedbackRepository(this);

        View btnBack = findViewById(R.id.btnBackFeedback);
        ratingFeedback = findViewById(R.id.ratingFeedback);
        etFeedbackComment = findViewById(R.id.etFeedbackComment);
        btnSubmitFeedback = findViewById(R.id.btnSubmitFeedback);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (ratingFeedback != null) {
            ratingFeedback.setStepSize(1f);
            ratingFeedback.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
                if (fromUser) {
                    bar.setRating(Math.round(rating));
                }
            });
        }

        applyPressFeedback(btnSubmitFeedback);
        if (btnSubmitFeedback != null) {
            btnSubmitFeedback.setOnClickListener(v -> submitFeedback());
        }
    }

    private void submitFeedback() {
        int rating = ratingFeedback == null ? 0 : Math.round(ratingFeedback.getRating());
        String comment = etFeedbackComment == null || etFeedbackComment.getText() == null
                ? ""
                : etFeedbackComment.getText().toString().trim();

        if (rating <= 0) {
            Toast.makeText(this, R.string.review_rating_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (comment.isEmpty()) {
            Toast.makeText(this, R.string.feedback_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnSubmitFeedback != null) {
            btnSubmitFeedback.setEnabled(false);
        }
        feedbackRepository.submitFeedback(rating, comment, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                if (btnSubmitFeedback != null) {
                    btnSubmitFeedback.setEnabled(true);
                }
                if (ratingFeedback != null) {
                    ratingFeedback.setRating(0f);
                }
                if (etFeedbackComment != null) {
                    etFeedbackComment.setText("");
                }
                Toast.makeText(FeedbackActivity.this, R.string.feedback_sent, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (btnSubmitFeedback != null) {
                    btnSubmitFeedback.setEnabled(true);
                }
                Toast.makeText(FeedbackActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyPressFeedback(View... views) {
        for (View v : views) {
            if (v == null) continue;
            v.setOnTouchListener((view, event) -> {
                if (!view.isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.95f).setDuration(100).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
                }
                return false;
            });
        }
    }
}

