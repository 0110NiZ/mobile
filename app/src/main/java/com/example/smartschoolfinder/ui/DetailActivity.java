package com.example.smartschoolfinder.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
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
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.ReviewRecyclerAdapter;
import com.example.smartschoolfinder.data.CompareRepository;
import com.example.smartschoolfinder.data.FavoritesManager;
import com.example.smartschoolfinder.data.ReviewRepository;
import com.example.smartschoolfinder.data.TransportRepository;
import com.example.smartschoolfinder.model.Review;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.model.TransportInfo;
import com.example.smartschoolfinder.model.ReviewListResponse;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.DeviceUserIdManager;
import com.example.smartschoolfinder.utils.IntentUtils;
import com.example.smartschoolfinder.utils.LocaleUtils;
import com.example.smartschoolfinder.utils.TransportUiFormatter;

import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "ssf_user_prefs";
    private static final String KEY_USER_NICKNAME = "user_nickname";

    private School school;
    private FavoritesManager favoritesManager;
    private ReviewRepository reviewRepository;
    private TransportRepository transportRepository;

    private TextView tvName;
    private TextView tvAddress;
    private TextView tvPhone;
    private TextView tvDistrict;
    private TextView tvType;
    private TextView tvTuition;
    private TextView tvTransportMtr;
    private TextView tvTransportBus;
    private TextView tvTransportMinibus;
    private TextView tvTransportConvenience;
    private TextView tvAvgScore;
    private TextView tvAvgDesc;
    private ProgressBar pbStar5;
    private ProgressBar pbStar4;
    private ProgressBar pbStar3;
    private ProgressBar pbStar2;
    private ProgressBar pbStar1;

    private RecyclerView listReviews;
    private TextView tvReviewsEmpty;
    private EditText etComment;
    private RatingBar ratingInput;
    private Spinner spinnerReviewSort;
    private String currentSort = "latest";
    private String deviceUserId;

    private final List<Review> reviews = new ArrayList<>();
    private ReviewRecyclerAdapter reviewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        favoritesManager = new FavoritesManager(this);
        reviewRepository = new ReviewRepository(this);
        transportRepository = new TransportRepository();
        deviceUserId = DeviceUserIdManager.getOrCreate(this);

        tvName = findViewById(R.id.tvDetailName);
        tvAddress = findViewById(R.id.tvDetailAddress);
        tvPhone = findViewById(R.id.tvDetailPhone);
        tvDistrict = findViewById(R.id.tvDetailDistrict);
        tvType = findViewById(R.id.tvDetailType);
        tvTuition = findViewById(R.id.tvDetailTuition);
        tvTransportMtr = findViewById(R.id.tvTransportMtr);
        tvTransportBus = findViewById(R.id.tvTransportBus);
        tvTransportMinibus = findViewById(R.id.tvTransportMinibus);
        tvTransportConvenience = findViewById(R.id.tvTransportConvenience);
        resetTransportViewsToPlaceholder();
        tvAvgScore = findViewById(R.id.tvAvgScore);
        tvAvgDesc = findViewById(R.id.tvAvgDesc);
        pbStar5 = findViewById(R.id.pbStar5);
        pbStar4 = findViewById(R.id.pbStar4);
        pbStar3 = findViewById(R.id.pbStar3);
        pbStar2 = findViewById(R.id.pbStar2);
        pbStar1 = findViewById(R.id.pbStar1);

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

        reviewAdapter = new ReviewRecyclerAdapter(
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
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        },
                new ReviewRecyclerAdapter.OnOwnerActionListener() {
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
        listReviews.setLayoutManager(new LinearLayoutManager(this));
        listReviews.setNestedScrollingEnabled(false); // single main scroll area (NestedScrollView)
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
        new SchoolApiService().getSchools(this, new ApiCallback<List<School>>() {
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
        bindTransportInfo();

        Button btnFavorite = findViewById(R.id.btnFavorite);
        updateFavoriteButton(btnFavorite);
        loadReviews();
    }

    private void resetTransportViewsToPlaceholder() {
        if (tvTransportMtr != null) tvTransportMtr.setText(R.string.transport_na_mtr);
        if (tvTransportBus != null) tvTransportBus.setText(R.string.transport_na_bus);
        if (tvTransportMinibus != null) tvTransportMinibus.setText(R.string.transport_na_minibus);
        if (tvTransportConvenience != null) {
            tvTransportConvenience.setText(R.string.transport_na_convenience);
        }
    }

    private void bindTransportInfo() {
        if (school == null || school.getId() == null) {
            return;
        }
        resetTransportViewsToPlaceholder();
        boolean preferZh = LocaleUtils.prefersChineseSchoolData(this);
        transportRepository.getSchoolTransport(school.getId(), preferZh, new ApiCallback<TransportInfo>() {
            @Override
            public void onSuccess(TransportInfo data) {
                if (data == null) {
                    resetTransportViewsToPlaceholder();
                    return;
                }
                bindTransportInfoToViews(data);
            }

            @Override
            public void onError(String message) {
                resetTransportViewsToPlaceholder();
            }
        });
    }

    private void bindTransportInfoToViews(TransportInfo info) {
        if (info == null) {
            return;
        }
        if (tvTransportMtr != null) {
            tvTransportMtr.setText(TransportUiFormatter.lineMtr(this, info));
        }
        if (tvTransportBus != null) {
            tvTransportBus.setText(TransportUiFormatter.lineBus(this, info));
        }
        if (tvTransportMinibus != null) {
            tvTransportMinibus.setText(TransportUiFormatter.lineMinibus(this, info));
        }
        if (tvTransportConvenience != null) {
            tvTransportConvenience.setText(TransportUiFormatter.lineConvenience(this, info));
        }
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
                updateRatingSummaryFromReviews(data);
                if (tvReviewsEmpty != null) {
                    tvReviewsEmpty.setVisibility(reviews.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                reviews.clear();
                reviewAdapter.notifyDataSetChanged();
                updateRatingSummaryEmpty();
                if (tvReviewsEmpty != null) {
                    tvReviewsEmpty.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void addReview() {
        if (school == null) {
            return;
        }
        // Fix IME composing text (Chinese input): commit composing text before reading.
        etComment.clearComposingText();
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
        ensureNicknameThenSubmit(comment, rating);
    }

    private void ensureNicknameThenSubmit(String comment, int rating) {
        String nickname = getNickname();
        if (!nickname.isEmpty()) {
            submitReviewWithNickname(comment, rating, nickname);
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_set_nickname, null, false);
        EditText input = content.findViewById(R.id.etNicknameInput);
        Button btnCancel = content.findViewById(R.id.btnNicknameCancel);
        Button btnConfirm = content.findViewById(R.id.btnNicknameConfirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set Your Nickname")
                .setView(content)
                .create();
        dialog.show();
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(this, "Nickname cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            saveNickname(value);
            dialog.dismiss();
            submitReviewWithNickname(comment, rating, value);
        });
    }

    private void submitReviewWithNickname(String comment, int rating, String nickname) {
        hideKeyboard();
        reviewRepository.addReview(school.getId(), deviceUserId, nickname, rating, comment, new ApiCallback<Review>() {
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

    private void saveNickname(String nickname) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_NICKNAME, nickname).apply();
    }

    private String getNickname() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_USER_NICKNAME, "").trim();
    }

    private void updateRatingSummaryFromReviews(ReviewListResponse response) {
        int total = reviews.size();
        if (total <= 0) {
            updateRatingSummaryEmpty();
            return;
        }

        int c5 = 0, c4 = 0, c3 = 0, c2 = 0, c1 = 0;
        int sum = 0;
        for (Review r : reviews) {
            if (r == null) continue;
            int rating = r.getRating();
            sum += rating;
            if (rating == 5) c5++;
            else if (rating == 4) c4++;
            else if (rating == 3) c3++;
            else if (rating == 2) c2++;
            else if (rating == 1) c1++;
        }

        double avg5 = response != null ? response.getAverageRating() : ((double) sum / total);
        double score10 = avg5 * 2.0; // Douban-like 10 scale
        if (tvAvgScore != null) {
            tvAvgScore.setText(String.format(java.util.Locale.getDefault(), "%.1f", score10));
        }
        if (tvAvgDesc != null) {
            tvAvgDesc.setText(pickDesc(avg5));
        }

        setPercent(pbStar5, c5, total);
        setPercent(pbStar4, c4, total);
        setPercent(pbStar3, c3, total);
        setPercent(pbStar2, c2, total);
        setPercent(pbStar1, c1, total);
    }

    private void updateRatingSummaryEmpty() {
        if (tvAvgScore != null) tvAvgScore.setText("0.0");
        if (tvAvgDesc != null) tvAvgDesc.setText("Average");
        if (pbStar5 != null) pbStar5.setProgress(0);
        if (pbStar4 != null) pbStar4.setProgress(0);
        if (pbStar3 != null) pbStar3.setProgress(0);
        if (pbStar2 != null) pbStar2.setProgress(0);
        if (pbStar1 != null) pbStar1.setProgress(0);
    }

    private void setPercent(ProgressBar pb, int count, int total) {
        if (pb == null) return;
        if (total <= 0) {
            pb.setProgress(0);
            return;
        }
        int percent = Math.round((count * 100f) / total);
        pb.setProgress(percent);
    }

    private String pickDesc(double avg5) {
        if (avg5 >= 4.5) return "Excellent";
        if (avg5 >= 4.0) return "Very Good";
        if (avg5 >= 3.5) return "Good";
        if (avg5 >= 3.0) return "Average";
        return "Poor";
    }

    private void showEditDialog(Review review) {
        if (review == null || review.getId() == null) return;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_review, null, false);
        RatingBar ratingEdit = content.findViewById(R.id.ratingEdit);
        EditText etEdit = content.findViewById(R.id.etEditComment);
        Button btnCancel = content.findViewById(R.id.btnEditCancel);
        Button btnSave = content.findViewById(R.id.btnEditSave);

        ratingEdit.setRating(review.getRating());
        etEdit.setText(review.getComment());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.edit_review)
                .setView(content)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
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
                    dialog.dismiss();
                    loadReviews();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void confirmDelete(Review review) {
        if (review == null || review.getId() == null) return;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_delete_review, null, false);
        Button btnCancel = content.findViewById(R.id.btnDeleteCancel);
        Button btnDelete = content.findViewById(R.id.btnDeleteConfirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDelete.setOnClickListener(v -> {
            reviewRepository.deleteReview(review.getId(), deviceUserId, new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean data) {
                    dialog.dismiss();
                    loadReviews();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
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
