package com.example.smartschoolfinder.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.SchoolAdapter;
import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.data.ReviewRepository;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.FilterUtils;
import com.example.smartschoolfinder.utils.LocaleUtils;
import com.example.smartschoolfinder.utils.LocationHelper;
import com.example.smartschoolfinder.utils.PinyinUtils;
import com.example.smartschoolfinder.utils.SchoolDisplayUtils;
import com.example.smartschoolfinder.widget.SideBar;

import java.io.File;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String COUNT_DEBUG_TAG = "COUNT_DEBUG";
    private static final String DEDUP_DEBUG_TAG = "DEDUP_DEBUG";
    private static final String NAME_DEBUG_TAG = "NAME_DEBUG";
    private static final String SORT_DEBUG_TAG = "SORT_DEBUG";

    private static final String LOCALE_TAG_ENGLISH = "en";
    private static final String LOCALE_TAG_TRADITIONAL_CHINESE = "zh-Hant";
    private static final int LOCATION_MODE_CURRENT = 0;
    private static final int LOCATION_MODE_CUSTOM = 1;
    private static final int LOCATION_MODE_OFF = 2;

    private View loadingView;
    private View errorView;
    private View emptyView;
    private RecyclerView recyclerView;
    private SideBar sideBar;
    private TextView tvLetterHint;
    private TextView tvFilterResultHint;
    private FloatingActionButton fabBackToTop;
    private boolean isBackToTopVisible = false;

    private View layoutFilterContent;
    private ImageView ivFilterToggle;
    private boolean isFilterExpanded = true;

    private EditText etSearch;
    private Spinner spinnerDistrict;
    private Spinner spinnerType;
    private ArrayAdapter<FilterOption> districtAdapter;
    private ArrayAdapter<FilterOption> typeAdapter;
    private Button btnSortBy;
    private TextView tvSortByLabel;
    private Button btnSortDistance;
    private Button btnNearestFive;

    private DrawerLayout drawerLayout;
    private SharedPreferences prefs;
    private SwitchCompat switchDrawerLocation;
    private SwitchCompat switchDrawerDarkMode;
    private boolean syncingDrawerSwitches;

    private SchoolAdapter adapter;
    /** 完整原始数据（全量数据源）；距离排序与「最近 5 所」均基于此列表再筛选，从不使用当前 Adapter 子集作为数据源 */
    private final List<School> rawSchoolList = new ArrayList<>();
    /** 当前 RecyclerView 展示的数据 */
    private final List<School> filteredSchoolList = new ArrayList<>();
    private boolean hasInitializedDefaultFilter = false;

    /** 用户参考点纬度（无权限或定位失败时为香港默认） */
    private double userLatitude = LocationHelper.HK_DEFAULT_LATITUDE;
    /** 用户参考点经度 */
    private double userLongitude = LocationHelper.HK_DEFAULT_LONGITUDE;

    private boolean sortByDistance = false;
    /** 仅显示当前筛选条件下距离最近 5 所（仍尊重搜索与地区、类型） */
    private boolean nearestFiveOnly = false;
    private boolean hasShownLocationFallbackNotice = false;
    private boolean hasShownCustomLocationInvalidNotice = false;
    private String selectedDistrictFilter = "All";
    private String selectedTypeFilter = "All";
    private String selectedGenderFilter = "All";
    private String selectedDistanceFilter = "default";
    private int fetchSchoolsCallCount = 0;
    private Map<String, Integer> latestTypeCounts = new LinkedHashMap<>();
    private int latestHkCount = 0;
    private int latestKwCount = 0;
    private int latestNtCount = 0;
    private int latestUnknownDistrictCount = 0;
    private int latestGenderBoysCount = 0;
    private int latestGenderGirlsCount = 0;
    private int latestGenderCoedCount = 0;
    private int latestGenderUnknownCount = 0;
    private int backToTopThresholdPx = 500;

    private static final class FilterOption {
        final String value; // actual filter value used in logic
        final String label; // displayed label, e.g. "Kowloon (88)"
        FilterOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
        @Override public String toString() {
            return label;
        }
    }

    private static final String KEY_REVIEWS_SEEDED = "reviews_seeded_v1";

    private static final Comparator<School> DISTANCE_COMPARATOR = new Comparator<School>() {
        @Override
        public int compare(School a, School b) {
            boolean va = a.hasValidDistance();
            boolean vb = b.hasValidDistance();
            if (va && !vb) {
                return -1;
            }
            if (!va && vb) {
                return 1;
            }
            if (!va) {
                return 0;
            }
            return Double.compare(a.getDistance(), b.getDistance());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);

        loadingView = findViewById(R.id.loadingView);
        errorView = findViewById(R.id.errorView);
        emptyView = findViewById(R.id.emptyView);
        recyclerView = findViewById(R.id.recyclerSchools);
        sideBar = findViewById(R.id.sideBar);
        tvLetterHint = findViewById(R.id.tvLetterHint);
        tvFilterResultHint = findViewById(R.id.tvFilterResultHint);
        fabBackToTop = findViewById(R.id.fabBackToTop);

        layoutFilterContent = findViewById(R.id.layoutFilterContent);
        ivFilterToggle = findViewById(R.id.ivFilterToggle);
        if (ivFilterToggle != null) {
            ivFilterToggle.setOnClickListener(v -> toggleFilterPanel());
        }

        etSearch = findViewById(R.id.etSearch);
        btnSortBy = findViewById(R.id.btnSortBy);

        View btnSortByQuick = findViewById(R.id.btnSortByQuick);
        Button btnRetry = findViewById(R.id.btnRetry);
        View btnFavorites = findViewById(R.id.btnFavorites);
        View btnCompare = findViewById(R.id.btnCompare);
        tvSortByLabel = findViewById(R.id.tvSortByLabel);
        setUnderlined(findViewById(R.id.tvFavoritesLabel));
        setUnderlined(findViewById(R.id.tvCompareLabel));
        setUnderlined(tvSortByLabel);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        adapter = new SchoolAdapter(school -> {
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("school_id", school.getId());
            startActivity(intent);
            overridePendingTransition(R.anim.ssf_slide_in_right, R.anim.ssf_fade_out);
        });
        recyclerView.setAdapter(adapter);
        setupBackToTopButton();
        setupSideBar();

        updateSortButtonLabel();
        applyPressFeedback(btnRetry);
        applyQuickIconFeedback(btnSortByQuick, btnFavorites, btnCompare);
        applyPressFeedback(btnSortBy);

        ensureFirstLocationChoice();
        refreshUserReferenceLocation();

        setupDrawer();

        btnSortBy.setOnClickListener(v -> applyFilter(true));
        btnRetry.setOnClickListener(v -> loadSchools());
        btnFavorites.setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));
        btnCompare.setOnClickListener(v -> startActivity(new Intent(this, CompareActivity.class)));

        if (btnSortByQuick != null) {
            btnSortByQuick.setOnClickListener(v -> showSortByPanel());
        }

        loadSchools();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    private void setupDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout);
        ImageButton btnOpenDrawer = findViewById(R.id.btnOpenDrawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        TextView tvVersion = findViewById(R.id.tvDrawerVersion);
        if (tvVersion != null) {
            String versionName = "";
            long versionCode = 0L;
            try {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                versionName = packageInfo.versionName;
                versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? packageInfo.getLongVersionCode()
                        : packageInfo.versionCode;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            tvVersion.setText(getString(R.string.drawer_version_line, versionName, versionCode));
        }

        switchDrawerLocation = findViewById(R.id.switchDrawerLocation);
        switchDrawerDarkMode = findViewById(R.id.switchDrawerDarkMode);

        View drawerNavHome = findViewById(R.id.drawerNavHome);
        if (drawerNavHome != null) {
            drawerNavHome.setOnClickListener(v -> closeDrawer());
        }

        if (findViewById(R.id.drawerNavFavorites) != null) {
            findViewById(R.id.drawerNavFavorites).setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(this, FavoritesActivity.class));
            });
        }
        if (findViewById(R.id.drawerNavCompare) != null) {
            findViewById(R.id.drawerNavCompare).setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(this, CompareActivity.class));
            });
        }
        if (findViewById(R.id.drawerNavAbout) != null) {
            findViewById(R.id.drawerNavAbout).setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(this, AboutActivity.class));
            });
        }

        if (switchDrawerLocation != null) {
            switchDrawerLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (syncingDrawerSwitches) return;
                applyLocationMode(isChecked ? LOCATION_MODE_CURRENT : LOCATION_MODE_OFF, true);
            });
        }

        if (findViewById(R.id.drawerSetLocation) != null) {
            findViewById(R.id.drawerSetLocation).setOnClickListener(v -> {
                closeDrawer();
                promptCustomLocationThenApply();
            });
        }
        if (findViewById(R.id.drawerSortBy) != null) {
            findViewById(R.id.drawerSortBy).setOnClickListener(v -> {
                closeDrawer();
                showSortByPicker();
            });
        }

        if (switchDrawerDarkMode != null) {
            switchDrawerDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (syncingDrawerSwitches) return;
                int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                prefs.edit().putInt(AppConstants.KEY_THEME_MODE, mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
            });
        }
        if (findViewById(R.id.drawerLanguage) != null) {
            findViewById(R.id.drawerLanguage).setOnClickListener(v -> {
                closeDrawer();
                showLanguagePicker();
            });
        }

        if (findViewById(R.id.drawerFaq) != null) {
            findViewById(R.id.drawerFaq).setOnClickListener(v -> showFaqDialog());
        }
        View drawerFeedback = findViewById(R.id.drawerFeedback);
        if (drawerFeedback != null) {
            drawerFeedback.setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(this, FeedbackActivity.class));
            });
        }
        if (findViewById(R.id.drawerPrivacy) != null) {
            findViewById(R.id.drawerPrivacy).setOnClickListener(v -> openPrivacyPolicy());
        }

        syncDrawerUiFromPrefs();
    }

    private void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private int getLocationMode() {
        if (prefs.contains(AppConstants.KEY_LOCATION_MODE)) {
            return prefs.getInt(AppConstants.KEY_LOCATION_MODE, LOCATION_MODE_CURRENT);
        }
        // Backward compatibility from old switch-based pref.
        boolean useOld = prefs.getBoolean(AppConstants.KEY_USE_LOCATION, true);
        return useOld ? LOCATION_MODE_CURRENT : LOCATION_MODE_OFF;
    }

    private void saveLocationMode(int mode) {
        prefs.edit()
                .putInt(AppConstants.KEY_LOCATION_MODE, mode)
                .putBoolean(AppConstants.KEY_USE_LOCATION, mode != LOCATION_MODE_OFF)
                .putBoolean(AppConstants.KEY_LOCATION_INITIALIZED, true)
                .apply();
    }

    private String getCustomLocationName() {
        return prefs.getString(AppConstants.KEY_CUSTOM_LOCATION_NAME, "");
    }

    private boolean hasUsableCustomLocation() {
        if (!prefs.contains(AppConstants.KEY_CUSTOM_LOCATION_LAT)
                || !prefs.contains(AppConstants.KEY_CUSTOM_LOCATION_LON)) {
            return false;
        }
        float lat = prefs.getFloat(AppConstants.KEY_CUSTOM_LOCATION_LAT, Float.NaN);
        float lon = prefs.getFloat(AppConstants.KEY_CUSTOM_LOCATION_LON, Float.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return false;
        }
        // Treat (0,0) as invalid custom location.
        return !(Math.abs(lat) < 1e-6f && Math.abs(lon) < 1e-6f);
    }

    private boolean isDistanceModeActive() {
        int mode = getLocationMode();
        if (mode == LOCATION_MODE_CURRENT) {
            return true;
        }
        if (mode == LOCATION_MODE_CUSTOM) {
            return hasUsableCustomLocation();
        }
        return false;
    }

    private boolean canUseDistanceFeatures() {
        if (!isDistanceModeActive()) {
            Toast.makeText(this, R.string.location_mode_requires_distance, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String locationModeDisplayName() {
        int mode = getLocationMode();
        if (mode == LOCATION_MODE_CURRENT) return getString(R.string.location_mode_current);
        if (mode == LOCATION_MODE_CUSTOM) {
            String custom = getCustomLocationName();
            if (custom != null && !custom.trim().isEmpty()) {
                return custom;
            }
            return getString(R.string.location_mode_custom);
        }
        return getString(R.string.location_mode_off);
    }

    private void showLocationModePicker() {
        CharSequence[] labels = new CharSequence[]{
                getString(R.string.location_mode_current),
                getString(R.string.location_mode_custom),
                getString(R.string.location_mode_off)
        };
        int checked = getLocationMode();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_mode_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == LOCATION_MODE_CUSTOM) {
                        promptCustomLocationThenApply();
                    } else {
                        applyLocationMode(which, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void ensureFirstLocationChoice() {
        if (prefs.getBoolean(AppConstants.KEY_LOCATION_INITIALIZED, false)) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_first_use_title)
                .setMessage(R.string.location_first_use_message)
                .setPositiveButton(R.string.location_mode_current, (dialog, which) -> applyLocationMode(LOCATION_MODE_CURRENT, true))
                .setNegativeButton(R.string.location_mode_off, (dialog, which) -> applyLocationMode(LOCATION_MODE_OFF, false))
                .setNeutralButton(R.string.location_mode_custom, (dialog, which) -> promptCustomLocationThenApply())
                .setCancelable(false)
                .show();
    }

    private void applyLocationMode(int mode, boolean fromUserAction) {
        saveLocationMode(mode);
        hasShownLocationFallbackNotice = false;
        hasShownCustomLocationInvalidNotice = false;
        if (mode == LOCATION_MODE_CURRENT && fromUserAction && !LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this);
        }
        refreshSchoolDistancesForCurrentLocation();
        nearestFiveOnly = false;
        if (!isDistanceModeActive()) {
            sortByDistance = false;
            updateSortButtonLabel();
        }
        applyFilter(false);
        syncDrawerUiFromPrefs();
        updateSideBarVisibility();
    }

    private void promptCustomLocationThenApply() {
        final EditText input = new EditText(this);
        input.setHint(R.string.set_location_hint);
        String existing = getCustomLocationName();
        if (existing != null && !existing.trim().isEmpty()) {
            input.setText(existing);
            input.setSelection(existing.length());
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.set_location_title)
                .setView(input)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            styleLocationDialogActionButton(positive);
            styleLocationDialogActionButton(negative);
            if (positive == null) return;
            positive.setOnClickListener(v -> {
                String query = input.getText() == null ? "" : input.getText().toString().trim();
                if (query.isEmpty()) {
                    Toast.makeText(this, R.string.enter_location_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                positive.setEnabled(false);
                resolveAndSaveCustomLocation(query, () -> {
                    Toast.makeText(this, getString(R.string.location_custom_saved, query), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    applyLocationMode(LOCATION_MODE_CUSTOM, false);
                }, () -> {
                    positive.setEnabled(true);
                    Toast.makeText(this, R.string.location_not_found, Toast.LENGTH_SHORT).show();
                });
            });
        });
        dialog.show();
    }

    private void styleLocationDialogActionButton(Button button) {
        if (button == null) return;
        // Keep this dialog's actions as flat text buttons only.
        button.setBackground(null);
        button.setAllCaps(false);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
            button.setTextColor(typedValue.data);
        }
    }

    private void resolveAndSaveCustomLocation(String query, Runnable onSuccess, Runnable onFailed) {
        new Thread(() -> {
            try {
                if (!Geocoder.isPresent()) {
                    runOnUiThread(onFailed);
                    return;
                }
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(query, 1);
                if (results == null || results.isEmpty()) {
                    runOnUiThread(onFailed);
                    return;
                }
                Address first = results.get(0);
                final double lat = first.getLatitude();
                final double lon = first.getLongitude();
                prefs.edit()
                        .putString(AppConstants.KEY_CUSTOM_LOCATION_NAME, query)
                        .putFloat(AppConstants.KEY_CUSTOM_LOCATION_LAT, (float) lat)
                        .putFloat(AppConstants.KEY_CUSTOM_LOCATION_LON, (float) lon)
                        .apply();
                runOnUiThread(onSuccess);
            } catch (Exception e) {
                runOnUiThread(onFailed);
            }
        }).start();
    }

    private void showSortByPicker() {
        CharSequence[] labels = new CharSequence[]{
                getString(R.string.sort_option_initial),
                getString(R.string.sort_option_location),
                getString(R.string.sort_option_distance),
                getString(R.string.sort_option_gender)
        };
        int checked = drawerSortPickerCheckedItem();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_sort_by)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) { // School initial (default ordering)
                        nearestFiveOnly = false;
                        sortByDistance = false;
                        selectedDistanceFilter = "default";
                        updateSortButtonLabel();
                        bindSchoolListToUi();
                        updateSideBarVisibility();
                        return;
                    }
                    if (which == 1 || which == 3) { // Location / Gender
                        showSortByPanel();
                        return;
                    }
                    // Distance
                    if (!canUseDistanceFeatures()) {
                        return;
                    }
                    showSortByPanel();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int drawerSortPickerCheckedItem() {
        if (selectedGenderFilter != null && !"All".equalsIgnoreCase(selectedGenderFilter)) {
            return 3;
        }
        if (selectedDistrictFilter != null && !"All".equalsIgnoreCase(selectedDistrictFilter)) {
            return 1;
        }
        if (sortByDistance || (selectedDistanceFilter != null && !"default".equalsIgnoreCase(selectedDistanceFilter))) {
            return 2;
        }
        return 0;
    }

    /**
     * Index for single-choice dialog: 0 = English, 1 = Traditional Chinese.
     * Uses saved pref when set; otherwise infers from AppCompat / system locale
     * (aligned with {@link LocaleUtils#prefersChineseSchoolData}).
     */
    private int languagePickerCheckedItem() {
        String saved = prefs.getString(AppConstants.KEY_APP_LANGUAGE, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.trim().toLowerCase(Locale.ROOT).startsWith("zh") ? 1 : 0;
        }
        return LocaleUtils.prefersChineseSchoolData(this) ? 1 : 0;
    }

    private void showLanguagePicker() {
        CharSequence[] labels = new CharSequence[]{
                getString(R.string.language_option_english),
                getString(R.string.language_option_zh_hant)
        };
        int checked = languagePickerCheckedItem();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.language_picker_title)
                .setSingleChoiceItems(labels, checked, (pickerDialog, which) -> {
                    String tag = which == 0 ? LOCALE_TAG_ENGLISH : LOCALE_TAG_TRADITIONAL_CHINESE;
                    prefs.edit().putString(AppConstants.KEY_APP_LANGUAGE, tag).apply();
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
                    pickerDialog.dismiss();
                    recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showThemePicker() {
        final int[] modes = new int[]{
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        };
        CharSequence[] labels = new CharSequence[]{
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system)
        };
        int current = prefs.getInt(AppConstants.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int checked = 2;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_theme_pick)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    prefs.edit().putInt(AppConstants.KEY_THEME_MODE, modes[which]).apply();
                    AppCompatDelegate.setDefaultNightMode(modes[which]);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showFaqDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_faq)
                .setMessage(R.string.faq_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showFeedbackDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_feedback, null);
        RatingBar ratingBar = content.findViewById(R.id.ratingFeedback);
        EditText commentInput = content.findViewById(R.id.etFeedbackComment);
        if (ratingBar != null) {
            ratingBar.setIsIndicator(false);
            ratingBar.setStepSize(1f);
            ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
                if (fromUser) {
                    bar.setRating(Math.round(rating));
                }
            });
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_feedback)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.feedback_send, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            styleLocationDialogActionButton(positive);
            styleLocationDialogActionButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            if (positive == null) return;
            positive.setOnClickListener(v -> {
                int rating = ratingBar == null ? 0 : Math.round(ratingBar.getRating());
                String comment = commentInput == null || commentInput.getText() == null
                        ? ""
                        : commentInput.getText().toString().trim();
                if (rating <= 0 && comment.isEmpty()) {
                    Toast.makeText(this, R.string.feedback_empty_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, R.string.feedback_sent, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void openPrivacyPolicy() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_privacy)
                .setMessage(R.string.privacy_policy_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void clearCacheDir() {
        deleteRecursive(getCacheDir());
        try {
            deleteRecursive(getCodeCacheDir());
        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) {
                    deleteRecursive(c);
                }
                //noinspection ResultOfMethodCallIgnored
                c.delete();
            }
        }
    }

    private void syncDrawerUiFromPrefs() {
        syncingDrawerSwitches = true;
        if (switchDrawerLocation != null) {
            switchDrawerLocation.setChecked(getLocationMode() != LOCATION_MODE_OFF);
        }
        if (adapter != null) {
            adapter.setShowDistance(isDistanceModeActive());
        }
        if (switchDrawerDarkMode != null) {
            int mode = prefs.getInt(AppConstants.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            boolean dark = mode == AppCompatDelegate.MODE_NIGHT_YES
                    || (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    && (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            switchDrawerDarkMode.setChecked(dark);
        }
        syncingDrawerSwitches = false;
    }

    private void toggleFilterPanel() {
        if (layoutFilterContent == null || ivFilterToggle == null) {
            return;
        }
        if (isFilterExpanded) {
            collapseFilterPanel();
        } else {
            expandFilterPanel();
        }
        isFilterExpanded = !isFilterExpanded;
        ivFilterToggle.setImageResource(isFilterExpanded ? R.drawable.ic_arrow_up_16 : R.drawable.ic_arrow_down_16);
    }

    private void collapseFilterPanel() {
        final View target = layoutFilterContent;
        final int initialHeight = target.getHeight();
        if (initialHeight <= 0) {
            target.setVisibility(View.GONE);
            target.setAlpha(1f);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
        animator.setDuration(240);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            int h = (int) a.getAnimatedValue();
            ViewGroup.LayoutParams lp = target.getLayoutParams();
            lp.height = h;
            target.setLayoutParams(lp);
            float p = initialHeight == 0 ? 0f : (h * 1f / initialHeight);
            target.setAlpha(p);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                target.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = target.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                target.setLayoutParams(lp);
                target.setAlpha(1f);
            }
        });
        animator.start();
    }

    private void expandFilterPanel() {
        final View target = layoutFilterContent;
        target.setVisibility(View.VISIBLE);
        target.setAlpha(0f);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(((View) target.getParent()).getWidth(), View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        target.measure(widthSpec, heightSpec);
        final int targetHeight = target.getMeasuredHeight();

        ViewGroup.LayoutParams lp = target.getLayoutParams();
        lp.height = 0;
        target.setLayoutParams(lp);

        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            int h = (int) a.getAnimatedValue();
            ViewGroup.LayoutParams lpp = target.getLayoutParams();
            lpp.height = h;
            target.setLayoutParams(lpp);
            float p = targetHeight == 0 ? 1f : (h * 1f / targetHeight);
            target.setAlpha(p);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams lpp = target.getLayoutParams();
                lpp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                target.setLayoutParams(lpp);
                target.setAlpha(1f);
            }
        });
        animator.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LocationHelper.REQUEST_CODE_LOCATION) {
            if (getLocationMode() == LOCATION_MODE_CURRENT && !LocationHelper.hasLocationPermission(this)) {
                Toast.makeText(this, R.string.location_permission_needed, Toast.LENGTH_SHORT).show();
            }
            refreshUserReferenceLocation();
            recalculateAllSchoolDistances();
            applyFilter(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncDrawerUiFromPrefs();
        if (!rawSchoolList.isEmpty()) {
            // Ensure distance-dependent UI always reflects latest mode/location.
            refreshUserReferenceLocation();
            recalculateAllSchoolDistances();
            applyFilter(false);
        }
    }

    private void showSortByPanel() {
        View panel = getLayoutInflater().inflate(R.layout.dialog_sort_by_panel, null, false);
        Spinner locationSpinner = panel.findViewById(R.id.spinnerPanelLocation);
        Spinner levelSpinner = panel.findViewById(R.id.spinnerPanelLevel);
        Spinner distanceSpinner = panel.findViewById(R.id.spinnerPanelDistance);
        Spinner genderSpinner = panel.findViewById(R.id.spinnerPanelGender);

        List<FilterOption> locationOptions = new ArrayList<>();
        locationOptions.add(new FilterOption("All", getString(R.string.filter_option_all_count, rawSchoolList.size())));
        Log.d(COUNT_DEBUG_TAG, "sort by all count = " + rawSchoolList.size());
        locationOptions.add(new FilterOption("Hong Kong Island", getString(R.string.filter_option_hk_island_count, latestHkCount)));
        locationOptions.add(new FilterOption("Kowloon", getString(R.string.filter_option_kowloon_count, latestKwCount)));
        locationOptions.add(new FilterOption("New Territories", getString(R.string.filter_option_new_territories_count, latestNtCount)));

        List<FilterOption> levelOptions = new ArrayList<>();
        levelOptions.add(new FilterOption("All", getString(R.string.filter_option_all_count, rawSchoolList.size())));
        addPanelTypeOption(levelOptions, "kindergarten", "Kindergarten", R.string.filter_type_kindergarten);
        addPanelTypeOption(levelOptions, "primary", "Primary", R.string.filter_type_primary);
        addPanelTypeOption(levelOptions, "secondary", "Secondary", R.string.filter_type_secondary);
        addPanelTypeOption(levelOptions, "university", "University", R.string.filter_type_university);

        List<FilterOption> distanceOptions = new ArrayList<>();
        distanceOptions.add(new FilterOption("default", getString(R.string.distance_default)));
        distanceOptions.add(new FilterOption("nearest", getString(R.string.distance_nearest_first)));
        distanceOptions.add(new FilterOption("farthest", getString(R.string.distance_farthest_first)));

        List<FilterOption> genderOptions = new ArrayList<>();
        genderOptions.add(new FilterOption("All", getString(R.string.filter_option_all_count, rawSchoolList.size())));
        if (latestGenderBoysCount > 0) {
            genderOptions.add(new FilterOption("Boys", getString(R.string.filter_option_with_count, getString(R.string.gender_boys), latestGenderBoysCount)));
        }
        if (latestGenderGirlsCount > 0) {
            genderOptions.add(new FilterOption("Girls", getString(R.string.filter_option_with_count, getString(R.string.gender_girls), latestGenderGirlsCount)));
        }
        if (latestGenderCoedCount > 0) {
            genderOptions.add(new FilterOption("Co-ed", getString(R.string.filter_option_with_count, getString(R.string.gender_coed), latestGenderCoedCount)));
        }

        bindPanelSpinner(locationSpinner, locationOptions, selectedDistrictFilter);
        bindPanelSpinner(levelSpinner, levelOptions, selectedTypeFilter);
        bindPanelSpinner(distanceSpinner, distanceOptions, selectedDistanceFilter);
        bindPanelSpinner(genderSpinner, genderOptions, selectedGenderFilter);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> applyFilter(false));
        dialog.show();

        locationSpinner.setOnItemSelectedListener(simpleSelectionListener(v -> selectedDistrictFilter = v));
        levelSpinner.setOnItemSelectedListener(simpleSelectionListener(v -> selectedTypeFilter = v));
        distanceSpinner.setOnItemSelectedListener(simpleSelectionListener(v -> {
            selectedDistanceFilter = v;
            applyDistanceMode(v);
        }));
        genderSpinner.setOnItemSelectedListener(simpleSelectionListener(v -> selectedGenderFilter = v));
    }

    private void setupSideBar() {
        if (sideBar == null) return;
        sideBar.setOnLetterChangedListener(new SideBar.OnLetterChangedListener() {
            @Override
            public void onLetterChanged(String letter) {
                if (tvLetterHint != null) {
                    tvLetterHint.setText(letter);
                    tvLetterHint.setVisibility(View.VISIBLE);
                }
                scrollToLetter(letter);
            }

            @Override
            public void onTouchReleased() {
                if (tvLetterHint != null) {
                    tvLetterHint.setVisibility(View.GONE);
                }
            }
        });
        updateSideBarVisibility();
    }

    private void scrollToLetter(String letter) {
        if (letter == null || recyclerView == null || filteredSchoolList.isEmpty()) return;
        int pos = -1;
        for (int i = 0; i < filteredSchoolList.size(); i++) {
            School s = filteredSchoolList.get(i);
            if (s != null && letter.equals(getDisplayFirstLetter(s))) {
                pos = i;
                break;
            }
        }
        if (pos >= 0 && recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(pos, 0);
        }
    }

    private void updateSideBarVisibility() {
        if (sideBar == null) return;
        boolean visible = !sortByDistance && !nearestFiveOnly;
        sideBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible && tvLetterHint != null) {
            tvLetterHint.setVisibility(View.GONE);
        }
    }

    private void updateSortButtonLabel() {
        if (tvSortByLabel != null) {
            tvSortByLabel.setText(R.string.drawer_sort_by);
        }
    }

    /**
     * 从系统读取最后已知位置；失败则保持/回退到香港默认坐标。
     */
    private void refreshUserReferenceLocation() {
        int mode = getLocationMode();
        if (mode == LOCATION_MODE_OFF) {
            userLatitude = Double.NaN;
            userLongitude = Double.NaN;
            return;
        }
        if (mode == LOCATION_MODE_CUSTOM) {
            if (hasUsableCustomLocation()) {
                userLatitude = prefs.getFloat(AppConstants.KEY_CUSTOM_LOCATION_LAT, 0f);
                userLongitude = prefs.getFloat(AppConstants.KEY_CUSTOM_LOCATION_LON, 0f);
                hasShownCustomLocationInvalidNotice = false;
            } else {
                userLatitude = Double.NaN;
                userLongitude = Double.NaN;
                if (!hasShownCustomLocationInvalidNotice) {
                    Toast.makeText(this, R.string.location_custom_invalid, Toast.LENGTH_SHORT).show();
                    hasShownCustomLocationInvalidNotice = true;
                }
            }
            return;
        }
        // CURRENT_LOCATION
        if (!LocationHelper.hasLocationPermission(this)) {
            userLatitude = LocationHelper.HK_DEFAULT_LATITUDE;
            userLongitude = LocationHelper.HK_DEFAULT_LONGITUDE;
            return;
        }
        Location loc = LocationHelper.getBestLastKnownLocation(this);
        if (LocationHelper.isValidLocation(loc)) {
            userLatitude = loc.getLatitude();
            userLongitude = loc.getLongitude();
            hasShownLocationFallbackNotice = false;
        } else {
            userLatitude = LocationHelper.HK_DEFAULT_LATITUDE;
            userLongitude = LocationHelper.HK_DEFAULT_LONGITUDE;
            if (!hasShownLocationFallbackNotice) {
                Toast.makeText(this, R.string.location_unavailable_fallback, Toast.LENGTH_LONG).show();
                hasShownLocationFallbackNotice = true;
            }
        }
    }

    /** 对原始列表中每所学校写入与用户参考点的距离 */
    private void recalculateAllSchoolDistances() {
        if (!isDistanceModeActive() || Double.isNaN(userLatitude) || Double.isNaN(userLongitude)) {
            for (School s : rawSchoolList) {
                s.clearDistance();
            }
            return;
        }
        // Never allow (0,0) into distance math; force HK fallback.
        if (Math.abs(userLatitude) < 1e-6 && Math.abs(userLongitude) < 1e-6) {
            userLatitude = LocationHelper.HK_DEFAULT_LATITUDE;
            userLongitude = LocationHelper.HK_DEFAULT_LONGITUDE;
            if (!hasShownLocationFallbackNotice) {
                Toast.makeText(this, R.string.location_unavailable_fallback, Toast.LENGTH_LONG).show();
                hasShownLocationFallbackNotice = true;
            }
        }
        for (School s : rawSchoolList) {
            s.updateDistanceFrom(userLatitude, userLongitude);
        }
    }

    /** 刷新参考点并写回全量列表中的距离，供距离按钮在排序前使用最新值 */
    private void refreshSchoolDistancesForCurrentLocation() {
        refreshUserReferenceLocation();
        recalculateAllSchoolDistances();
        requestFreshLocationIfPossible();
    }

    private void requestFreshLocationIfPossible() {
        if (getLocationMode() != LOCATION_MODE_CURRENT) {
            return;
        }
        if (!LocationHelper.hasLocationPermission(this)) {
            return;
        }
        LocationHelper.requestCurrentLocation(this, loc -> {
            if (!LocationHelper.isValidLocation(loc)) {
                return;
            }
            userLatitude = loc.getLatitude();
            userLongitude = loc.getLongitude();
            hasShownLocationFallbackNotice = false;
            recalculateAllSchoolDistances();
            runOnUiThread(() -> {
                if (nearestFiveOnly) {
                    bindSchoolListToUi();
                } else if (sortByDistance) {
                    bindFullDistanceSortedList();
                } else {
                    applyFilter(false);
                }
            });
        });
    }

    private void loadSchools() {
        fetchSchoolsCallCount++;
        boolean prefersChinese = LocaleUtils.prefersChineseSchoolData(this);
        String locale = prefersChinese ? "zh" : "en";
        Log.d(COUNT_DEBUG_TAG, "fetchSchools() called count=" + fetchSchoolsCallCount + ", locale=" + locale);
        showLoading();
        new SchoolApiService().getSchools(this, new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                rawSchoolList.clear();
                if (data != null) {
                    rawSchoolList.addAll(data);
                }
                Log.d(DEDUP_DEBUG_TAG, "home list size = " + rawSchoolList.size());
                Log.d(COUNT_DEBUG_TAG, "home displayed total = " + rawSchoolList.size());
                Log.d(COUNT_DEBUG_TAG, (prefersChinese ? "chinese master list = " : "english master list = ") + rawSchoolList.size());
                Log.d(COUNT_DEBUG_TAG, "locale = " + locale + ", rawSchoolList = " + rawSchoolList.size());
                Log.d(COUNT_DEBUG_TAG, "locale = " + locale + ", total = " + rawSchoolList.size());
                logNamePreview(locale);

                // Update filter counts/options based on REAL data (before showing list).
                updateFilterCountsAndRefreshSpinners();

                refreshUserReferenceLocation();
                recalculateAllSchoolDistances();

                seedReviewsIfNeeded();

                if (!hasInitializedDefaultFilter) {
                    etSearch.setText("");
                    selectedDistrictFilter = "All";
                    selectedTypeFilter = "All";
                    selectedGenderFilter = "All";
                    selectedDistanceFilter = "default";
                    hasInitializedDefaultFilter = true;
                }
                applyFilter(true);
            }

            @Override
            public void onError(String message) {
                showError();
            }
        });
    }

    private void seedReviewsIfNeeded() {
        if (rawSchoolList.isEmpty()) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_REVIEWS_SEEDED, false)) {
            return;
        }

        List<ReviewRepository.SeedSchool> schools = new ArrayList<>();
        for (School s : rawSchoolList) {
            if (s == null) continue;
            schools.add(new ReviewRepository.SeedSchool(s.getId(), s.getName()));
        }

        new ReviewRepository(this).seedReviews(schools, new ApiCallback<ReviewRepository.SeedResult>() {
            @Override
            public void onSuccess(ReviewRepository.SeedResult data) {
                prefs.edit().putBoolean(KEY_REVIEWS_SEEDED, true).apply();
            }

            @Override
            public void onError(String message) {
                // Do not block UI: seeding is best-effort; can retry next launch.
            }
        });
    }

    /**
     * 从 {@link #rawSchoolList} 与当前搜索/筛选条件得到列表（不截取条数）。
     */
    private List<School> buildFilteredSchoolsFromSource() {
        String keyword = etSearch.getText().toString();
        String district = getSelectedDistrictValue();
        String type = getSelectedTypeValue();
        String gender = selectedGenderFilter == null ? "All" : selectedGenderFilter;
        List<School> base = new ArrayList<>(FilterUtils.filter(rawSchoolList, keyword, district, type));
        List<School> result = new ArrayList<>();
        for (School s : base) {
            if (isGenderMatch(s, gender)) {
                result.add(s);
            }
        }
        return result;
    }

    private void publishSchoolsToUi(List<School> items) {
        filteredSchoolList.clear();
        filteredSchoolList.addAll(items);
        adapter.setData(filteredSchoolList);
        updateFilterResultHint(items == null ? 0 : items.size());
        recyclerView.setAlpha(0f);
        recyclerView.animate().alpha(1f).setDuration(180).start();

        if (filteredSchoolList.isEmpty()) {
            showEmpty();
        } else {
            showContent();
        }
    }

    private void setupBackToTopButton() {
        if (fabBackToTop != null) {
            fabBackToTop.setOnClickListener(v -> {
                if (recyclerView != null) {
                    recyclerView.smoothScrollToPosition(0);
                }
            });
        }
        if (recyclerView != null) {
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateBackToTopVisibility();
                }
            });
            float density = getResources().getDisplayMetrics().density;
            backToTopThresholdPx = (int) (300f * density);
            updateBackToTopVisibility();
        }
    }

    private void updateBackToTopVisibility() {
        if (recyclerView == null) return;
        boolean shouldShow = recyclerView.computeVerticalScrollOffset() > backToTopThresholdPx;
        if (shouldShow == isBackToTopVisible) return;
        isBackToTopVisible = shouldShow;
        if (fabBackToTop == null) return;
        fabBackToTop.animate().cancel();
        if (shouldShow) {
            fabBackToTop.setVisibility(View.VISIBLE);
            fabBackToTop.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
        } else {
            fabBackToTop.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(140)
                    .withEndAction(() -> fabBackToTop.setVisibility(View.GONE))
                    .start();
        }
    }

    private void updateFilterResultHint(int count) {
        if (tvFilterResultHint == null) return;
        if (!hasActiveFilterCriteria()) {
            tvFilterResultHint.setVisibility(View.GONE);
            return;
        }
        tvFilterResultHint.setText(getString(R.string.found_results, count));
        tvFilterResultHint.setVisibility(View.VISIBLE);
    }

    private boolean hasActiveFilterCriteria() {
        String keyword = etSearch == null || etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        boolean hasKeyword = !keyword.isEmpty();
        boolean districtActive = selectedDistrictFilter != null && !"All".equalsIgnoreCase(selectedDistrictFilter);
        boolean typeActive = selectedTypeFilter != null && !"All".equalsIgnoreCase(selectedTypeFilter);
        boolean genderActive = selectedGenderFilter != null && !"All".equalsIgnoreCase(selectedGenderFilter);
        boolean distanceActive = selectedDistanceFilter != null && !"default".equalsIgnoreCase(selectedDistanceFilter);
        return hasKeyword || districtActive || typeActive || genderActive || distanceActive;
    }

    /**
     * 按当前 {@link #nearestFiveOnly}、{@link #sortByDistance} 刷新列表（搜索、筛选、onResume 等共用）。
     */
    private void bindSchoolListToUi() {
        List<School> working = buildFilteredSchoolsFromSource();
        if (nearestFiveOnly) {
            Collections.sort(working, DISTANCE_COMPARATOR);
            int take = Math.min(5, working.size());
            working = new ArrayList<>(working.subList(0, take));
        } else if (sortByDistance) {
            Collections.sort(working, DISTANCE_COMPARATOR);
            if ("farthest".equals(selectedDistanceFilter)) {
                Collections.reverse(working);
            }
        } else {
            Collections.sort(working, defaultNameComparator());
            logSortPreview(LocaleUtils.prefersChineseSchoolData(this) ? "zh" : "en", working);
        }
        publishSchoolsToUi(working);
        updateSideBarVisibility();
    }

    private Comparator<School> defaultNameComparator() {
        return (a, b) -> {
            String la = a == null ? "#" : getDisplayFirstLetter(a);
            String lb = b == null ? "#" : getDisplayFirstLetter(b);
            if (!la.equals(lb)) {
                if ("#".equals(la)) return 1;
                if ("#".equals(lb)) return -1;
                return la.compareTo(lb);
            }
            String na = getDisplaySortName(a);
            String nb = getDisplaySortName(b);
            return na.compareToIgnoreCase(nb);
        };
    }

    private String getDisplaySortName(School school) {
        String value = SchoolDisplayUtils.displayName(this, school);
        return value == null ? "" : value.trim();
    }

    private String getDisplayFirstLetter(School school) {
        return PinyinUtils.firstLetter(getDisplaySortName(school));
    }

    private void logNamePreview(String locale) {
        int limit = Math.min(10, rawSchoolList.size());
        for (int i = 0; i < limit; i++) {
            School s = rawSchoolList.get(i);
            if (s == null) continue;
            String enName = s.getName() == null ? "" : s.getName().trim();
            String zhName = s.getChineseName() == null ? "" : s.getChineseName().trim();
            String display = SchoolDisplayUtils.displayName(this, s);
            Log.d(NAME_DEBUG_TAG, "locale=" + locale + ", school=" + enName
                    + ", chineseName=" + (zhName.isEmpty() ? "(empty)" : zhName)
                    + ", displayName=" + (display == null ? "" : display));
        }
    }

    private void logSortPreview(String locale, List<School> schools) {
        int limit = Math.min(10, schools == null ? 0 : schools.size());
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < limit; i++) {
            School s = schools.get(i);
            if (i > 0) builder.append(", ");
            builder.append(SchoolDisplayUtils.displayName(this, s));
        }
        builder.append("]");
        Log.d(SORT_DEBUG_TAG, "locale=" + locale + ", first 10 schools after sort = " + builder);
    }

    /**
     * 「取消距离排序 / Clear distance sort」：退出仅显示 5 所的限制，在当前筛选下按距离近→远展示<strong>全部</strong>学校。
     * 不读取 {@link #nearestFiveOnly}，避免任何 limit(5) 逻辑。
     */
    private void bindFullDistanceSortedList() {
        List<School> working = buildFilteredSchoolsFromSource();
        Collections.sort(working, DISTANCE_COMPARATOR);
        publishSchoolsToUi(working);
        updateSideBarVisibility();
    }

    /**
     * @param resetNearestFive 为 true 时退出「最近 5 所」模式（例如用户点击搜索，恢复完整筛选结果）
     */
    private void applyFilter(boolean resetNearestFive) {
        if (resetNearestFive) {
            nearestFiveOnly = false;
        }
        bindSchoolListToUi();
    }

    private String getSelectedDistrictValue() {
        return selectedDistrictFilter == null ? "All" : selectedDistrictFilter;
    }

    private String getSelectedTypeValue() {
        return selectedTypeFilter == null ? "All" : selectedTypeFilter;
    }

    private void updateFilterCountsAndRefreshSpinners() {
        int total = rawSchoolList.size();
        Log.d(COUNT_DEBUG_TAG, "displayed all count = " + total + " (from rawSchoolList)");
        int hk = 0, kw = 0, nt = 0, unknown = 0;
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        int boys = 0, girls = 0, coed = 0, genderUnknown = 0;

        for (School s : rawSchoolList) {
            if (s == null) continue;
            String dn = FilterUtils.normalizeDistrict(s.getDistrict());
            if ("hong kong island".equals(dn)) hk++;
            else if ("kowloon".equals(dn)) kw++;
            else if ("new territories".equals(dn)) nt++;
            else unknown++;

            String tn = FilterUtils.normalizeType(s.getType());
            if (tn == null || tn.trim().isEmpty() || "all".equals(tn)) continue;
            typeCounts.put(tn, (typeCounts.containsKey(tn) ? typeCounts.get(tn) : 0) + 1);

            String gn = normalizeGender(s.getGender());
            if ("boys".equals(gn)) boys++;
            else if ("girls".equals(gn)) girls++;
            else if ("coed".equals(gn)) coed++;
            else genderUnknown++;
        }

        updateSpinnerOptionsFromCounts(total, hk, kw, nt, unknown, typeCounts, boys, girls, coed, genderUnknown);
        Log.d(COUNT_DEBUG_TAG, "district counts hk=" + hk + ", kw=" + kw + ", nt=" + nt + ", unknown=" + unknown);
        Log.d(COUNT_DEBUG_TAG, "type option buckets = " + typeCounts + ", gender boys=" + boys + ", girls=" + girls + ", coed=" + coed + ", unknown=" + genderUnknown);
    }

    private void updateSpinnerOptionsFromCounts(int total, int hk, int kw, int nt, int unknown,
                                                Map<String, Integer> typeCountsNorm,
                                                int boys, int girls, int coed, int genderUnknown) {
        latestHkCount = hk;
        latestKwCount = kw;
        latestNtCount = nt;
        latestUnknownDistrictCount = unknown;
        latestTypeCounts = typeCountsNorm == null ? new LinkedHashMap<>() : new LinkedHashMap<>(typeCountsNorm);
        latestGenderBoysCount = boys;
        latestGenderGirlsCount = girls;
        latestGenderCoedCount = coed;
        latestGenderUnknownCount = genderUnknown;
    }

    private void addPanelTypeOption(List<FilterOption> options, String normKey, String filterValue, int displayLabelRes) {
        Integer count = latestTypeCounts.get(normKey);
        if (count == null || count <= 0) return;
        String label = getString(displayLabelRes);
        options.add(new FilterOption(filterValue, getString(R.string.filter_option_with_count, label, count)));
    }

    private void bindPanelSpinner(Spinner spinner, List<FilterOption> options, String selectedValue) {
        ArrayAdapter<FilterOption> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, options);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinner.setAdapter(adapter);
        if (selectedValue == null) return;
        for (int i = 0; i < options.size(); i++) {
            FilterOption option = options.get(i);
            if (option != null && selectedValue.equals(option.value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private AdapterView.OnItemSelectedListener simpleSelectionListener(ValueUpdater updater) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selected = parent.getItemAtPosition(position);
                String value = selected instanceof FilterOption ? ((FilterOption) selected).value : "All";
                updater.update(value);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }

    private void applyDistanceMode(String distanceValue) {
        if ("nearest".equals(distanceValue) || "farthest".equals(distanceValue)) {
            if (!canUseDistanceFeatures()) {
                selectedDistanceFilter = "default";
                sortByDistance = false;
                nearestFiveOnly = false;
                return;
            }
            refreshSchoolDistancesForCurrentLocation();
            sortByDistance = true;
            nearestFiveOnly = false;
            return;
        }
        sortByDistance = false;
        nearestFiveOnly = false;
    }

    private boolean isGenderMatch(School school, String selectedGender) {
        if (school == null) return false;
        if (selectedGender == null || "All".equalsIgnoreCase(selectedGender)) return true;
        String norm = normalizeGender(school.getGender());
        if ("Boys".equalsIgnoreCase(selectedGender)) return "boys".equals(norm);
        if ("Girls".equalsIgnoreCase(selectedGender)) return "girls".equals(norm);
        if ("Co-ed".equalsIgnoreCase(selectedGender)) return "coed".equals(norm);
        return "unknown".equals(norm);
    }

    private String normalizeGender(String value) {
        if (value == null) return "unknown";
        String s = value.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "unknown";
        if (s.contains("boy") || s.contains("male") || s.contains("男")) return "boys";
        if (s.contains("girl") || s.contains("female") || s.contains("女")) return "girls";
        if (s.contains("co") || s.contains("mixed") || s.contains("男女")) return "coed";
        return "unknown";
    }

    private interface ValueUpdater {
        void update(String value);
    }

    private void showLoading() {
        animateState(loadingView, recyclerView, errorView, emptyView);
    }

    private void showContent() {
        animateState(recyclerView, loadingView, errorView, emptyView);
    }

    private void showError() {
        animateState(errorView, loadingView, recyclerView, emptyView);
    }

    private void showEmpty() {
        animateState(emptyView, loadingView, recyclerView, errorView);
    }

    private void animateState(View target, View... others) {
        for (View v : others) {
            if (v.getVisibility() == View.VISIBLE) {
                v.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    v.setVisibility(View.GONE);
                    v.setAlpha(1f);
                }).start();
            }
        }
        if (target.getVisibility() != View.VISIBLE) {
            target.setAlpha(0f);
            target.setVisibility(View.VISIBLE);
            target.animate().alpha(1f).setDuration(160).start();
        }
    }

    private void applyPressFeedback(View... views) {
        for (View v : views) {
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

    private void pulseView(View view) {
        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction(
                () -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ).start();
    }

    private void applyQuickIconFeedback(View... views) {
        for (View v : views) {
            if (v == null) continue;
            v.setOnTouchListener((view, event) -> {
                if (!view.isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                }
                return false;
            });
        }
    }

    private void setUnderlined(TextView textView) {
        if (textView == null) return;
        textView.setPaintFlags(textView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }
}
