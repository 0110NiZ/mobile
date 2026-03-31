package com.example.smartschoolfinder.ui;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.ReviewAdapter;
import com.example.smartschoolfinder.data.CompareRepository;
import com.example.smartschoolfinder.data.FavoritesManager;
import com.example.smartschoolfinder.data.MockTransportProvider;
import com.example.smartschoolfinder.data.ReviewRepository;
import com.example.smartschoolfinder.model.Review;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.IntentUtils;

import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends AppCompatActivity {

    private School school;
    private FavoritesManager favoritesManager;
    private ReviewRepository reviewRepository;

    private TextView tvName;
    private TextView tvAddress;
    private TextView tvPhone;
    private TextView tvDistrict;
    private TextView tvType;
    private TextView tvTuition;
    private TextView tvTransport;
    private RatingBar ratingAverage;

    private ListView listReviews;
    private TextView tvReviewsEmpty;
    private EditText etComment;
    private RatingBar ratingInput;

    private final List<Review> reviews = new ArrayList<>();
    private ReviewAdapter reviewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        favoritesManager = new FavoritesManager(this);
        reviewRepository = new ReviewRepository(this);

        tvName = findViewById(R.id.tvDetailName);
        tvAddress = findViewById(R.id.tvDetailAddress);
        tvPhone = findViewById(R.id.tvDetailPhone);
        tvDistrict = findViewById(R.id.tvDetailDistrict);
        tvType = findViewById(R.id.tvDetailType);
        tvTuition = findViewById(R.id.tvDetailTuition);
        tvTransport = findViewById(R.id.tvTransportInfo);
        ratingAverage = findViewById(R.id.ratingAverage);

        listReviews = findViewById(R.id.listReviews);
        tvReviewsEmpty = findViewById(R.id.tvReviewsEmpty);
        etComment = findViewById(R.id.etComment);
        ratingInput = findViewById(R.id.ratingInput);
        ratingInput.setRating(0f); // Initial state: unrated (all gray stars).

        Button btnFavorite = findViewById(R.id.btnFavorite);
        Button btnCall = findViewById(R.id.btnCall);
        Button btnMap = findViewById(R.id.btnMap);
        Button btnAddReview = findViewById(R.id.btnAddReview);
        Button btnAddCompare = findViewById(R.id.btnAddCompare);
        applyPressFeedback(btnFavorite, btnCall, btnMap, btnAddReview, btnAddCompare);

        // Use interactive stars instead of dropdown for rating selection.
        ratingInput.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (!fromUser) {
                return;
            }
            // Keep direct click-to-select behavior; 0 means "not selected yet".
            ratingBar.setRating(Math.round(rating));
            ratingBar.animate().scaleX(1.05f).scaleY(1.05f).setDuration(90).withEndAction(
                    () -> ratingBar.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            ).start();
        });

        reviewAdapter = new ReviewAdapter(reviews);
        listReviews.setAdapter(reviewAdapter);

        String schoolId = getIntent().getStringExtra("school_id");

        btnFavorite.setOnClickListener(v -> {
            if (school == null) {
                return;
            }
            favoritesManager.toggleFavorite(school.getId());
            updateFavoriteButton(btnFavorite);
        });

        btnCall.setOnClickListener(v -> {
            if (school != null) {
                IntentUtils.openDial(this, school.getPhone());
            }
        });

        btnMap.setOnClickListener(v -> {
            if (school != null) {
                IntentUtils.openMap(this, school.getAddress(), school.getLatitude(), school.getLongitude());
            }
        });

        btnAddReview.setOnClickListener(v -> addReview());

        btnAddCompare.setOnClickListener(v -> {
            if (school == null) {
                return;
            }
            if (CompareRepository.getSchoolAId() == null) {
                CompareRepository.setSchoolAId(school.getId());
                Toast.makeText(this, R.string.compare_added_a, Toast.LENGTH_SHORT).show();
            } else {
                CompareRepository.setSchoolBId(school.getId());
                Toast.makeText(this, R.string.compare_added_b, Toast.LENGTH_SHORT).show();
            }
        });

        loadSchool(schoolId);
    }

    private void loadSchool(String schoolId) {
        new SchoolApiService().getSchools(new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                for (School s : data) {
                    if (s.getId().equals(schoolId)) {
                        school = s;
                        bindSchool();
                        break;
                    }
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindSchool() {
        if (school == null) {
            return;
        }
        tvName.setText(school.getName());
        tvAddress.setText(getString(R.string.label_address, school.getAddress()));
        tvPhone.setText(getString(R.string.label_phone, school.getPhone()));
        tvDistrict.setText(getString(R.string.label_district, school.getDistrict()));
        tvType.setText(getString(R.string.label_type, school.getType()));
        tvTuition.setText(getString(R.string.label_tuition, school.getTuition()));
        tvTransport.setText(MockTransportProvider.buildTransportText(school));

        Button btnFavorite = findViewById(R.id.btnFavorite);
        updateFavoriteButton(btnFavorite);
        loadReviews();
    }

    private void updateFavoriteButton(Button btnFavorite) {
        if (school == null) return;
        btnFavorite.setText(favoritesManager.isFavorite(school.getId())
                ? R.string.remove_favorite
                : R.string.add_favorite);
    }

    private void loadReviews() {
        reviews.clear();
        reviews.addAll(reviewRepository.getReviews(school.getId()));
        reviewAdapter.notifyDataSetChanged();
        updateAverageRating();
        if (tvReviewsEmpty != null) {
            tvReviewsEmpty.setVisibility(reviews.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void addReview() {
        if (school == null) {
            return;
        }
        String comment = etComment.getText().toString().trim();
        if (comment.isEmpty()) {
            Toast.makeText(this, R.string.review_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        int rating = Math.round(ratingInput.getRating());
        if (rating <= 0) {
            Toast.makeText(this, R.string.review_rating_required, Toast.LENGTH_SHORT).show();
            return;
        }
        reviewRepository.addReview(school.getId(), rating, comment);
        etComment.setText("");
        ratingInput.setRating(0f); // Reset to unrated after successful submit.
        loadReviews();
    }

    private void updateAverageRating() {
        if (reviews.isEmpty()) {
            ratingAverage.setRating(0f);
            return;
        }
        int sum = 0;
        for (Review review : reviews) {
            sum += review.getRating();
        }
        ratingAverage.setRating((float) sum / reviews.size());
    }

    private void applyPressFeedback(View... views) {
        for (View view : views) {
            view.setOnTouchListener((v, event) -> {
                if (!v.isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.95f).setDuration(100).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
                }
                return false;
            });
        }
    }
}
