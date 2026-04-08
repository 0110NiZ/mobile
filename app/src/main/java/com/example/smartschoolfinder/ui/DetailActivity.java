package com.example.smartschoolfinder.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.TypedValue;
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
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.example.smartschoolfinder.utils.SchoolDisplayUtils;
import com.example.smartschoolfinder.utils.TransportUiFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    private static final long ICON_BOUNCE_DURATION_MS = 120L;
    private static final float FAVORITE_ICON_SCALE = 2f;
    private static final String PREFS_NAME = "ssf_user_prefs";
    private static final String KEY_USER_NICKNAME = "user_nickname";
    private static final String COMMENT_REPLY_DEBUG = "COMMENT_REPLY_DEBUG";
    private static final String EXTRA_FOCUS_COMMENT_ID = "focus_comment_id";

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
    private TextView tvReligion;
    private TextView tvWebsite;
    private TextView tvTransportMtr;
    private TextView tvTransportBus;
    private TextView tvTransportMinibus;
    private TextView tvTransportConvenience;
    private View layoutTransportNormal;
    private View layoutTransportEmpty;
    private TextView tvTransportEmptyPrimary;
    private TextView tvTransportEmptySecondary;
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
    private String pendingFocusCommentId;
    private ImageButton btnSpeakInfo;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean isSpeaking = false;
    private AnimatorSet speakPulseAnimator;
    private ImageView ivFavoriteIcon;
    private TextView tvFavoriteLabel;

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
        tvReligion = findViewById(R.id.tvDetailReligion);
        tvWebsite = findViewById(R.id.tvDetailWebsite);
        tvTransportMtr = findViewById(R.id.tvTransportMtr);
        tvTransportBus = findViewById(R.id.tvTransportBus);
        tvTransportMinibus = findViewById(R.id.tvTransportMinibus);
        tvTransportConvenience = findViewById(R.id.tvTransportConvenience);
        layoutTransportNormal = findViewById(R.id.layoutTransportNormal);
        layoutTransportEmpty = findViewById(R.id.layoutTransportEmpty);
        tvTransportEmptyPrimary = findViewById(R.id.tvTransportEmptyPrimary);
        tvTransportEmptySecondary = findViewById(R.id.tvTransportEmptySecondary);
        prepareTransportViewsForFirstBind();
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

        View btnFavorite = findViewById(R.id.btnFavorite);
        View btnCall = findViewById(R.id.btnCall);
        View btnMap = findViewById(R.id.btnMap);
        Button btnAddReview = findViewById(R.id.btnAddReview);
        Button btnAddCompare = findViewById(R.id.btnAddCompare);
        btnSpeakInfo = findViewById(R.id.btnSpeakInfo);
        ivFavoriteIcon = findViewById(R.id.ivFavoriteIcon);
        tvFavoriteLabel = findViewById(R.id.tvFavoriteLabel);
        TextView tvCallLabel = findViewById(R.id.tvCallLabel);
        TextView tvMapLabel = findViewById(R.id.tvMapLabel);
        setUnderlined(tvCallLabel);
        setUnderlined(tvMapLabel);
        setUnderlined(tvFavoriteLabel);
        applyPressFeedback(btnFavorite, btnCall, btnMap, btnAddReview, btnAddCompare, btnSpeakInfo);
        if (btnSpeakInfo != null) {
            btnSpeakInfo.setOnClickListener(v -> {
                if (isSpeaking) {
                    stopSpeaking();
                } else {
                    speakSchoolInfo();
                }
            });
            btnSpeakInfo.setEnabled(false);
            updateSpeakButtonLabel();
        }
        initTextToSpeech();

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
                },
                (parentReview, replyContent) -> submitReply(parentReview, replyContent),
                this::shareReview
        );
        listReviews.setLayoutManager(new LinearLayoutManager(this));
        listReviews.setNestedScrollingEnabled(false); // single main scroll area (NestedScrollView)
        listReviews.setAdapter(reviewAdapter);

        String schoolId = getIntent().getStringExtra("school_id");
        pendingFocusCommentId = getIntent().getStringExtra(EXTRA_FOCUS_COMMENT_ID);

        btnFavorite.setOnClickListener(v -> {
            if (school == null) {
                return;
            }
            boolean wasFavorite = favoritesManager.isFavorite(school.getId());
            favoritesManager.toggleFavorite(school.getId());
            updateFavoriteButton();
            animateFavoriteIcon(!wasFavorite);
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

    private void initTextToSpeech() {
        if (textToSpeech != null) {
            return;
        }
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, R.string.tts_init_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            boolean languageOk = setupTtsLanguage();
            if (!languageOk) {
                Toast.makeText(this, R.string.tts_not_supported, Toast.LENGTH_SHORT).show();
                return;
            }
            ttsReady = true;
            if (btnSpeakInfo != null) {
                btnSpeakInfo.setEnabled(true);
                updateSpeakButtonLabel();
            }
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    runOnUiThread(() -> {
                        isSpeaking = true;
                        updateSpeakButtonLabel();
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        isSpeaking = false;
                        updateSpeakButtonLabel();
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    runOnUiThread(() -> {
                        isSpeaking = false;
                        updateSpeakButtonLabel();
                    });
                }
            });
        });
    }

    private boolean setupTtsLanguage() {
        if (textToSpeech == null) {
            return false;
        }
        boolean preferZh = LocaleUtils.prefersChineseSchoolData(this);
        if (!preferZh) {
            int result = textToSpeech.setLanguage(Locale.US);
            textToSpeech.setSpeechRate(1.0f);
            textToSpeech.setPitch(1.0f);
            return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
        }

        // Chinese UI: prefer Cantonese first, then Traditional Chinese fallback.
        Locale[] candidates = new Locale[] {
                new Locale("yue", "HK"),
                Locale.forLanguageTag("yue-HK"),
                Locale.forLanguageTag("zh-HK"),
                Locale.TRADITIONAL_CHINESE
        };
        for (Locale locale : candidates) {
            int result = textToSpeech.setLanguage(locale);
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                // Slightly slower Chinese speech rate improves naturalness and reduces stutter.
                textToSpeech.setSpeechRate(0.92f);
                textToSpeech.setPitch(1.0f);
                return true;
            }
        }
        return false;
    }

    private void speakSchoolInfo() {
        if (!ttsReady || textToSpeech == null) {
            Toast.makeText(this, R.string.tts_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (school == null) {
            return;
        }
        String speech = buildSpeechText(school, LocaleUtils.prefersChineseSchoolData(this));
        if (speech.trim().isEmpty()) {
            return;
        }
        // Always cancel current utterance first to avoid overlap/jitter on rapid taps.
        textToSpeech.stop();
        textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "detail_speech");
    }

    private void stopSpeaking() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        isSpeaking = false;
        updateSpeakButtonLabel();
    }

    private void updateSpeakButtonLabel() {
        if (btnSpeakInfo == null) return;
        btnSpeakInfo.setBackgroundResource(isSpeaking ? R.drawable.bg_speak_icon_active : R.drawable.bg_speak_icon_inactive);
        btnSpeakInfo.setContentDescription(getString(isSpeaking ? R.string.stop_speaking : R.string.speak));
        btnSpeakInfo.setColorFilter(resolveThemeColor(
                isSpeaking ? com.google.android.material.R.attr.colorOnPrimaryContainer : android.R.attr.textColorSecondary
        ));
        btnSpeakInfo.setAlpha(btnSpeakInfo.isEnabled() ? 1f : 0.5f);
        if (isSpeaking && btnSpeakInfo.isEnabled()) {
            startSpeakPulse();
        } else {
            stopSpeakPulse();
        }
    }

    private int resolveThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return 0xFF5A3E99;
    }

    private void startSpeakPulse() {
        if (btnSpeakInfo == null) return;
        if (speakPulseAnimator != null && speakPulseAnimator.isRunning()) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(btnSpeakInfo, View.SCALE_X, 1f, 1.08f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(btnSpeakInfo, View.SCALE_Y, 1f, 1.08f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(btnSpeakInfo, View.ALPHA, 1f, 0.9f, 1f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setDuration(900);
        scaleY.setDuration(900);
        alpha.setDuration(900);
        speakPulseAnimator = new AnimatorSet();
        speakPulseAnimator.playTogether(scaleX, scaleY, alpha);
        speakPulseAnimator.start();
    }

    private void stopSpeakPulse() {
        if (speakPulseAnimator != null) {
            speakPulseAnimator.cancel();
            speakPulseAnimator = null;
        }
        if (btnSpeakInfo != null) {
            btnSpeakInfo.setScaleX(1f);
            btnSpeakInfo.setScaleY(1f);
            btnSpeakInfo.setAlpha(btnSpeakInfo.isEnabled() ? 1f : 0.5f);
        }
    }

    private String buildSpeechText(School data, boolean isChinese) {
        if (data == null) {
            return isChinese ? getString(R.string.speech_not_available) : getString(R.string.speech_not_available);
        }
        String name = speechValue(SchoolDisplayUtils.displayName(this, data));
        String address = speechValue(SchoolDisplayUtils.displayAddress(this, data));
        String phone = isChinese ? formatPhoneForTts(data.getPhone()) : formatPhoneForEnglishSpeech(data.getPhone());
        String district = speechValue(SchoolDisplayUtils.displayDistrict(this, data));
        String type = speechValue(SchoolDisplayUtils.displayType(this, data));
        String tuition = speechValue(data.getTuition());
        String religionDisplay = SchoolDisplayUtils.displayReligion(this, data);
        String religion = normalizeReligionForSpeech(religionDisplay, isChinese);
        if (isChinese) {
            return "學校名稱：" + name + "。"
                    + "地址：" + address + "。"
                    + "電話：" + phone + "。"
                    + "地區：" + district + "。"
                    + "類型：" + type + "。"
                    + "學費：" + tuition + "。"
                    + "宗教：" + religion + "。";
        }
        return "School name: " + name + ". "
                + "Address: " + address + ". "
                + "Phone: " + phone + ". "
                + "District: " + district + ". "
                + "Type: " + type + ". "
                + "Tuition: " + tuition + ". "
                + "Religion: " + religion + ".";
    }

    private String normalizeReligionForSpeech(String value, boolean isChinese) {
        String safe = value == null ? "" : value.trim();
        String lower = safe.toLowerCase(Locale.ROOT);
        if (safe.isEmpty()
                || "n/a".equals(lower)
                || "na".equals(lower)
                || "n.a.".equals(lower)
                || "-".equals(lower)) {
            return isChinese ? "不適用" : "Not applicable";
        }
        return safe;
    }

    private String formatPhoneForTts(String rawPhone) {
        String fallback = getString(R.string.speech_not_available);
        if (rawPhone == null) return fallback;
        String normalized = rawPhone.trim();
        if (normalized.isEmpty()) return fallback;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("n/a".equals(lower) || "na".equals(lower) || "-".equals(lower)
                || "無".equals(normalized) || "无".equals(normalized)) {
            return fallback;
        }

        String digits = normalized.replaceAll("\\D+", "");
        if (digits.isEmpty()) return fallback;
        StringBuilder sb = new StringBuilder(digits.length() * 2);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private String formatPhoneForEnglishSpeech(String rawPhone) {
        String fallback = getString(R.string.speech_not_available);
        if (rawPhone == null) return fallback;
        String normalized = rawPhone.trim();
        if (normalized.isEmpty()) return fallback;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("n/a".equals(lower) || "na".equals(lower) || "-".equals(lower)
                || "無".equals(normalized) || "无".equals(normalized)) {
            return fallback;
        }

        String digits = normalized.replaceAll("\\D+", "");
        if (digits.isEmpty()) return fallback;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(digitToWord(digits.charAt(i)));
        }
        return sb.toString();
    }

    private String digitToWord(char digit) {
        switch (digit) {
            case '0': return "zero";
            case '1': return "one";
            case '2': return "two";
            case '3': return "three";
            case '4': return "four";
            case '5': return "five";
            case '6': return "six";
            case '7': return "seven";
            case '8': return "eight";
            case '9': return "nine";
            default: return "";
        }
    }

    private String speechValue(String value) {
        if (value == null) return getString(R.string.speech_not_available);
        String v = value.trim();
        if (v.isEmpty()) return getString(R.string.speech_not_available);
        String lower = v.toLowerCase(Locale.ROOT);
        if ("n/a".equals(lower) || "na".equals(lower) || "-".equals(lower)
                || "無".equals(v) || "无".equals(v)) {
            return getString(R.string.speech_not_available);
        }
        return v;
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
        tvName.setText(SchoolDisplayUtils.displayName(this, school));
        tvAddress.setText(getString(R.string.label_address, SchoolDisplayUtils.displayAddress(this, school)));
        tvPhone.setText(getString(R.string.label_phone, school.getPhone()));
        tvDistrict.setText(getString(R.string.label_district, SchoolDisplayUtils.displayDistrict(this, school)));
        tvType.setText(getString(R.string.label_type, SchoolDisplayUtils.displayType(this, school)));
        tvTuition.setText(getString(R.string.label_tuition, school.getTuition()));
        tvReligion.setText(getString(R.string.label_religion, SchoolDisplayUtils.displayReligion(this, school)));
        bindSchoolWebsite();
        bindTransportInfo();

        updateFavoriteButton();
        loadReviews();
    }

    private void prepareTransportViewsForFirstBind() {
        // Avoid showing placeholder N/A rows before real transport data arrives.
        if (layoutTransportNormal != null) {
            layoutTransportNormal.setVisibility(View.GONE);
        }
        if (layoutTransportEmpty != null) {
            layoutTransportEmpty.setVisibility(View.GONE);
        }
    }

    private void bindTransportInfo() {
        if (school == null || school.getId() == null) {
            return;
        }
        prepareTransportViewsForFirstBind();
        boolean preferZh = LocaleUtils.prefersChineseSchoolData(this);
        transportRepository.getSchoolTransport(school.getId(), preferZh, new ApiCallback<TransportInfo>() {
            @Override
            public void onSuccess(TransportInfo data) {
                if (data == null) {
                    showTransportEmptyState();
                    return;
                }
                bindTransportInfoToViews(data);
            }

            @Override
            public void onError(String message) {
                showTransportEmptyState();
            }
        });
    }

    private void bindTransportInfoToViews(TransportInfo info) {
        if (info == null) {
            return;
        }
        if (isTransportDataUnavailable(info)) {
            showTransportEmptyState();
            return;
        }
        showTransportNormalState();
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

    /**
     * True when MTR/Bus/Minibus are all unavailable (null/blank/N/A/NA/無/无/-).
     */
    private boolean isTransportDataUnavailable(TransportInfo info) {
        return !hasAnyTransportData(info);
    }

    private boolean hasAnyTransportData(TransportInfo info) {
        if (info == null) {
            return false;
        }
        return hasTransportEntry(info.getMtrStation(), info.getMtrDistance())
                || hasTransportEntry(info.getBusStation(), info.getBusDistance())
                || hasTransportEntry(info.getMinibusStation(), info.getMinibusDistance());
    }

    private boolean hasTransportEntry(String place, String distance) {
        return !isUnavailableToken(place) || !isUnavailableToken(distance);
    }

    private boolean isUnavailableToken(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        return "n/a".equals(lower)
                || "na".equals(lower)
                || "-".equals(lower)
                || "無".equals(normalized)
                || "无".equals(normalized);
    }

    private void showTransportNormalState() {
        if (layoutTransportNormal != null) {
            layoutTransportNormal.setVisibility(View.VISIBLE);
        }
        if (layoutTransportEmpty != null) {
            layoutTransportEmpty.setVisibility(View.GONE);
        }
    }

    private void showTransportEmptyState() {
        if (layoutTransportNormal != null) {
            layoutTransportNormal.setVisibility(View.GONE);
        }
        if (layoutTransportEmpty != null) {
            layoutTransportEmpty.setVisibility(View.VISIBLE);
        }
        if (tvTransportEmptyPrimary != null) {
            tvTransportEmptyPrimary.setText(R.string.transport_no_data_main);
        }
        if (tvTransportEmptySecondary != null) {
            tvTransportEmptySecondary.setText(R.string.transport_no_data_sub);
        }
    }

    private void updateFavoriteButton() {
        if (school == null) return;
        boolean isFavorite = favoritesManager.isFavorite(school.getId());
        if (tvFavoriteLabel != null) {
            tvFavoriteLabel.setText(isFavorite ? R.string.saved_short : R.string.favorite_short);
            setUnderlined(tvFavoriteLabel);
            tvFavoriteLabel.setTextColor(getColor(isFavorite ? R.color.feedback_star_selected : R.color.ssf_primary_variant));
        }
        if (ivFavoriteIcon != null) {
            ivFavoriteIcon.setImageResource(isFavorite ? R.drawable.ic_star_filled_24 : R.drawable.ic_star_outline_24);
            ivFavoriteIcon.setImageTintList(ColorStateList.valueOf(getColor(isFavorite ? R.color.feedback_star_selected : R.color.ssf_text_hint)));
            ivFavoriteIcon.setScaleX(1f);
            ivFavoriteIcon.setScaleY(1f);
            ivFavoriteIcon.animate().cancel();
        }
    }

    private void animateFavoriteIcon(boolean selected) {
        if (ivFavoriteIcon == null) return;
        int targetColor = getColor(selected ? R.color.feedback_star_selected : R.color.ssf_text_hint);
        ivFavoriteIcon.animate().cancel();
        ivFavoriteIcon.setScaleX(1f);
        ivFavoriteIcon.setScaleY(1f);
        ivFavoriteIcon.animate()
                .scaleX(FAVORITE_ICON_SCALE)
                .scaleY(FAVORITE_ICON_SCALE)
                .setDuration(ICON_BOUNCE_DURATION_MS)
                .withEndAction(() -> {
                    ivFavoriteIcon.setImageResource(selected ? R.drawable.ic_star_filled_24 : R.drawable.ic_star_outline_24);
                    ivFavoriteIcon.setImageTintList(ColorStateList.valueOf(targetColor));
                    ivFavoriteIcon.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(ICON_BOUNCE_DURATION_MS)
                            .start();
                })
                .start();
    }

    private void setUnderlined(TextView tv) {
        if (tv == null) return;
        tv.setPaintFlags(tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    private void bindSchoolWebsite() {
        if (tvWebsite == null || school == null) return;
        String raw = school.getWebsite() == null ? "" : school.getWebsite().trim();
        if (raw.isEmpty() || "N/A".equalsIgnoreCase(raw) || "NA".equalsIgnoreCase(raw) || "-".equals(raw)) {
            tvWebsite.setVisibility(View.GONE);
            tvWebsite.setOnClickListener(null);
            return;
        }
        String url = normalizeWebsiteUrl(raw);
        tvWebsite.setText(getString(R.string.label_website, raw));
        tvWebsite.setVisibility(View.VISIBLE);
        setUnderlined(tvWebsite);
        tvWebsite.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(DetailActivity.this, R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String normalizeWebsiteUrl(String website) {
        String v = website == null ? "" : website.trim();
        if (v.isEmpty()) return "";
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return v;
        }
        return "https://" + v;
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
                scrollToPendingCommentIfNeeded();
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

    private void scrollToPendingCommentIfNeeded() {
        if (pendingFocusCommentId == null || pendingFocusCommentId.trim().isEmpty()) return;
        if (reviews.isEmpty() || listReviews == null) return;
        String targetId = pendingFocusCommentId.trim();

        String topLevelTargetId = targetId;
        for (Review r : reviews) {
            if (r == null) continue;
            String id = r.getId();
            if (id == null || !id.equals(targetId)) continue;
            if (r.getParentId() != null && !r.getParentId().trim().isEmpty()) {
                topLevelTargetId = r.getParentId().trim();
            }
            break;
        }

        int adapterPos = 0;
        int foundPos = -1;
        for (Review r : reviews) {
            if (r == null) continue;
            boolean isTop = r.getParentId() == null || r.getParentId().trim().isEmpty();
            if (!isTop) continue;
            String id = r.getId();
            if (id != null && id.equals(topLevelTargetId)) {
                foundPos = adapterPos;
                break;
            }
            adapterPos++;
        }
        if (foundPos >= 0) {
            final int targetPos = foundPos;
            listReviews.post(() -> listReviews.smoothScrollToPosition(targetPos));
        }
        pendingFocusCommentId = null;
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
                .setTitle(R.string.nickname_dialog_title)
                .setView(content)
                .create();
        dialog.show();
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(this, R.string.nickname_empty_error, Toast.LENGTH_SHORT).show();
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

    private void submitReply(Review parentReview, String replyContent) {
        if (school == null || parentReview == null || parentReview.getId() == null) return;
        String reply = replyContent == null ? "" : replyContent.trim();
        if (reply.isEmpty()) return;
        String nickname = getNickname();
        if (nickname.isEmpty()) {
            nickname = getString(R.string.guest_user);
        }
        reviewRepository.addReview(
                school.getId(),
                deviceUserId,
                nickname,
                0,
                reply,
                parentReview.getId(),
                new ApiCallback<Review>() {
                    @Override
                    public void onSuccess(Review data) {
                        if (data != null) {
                            if (data.getParentId() == null || data.getParentId().trim().isEmpty()) {
                                data.setParentId(parentReview.getId());
                            }
                            if (data.getTimestamp() <= 0) {
                                data.setTimestamp(System.currentTimeMillis());
                            }
                            reviews.add(data);
                            reviewAdapter.notifyDataSetChanged();
                            Log.d(COMMENT_REPLY_DEBUG, "parentId=" + parentReview.getId() + ", commentId=" + data.getId());
                        }
                        loadReviews();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(DetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void shareReview(Review review) {
        if (review == null) return;
        String schoolName = school == null ? "" : SchoolDisplayUtils.displayName(this, school);
        int rating = review.getRating();
        String comment = review.getComment() == null ? "" : review.getComment().trim();
        boolean zh = LocaleUtils.prefersChineseSchoolData(this);

        String shareText;
        if (zh) {
            shareText = "學校：" + (schoolName.isEmpty() ? "N/A" : schoolName) + "\n"
                    + "評分：" + rating + "/5\n"
                    + "評論：" + (comment.isEmpty() ? "N/A" : comment) + "\n"
                    + "來自 School Explorer";
        } else {
            shareText = "School: " + (schoolName.isEmpty() ? "N/A" : schoolName) + "\n"
                    + "Rating: " + rating + "/5\n"
                    + "Comment: " + (comment.isEmpty() ? "N/A" : comment) + "\n"
                    + "Sent from School Explorer";
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.setPackage("com.whatsapp");

        try {
            startActivity(shareIntent);
        } catch (Exception ignored) {
            Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("text/plain");
            fallback.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(fallback, getString(R.string.share)));
        }
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
        if (tvAvgDesc != null) tvAvgDesc.setText(R.string.rating_desc_average);
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
        if (avg5 >= 4.5) return getString(R.string.rating_desc_excellent);
        if (avg5 >= 4.0) return getString(R.string.rating_desc_very_good);
        if (avg5 >= 3.5) return getString(R.string.rating_desc_good);
        if (avg5 >= 3.0) return getString(R.string.rating_desc_average);
        return getString(R.string.rating_desc_poor);
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

    @Override
    protected void onDestroy() {
        stopSpeaking();
        stopSpeakPulse();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }
}
