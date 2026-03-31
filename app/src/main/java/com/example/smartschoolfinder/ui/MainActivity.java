package com.example.smartschoolfinder.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.SchoolAdapter;
import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.data.ReviewRepository;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.FilterUtils;
import com.example.smartschoolfinder.utils.LocationHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private View loadingView;
    private View errorView;
    private View emptyView;
    private RecyclerView recyclerView;

    private EditText etSearch;
    private Spinner spinnerDistrict;
    private Spinner spinnerType;

    private Button btnSortDistance;
    private Button btnNearestFive;

    private SchoolAdapter adapter;
    /** 完整原始数据，筛选都基于此列表 */
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

        loadingView = findViewById(R.id.loadingView);
        errorView = findViewById(R.id.errorView);
        emptyView = findViewById(R.id.emptyView);
        recyclerView = findViewById(R.id.recyclerSchools);

        etSearch = findViewById(R.id.etSearch);
        spinnerDistrict = findViewById(R.id.spinnerDistrict);
        spinnerType = findViewById(R.id.spinnerType);

        btnSortDistance = findViewById(R.id.btnSortDistance);
        btnNearestFive = findViewById(R.id.btnNearestFive);

        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnRetry = findViewById(R.id.btnRetry);
        Button btnFavorites = findViewById(R.id.btnFavorites);
        Button btnCompare = findViewById(R.id.btnCompare);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        adapter = new SchoolAdapter(school -> {
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("school_id", school.getId());
            startActivity(intent);
            overridePendingTransition(R.anim.ssf_slide_in_right, R.anim.ssf_fade_out);
        });
        recyclerView.setAdapter(adapter);

        setupSpinners();
        updateSortButtonLabel();
        applyPressFeedback(btnSearch, btnRetry, btnFavorites, btnCompare, btnSortDistance, btnNearestFive);

        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this);
        } else {
            refreshUserReferenceLocation();
        }

        btnSearch.setOnClickListener(v -> applyFilter(true));
        btnRetry.setOnClickListener(v -> loadSchools());
        btnFavorites.setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));
        btnCompare.setOnClickListener(v -> startActivity(new Intent(this, CompareActivity.class)));

        btnSortDistance.setOnClickListener(v -> {
            sortByDistance = !sortByDistance;
            updateSortButtonLabel();
            applyFilter(false);
        });

        btnNearestFive.setOnClickListener(v -> {
            nearestFiveOnly = true;
            applyFilter(false);
        });

        loadSchools();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LocationHelper.REQUEST_CODE_LOCATION) {
            // 拒绝权限不崩溃：继续使用默认香港坐标
            refreshUserReferenceLocation();
            recalculateAllSchoolDistances();
            applyFilter(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!rawSchoolList.isEmpty()) {
            applyFilter(false);
        }
    }

    private void setupSpinners() {
        ArrayAdapter<String> districtAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"All", "Hong Kong Island", "Kowloon", "New Territories"});
        districtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDistrict.setAdapter(districtAdapter);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"All", "Primary", "Secondary", "International"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        AdapterView.OnItemSelectedListener feedbackListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pulseView(parent);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spinnerDistrict.setOnItemSelectedListener(feedbackListener);
        spinnerType.setOnItemSelectedListener(feedbackListener);
    }

    private void updateSortButtonLabel() {
        if (sortByDistance) {
            btnSortDistance.setText(R.string.sort_distance_on);
        } else {
            btnSortDistance.setText(R.string.sort_distance_off);
        }
    }

    /**
     * 从系统读取最后已知位置；失败则保持/回退到香港默认坐标。
     */
    private void refreshUserReferenceLocation() {
        if (!LocationHelper.hasLocationPermission(this)) {
            userLatitude = LocationHelper.HK_DEFAULT_LATITUDE;
            userLongitude = LocationHelper.HK_DEFAULT_LONGITUDE;
            return;
        }
        Location loc = LocationHelper.getBestLastKnownLocation(this);
        if (LocationHelper.isValidLocation(loc)) {
            userLatitude = loc.getLatitude();
            userLongitude = loc.getLongitude();
        } else {
            userLatitude = LocationHelper.HK_DEFAULT_LATITUDE;
            userLongitude = LocationHelper.HK_DEFAULT_LONGITUDE;
        }
    }

    /** 对原始列表中每所学校写入与用户参考点的距离 */
    private void recalculateAllSchoolDistances() {
        for (School s : rawSchoolList) {
            s.updateDistanceFrom(userLatitude, userLongitude);
        }
    }

    private void loadSchools() {
        showLoading();
        new SchoolApiService().getSchools(new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                rawSchoolList.clear();
                if (data != null) {
                    rawSchoolList.addAll(data);
                }

                refreshUserReferenceLocation();
                recalculateAllSchoolDistances();

                seedReviewsIfNeeded();

                if (!hasInitializedDefaultFilter) {
                    etSearch.setText("");
                    spinnerDistrict.setSelection(0);
                    spinnerType.setSelection(0);
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
     * @param resetNearestFive 为 true 时退出「最近 5 所」模式（例如用户点击搜索，恢复完整筛选结果）
     */
    private void applyFilter(boolean resetNearestFive) {
        if (resetNearestFive) {
            nearestFiveOnly = false;
        }

        String keyword = etSearch.getText().toString();
        String district = spinnerDistrict.getSelectedItem().toString();
        String type = spinnerType.getSelectedItem().toString();

        // 1) 类型、地区、关键字：始终基于完整原始列表
        List<School> working = new ArrayList<>(FilterUtils.filter(rawSchoolList, keyword, district, type));

        if (nearestFiveOnly) {
            // 最近 5 所：只保留有距离的学校，按近到远，最多 5 条
            List<School> withDistance = new ArrayList<>();
            for (School s : working) {
                if (s.hasValidDistance()) {
                    withDistance.add(s);
                }
            }
            Collections.sort(withDistance, DISTANCE_COMPARATOR);
            int take = Math.min(5, withDistance.size());
            working.clear();
            for (int i = 0; i < take; i++) {
                working.add(withDistance.get(i));
            }
        } else if (sortByDistance) {
            // 全列表按距离排序，无距离排在后面
            Collections.sort(working, DISTANCE_COMPARATOR);
        }

        filteredSchoolList.clear();
        filteredSchoolList.addAll(working);
        adapter.setData(filteredSchoolList);
        recyclerView.setAlpha(0f);
        recyclerView.animate().alpha(1f).setDuration(180).start();

        if (filteredSchoolList.isEmpty()) {
            showEmpty();
        } else {
            showContent();
        }
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
}
