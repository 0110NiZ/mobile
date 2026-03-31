package com.example.smartschoolfinder.ui;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.Spinner;
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
import com.example.smartschoolfinder.model.ReviewListResponse;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.DeviceUserIdManager;
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
    private Spinner spinnerReviewSort;
    private String currentSort = "latest";
    private String deviceUserId;

    private final List<Review> reviews = new ArrayList<>();
    private ReviewAdapter reviewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        favoritesManager = new FavoritesManager(this);
        reviewRepository = new ReviewRepository(this);
        deviceUserId = DeviceUserIdManager.getOrCreate(this);

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
        spinnerReviewSort = findViewById(R.id.spinnerReviewSort);
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

        setupSortSpinner();

        reviewAdapter = new ReviewAdapter(
                reviews,
                (review, action) -> {
            if (review == null || review.getId() == null) {
                return;
            }
            reviewRepository.reactToReview(review.getId(), deviceUserId, action, new ApiCallback<ReviewRepository.ReactionResult>() {
                @Override
                public void onSuccess(ReviewRepository.ReactionResult data) {
                    if (data == null) return;
                    review.setLikes(data.likes);
                    review.setDislikes(data.dislikes);
                    review.setUserReaction(data.userReaction);
                    int state = "like".equalsIgnoreCase(data.userReaction) ? 1 : ("dislike".equalsIgnoreCase(data.userReaction) ? -1 : 0);
                    reviewAdapter.setLocalReaction(review.getId(), state);
                    adjustListViewHeightBasedOnChildren(listReviews);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        },
                new ReviewAdapter.OnOwnerActionListener() {
                    @Override
                    public void onEdit(Review review) {
                        showEditDialog(review);
                    }

                    @Override
                    public void onDelete(Review review) {
                        confirmDelete(review);
                    }
                }
        );
        listReviews.setAdapter(reviewAdapter);
        listReviews.setFocusable(false);

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
        if (school == null) {
            return;
        }
        reviewRepository.getReviews(school.getId(), currentSort, deviceUserId, new ApiCallback<ReviewListResponse>() {
            @Override
            public void onSuccess(ReviewListResponse data) {
                reviews.clear();
                if (data != null && data.getReviews() != null) {
                    reviews.addAll(data.getReviews());
                }
                reviewAdapter.notifyDataSetChanged();
                ratingAverage.setRating(data == null ? 0f : (float) data.getAverageRating());
                if (tvReviewsEmpty != null) {
                    tvReviewsEmpty.setVisibility(reviews.isEmpty() ? View.VISIBLE : View.GONE);
                }
                adjustListViewHeightBasedOnChildren(listReviews);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                reviews.clear();
                reviewAdapter.notifyDataSetChanged();
                ratingAverage.setRating(0f);
                if (tvReviewsEmpty != null) {
                    tvReviewsEmpty.setVisibility(View.VISIBLE);
                }
                adjustListViewHeightBasedOnChildren(listReviews);
            }
        });
    }

    private void addReview() {
        if (school == null) {
            return;
        }
        // Fix IME composing text (Chinese input): commit composing text before reading.
        etComment.clearComposingText();
        hideKeyboard();
        String raw = etComment.getText() == null ? "" : etComment.getText().toString();
        String comment = raw.replace("\u200B", "").trim();
        if (comment.isEmpty()) {
            Toast.makeText(this, R.string.review_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        int rating = Math.round(ratingInput.getRating());
        if (rating <= 0) {
            Toast.makeText(this, R.string.review_rating_required, Toast.LENGTH_SHORT).show();
            return;
        }
        reviewRepository.addReview(school.getId(), deviceUserId, "Guest User", rating, comment, new ApiCallback<Review>() {
            @Override
            public void onSuccess(Review data) {
                etComment.setText("");
                ratingInput.setRating(0f); // Reset to unrated after successful submit.
                loadReviews();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditDialog(Review review) {
        if (review == null || review.getId() == null) return;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_review, null, false);
        RatingBar ratingEdit = content.findViewById(R.id.ratingEdit);
        EditText etEdit = content.findViewById(R.id.etEditComment);

        ratingEdit.setRating(review.getRating());
        etEdit.setText(review.getComment());

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_review)
                .setView(content)
                .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.save, (d, which) -> {
                    etEdit.clearComposingText();
                    hideKeyboard();
                    String raw = etEdit.getText() == null ? "" : etEdit.getText().toString();
                    String comment = raw.replace("\u200B", "").trim();
                    int rating = Math.round(ratingEdit.getRating());
                    if (rating <= 0) {
                        Toast.makeText(this, R.string.review_rating_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (comment.isEmpty()) {
                        Toast.makeText(this, R.string.review_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reviewRepository.updateReview(review.getId(), deviceUserId, rating, comment, new ApiCallback<Review>() {
                        @Override
                        public void onSuccess(Review data) {
                            loadReviews();
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    private void confirmDelete(Review review) {
        if (review == null || review.getId() == null) return;
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_confirm)
                .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.delete, (d, which) -> {
                    reviewRepository.deleteReview(review.getId(), deviceUserId, new ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean data) {
                            loadReviews();
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    private void hideKeyboard() {
        try {
            View v = getCurrentFocus();
            if (v == null) v = etComment;
            if (v == null) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void setupSortSpinner() {
        if (spinnerReviewSort == null) return;
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.review_sort_latest));
        options.add(getString(R.string.review_sort_positive));
        options.add(getString(R.string.review_sort_negative));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReviewSort.setAdapter(adapter);
        spinnerReviewSort.setSelection(0);

        spinnerReviewSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String nextSort = "latest";
                if (position == 1) nextSort = "high_rating";
                if (position == 2) nextSort = "low_rating";
                if (!nextSort.equals(currentSort)) {
                    currentSort = nextSort;
                    loadReviews();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * ListView inside ScrollView: expand to full height so the whole page scrolls (no inner scroll).
     */
    private void adjustListViewHeightBasedOnChildren(ListView listView) {
        if (listView == null) return;
        if (listView.getAdapter() == null) return;

        int totalHeight = 0;
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST);
        for (int i = 0; i < listView.getAdapter().getCount(); i++) {
            View listItem = listView.getAdapter().getView(i, null, listView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += listItem.getMeasuredHeight();
        }

        int dividerHeight = listView.getDividerHeight();
        int totalDividers = Math.max(0, listView.getAdapter().getCount() - 1);
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + dividerHeight * totalDividers;
        listView.setLayoutParams(params);
        listView.requestLayout();
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
