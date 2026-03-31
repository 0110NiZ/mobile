package com.example.smartschoolfinder.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
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
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private View loadingView;
    private View errorView;
    private View emptyView;
    private RecyclerView recyclerView;

    private View layoutFilterContent;
    private ImageView ivFilterToggle;
    private boolean isFilterExpanded = true;

    private EditText etSearch;
    private Spinner spinnerDistrict;
    private Spinner spinnerType;
    private ArrayAdapter<FilterOption> districtAdapter;
    private ArrayAdapter<FilterOption> typeAdapter;

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
    private String lastDistrictSelection = "All";
    private String lastTypeSelection = "All";

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

        loadingView = findViewById(R.id.loadingView);
        errorView = findViewById(R.id.errorView);
        emptyView = findViewById(R.id.emptyView);
        recyclerView = findViewById(R.id.recyclerSchools);

        layoutFilterContent = findViewById(R.id.layoutFilterContent);
        ivFilterToggle = findViewById(R.id.ivFilterToggle);
        if (ivFilterToggle != null) {
            ivFilterToggle.setOnClickListener(v -> toggleFilterPanel());
        }

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
        districtAdapter = new ArrayAdapter<FilterOption>(this, R.layout.item_spinner_selected, new ArrayList<>()) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setText(getItem(position) == null ? "" : getItem(position).label);
                }
                return v;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setText(getItem(position) == null ? "" : getItem(position).label);
                }
                return v;
            }
        };
        districtAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerDistrict.setAdapter(districtAdapter);

        typeAdapter = new ArrayAdapter<FilterOption>(this, R.layout.item_spinner_selected, new ArrayList<>()) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setText(getItem(position) == null ? "" : getItem(position).label);
                }
                return v;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof android.widget.TextView) {
                    ((android.widget.TextView) v).setText(getItem(position) == null ? "" : getItem(position).label);
                }
                return v;
            }
        };
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerType.setAdapter(typeAdapter);

        // Initial options (0 counts until data loaded)
        updateSpinnerOptionsFromCounts(0, 0, 0, 0, new LinkedHashMap<>());

        AdapterView.OnItemSelectedListener feedbackListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pulseView(parent);
                // Ignore the first automatic callback during initial setup.
                if (!hasInitializedDefaultFilter) {
                    return;
                }
                String currentDistrict = getSelectedDistrictValue();
                String currentType = getSelectedTypeValue();
                boolean changed = !currentDistrict.equals(lastDistrictSelection) || !currentType.equals(lastTypeSelection);
                lastDistrictSelection = currentDistrict;
                lastTypeSelection = currentType;
                if (changed) {
                    applyFilter(true);
                }
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

                // Update filter counts/options based on REAL data (before showing list).
                updateFilterCountsAndRefreshSpinners();

                refreshUserReferenceLocation();
                recalculateAllSchoolDistances();

                seedReviewsIfNeeded();

                if (!hasInitializedDefaultFilter) {
                    etSearch.setText("");
                    setSpinnerSelectionByValue(spinnerDistrict, "All");
                    setSpinnerSelectionByValue(spinnerType, "All");
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
        String district = getSelectedDistrictValue();
        String type = getSelectedTypeValue();

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

    private String getSelectedDistrictValue() {
        Object selected = spinnerDistrict == null ? null : spinnerDistrict.getSelectedItem();
        if (selected instanceof FilterOption) {
            return ((FilterOption) selected).value;
        }
        return selected == null ? "All" : selected.toString();
    }

    private String getSelectedTypeValue() {
        Object selected = spinnerType == null ? null : spinnerType.getSelectedItem();
        if (selected instanceof FilterOption) {
            return ((FilterOption) selected).value;
        }
        return selected == null ? "All" : selected.toString();
    }

    private void updateFilterCountsAndRefreshSpinners() {
        int total = rawSchoolList.size();
        int hk = 0, kw = 0, nt = 0;
        Map<String, Integer> typeCounts = new LinkedHashMap<>();

        for (School s : rawSchoolList) {
            if (s == null) continue;
            String dn = FilterUtils.normalizeDistrict(s.getDistrict());
            if ("hong kong island".equals(dn)) hk++;
            else if ("kowloon".equals(dn)) kw++;
            else if ("new territories".equals(dn)) nt++;

            String tn = FilterUtils.normalizeType(s.getType());
            if (tn == null || tn.trim().isEmpty() || "all".equals(tn)) continue;
            typeCounts.put(tn, (typeCounts.containsKey(tn) ? typeCounts.get(tn) : 0) + 1);
        }

        String selectedDistrict = lastDistrictSelection;
        String selectedType = lastTypeSelection;
        if (hasInitializedDefaultFilter) {
            selectedDistrict = getSelectedDistrictValue();
            selectedType = getSelectedTypeValue();
        }

        updateSpinnerOptionsFromCounts(total, hk, kw, nt, typeCounts);
        setSpinnerSelectionByValue(spinnerDistrict, selectedDistrict);
        setSpinnerSelectionByValue(spinnerType, selectedType);
    }

    private void updateSpinnerOptionsFromCounts(int total, int hk, int kw, int nt, Map<String, Integer> typeCountsNorm) {
        if (districtAdapter != null) {
            districtAdapter.clear();
            districtAdapter.add(new FilterOption("All", "All (" + total + ")"));
            districtAdapter.add(new FilterOption("Hong Kong Island", "Hong Kong Island (" + hk + ")"));
            districtAdapter.add(new FilterOption("Kowloon", "Kowloon (" + kw + ")"));
            districtAdapter.add(new FilterOption("New Territories", "New Territories (" + nt + ")"));
            districtAdapter.notifyDataSetChanged();
        }

        if (typeAdapter != null) {
            typeAdapter.clear();
            typeAdapter.add(new FilterOption("All", "All (" + total + ")"));

            // Show common types in a stable order; only include if present in data.
            addTypeOptionIfPresent(typeCountsNorm, "primary", "Primary", typeAdapter);
            addTypeOptionIfPresent(typeCountsNorm, "secondary", "Secondary", typeAdapter);
            addTypeOptionIfPresent(typeCountsNorm, "kindergarten", "Kindergarten", typeAdapter);
            addTypeOptionIfPresent(typeCountsNorm, "international", "International", typeAdapter);
            addTypeOptionIfPresent(typeCountsNorm, "special", "Special", typeAdapter);

            typeAdapter.notifyDataSetChanged();
        }
    }

    private void addTypeOptionIfPresent(Map<String, Integer> typeCountsNorm, String normKey, String displayValue, ArrayAdapter<FilterOption> adapter) {
        if (typeCountsNorm == null || adapter == null) return;
        Integer count = typeCountsNorm.get(normKey);
        if (count == null || count <= 0) return;
        adapter.add(new FilterOption(displayValue, displayValue + " (" + count + ")"));
    }

    private void setSpinnerSelectionByValue(Spinner spinner, String value) {
        if (spinner == null || value == null) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            Object item = spinner.getItemAtPosition(i);
            if (item instanceof FilterOption) {
                if (value.equals(((FilterOption) item).value)) {
                    spinner.setSelection(i);
                    return;
                }
            } else if (value.equals(item == null ? "" : item.toString())) {
                spinner.setSelection(i);
                return;
            }
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
