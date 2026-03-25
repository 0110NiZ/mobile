package com.example.smartschoolfinder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.SchoolAdapter;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.FilterUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private View loadingView;
    private View errorView;
    private TextView emptyView;
    private RecyclerView recyclerView;

    private EditText etSearch;
    private Spinner spinnerDistrict;
    private Spinner spinnerType;

    private SchoolAdapter adapter;
    private final List<School> allSchools = new ArrayList<>();

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

        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnRetry = findViewById(R.id.btnRetry);
        Button btnFavorites = findViewById(R.id.btnFavorites);
        Button btnCompare = findViewById(R.id.btnCompare);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SchoolAdapter(school -> {
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("school_id", school.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        setupSpinners();

        btnSearch.setOnClickListener(v -> applyFilter());
        btnRetry.setOnClickListener(v -> loadSchools());
        btnFavorites.setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));
        btnCompare.setOnClickListener(v -> startActivity(new Intent(this, CompareActivity.class)));

        loadSchools();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!allSchools.isEmpty()) {
            applyFilter();
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
    }

    private void loadSchools() {
        showLoading();
        new SchoolApiService().getSchools(new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                allSchools.clear();
                allSchools.addAll(data);
                applyFilter();
            }

            @Override
            public void onError(String message) {
                showError();
            }
        });
    }

    private void applyFilter() {
        String keyword = etSearch.getText().toString();
        String district = spinnerDistrict.getSelectedItem().toString();
        String type = spinnerType.getSelectedItem().toString();

        List<School> filtered = FilterUtils.filter(allSchools, keyword, district, type);
        adapter.setData(filtered);

        if (filtered.isEmpty()) {
            showEmpty();
        } else {
            showContent();
        }
    }

    private void showLoading() {
        loadingView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    private void showError() {
        loadingView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }
}
