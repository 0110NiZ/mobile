package com.example.smartschoolfinder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.SchoolAdapter;
import com.example.smartschoolfinder.data.FavoritesManager;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.network.SchoolApiService;
import com.example.smartschoolfinder.utils.LocationModeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FavoritesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyView;
    private SchoolAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerFavorites);
        emptyView = findViewById(R.id.tvFavoritesEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SchoolAdapter(school -> {
            Intent intent = new Intent(FavoritesActivity.this, DetailActivity.class);
            intent.putExtra("school_id", school.getId());
            startActivity(intent);
            overridePendingTransition(R.anim.ssf_slide_in_right, R.anim.ssf_fade_out);
        });
        recyclerView.setAdapter(adapter);

        loadFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        FavoritesManager manager = new FavoritesManager(this);
        Set<String> favoriteIds = manager.getFavorites();

        new SchoolApiService().getSchools(this, new ApiCallback<List<School>>() {
            @Override
            public void onSuccess(List<School> data) {
                List<School> favorites = new ArrayList<>();
                for (School school : data) {
                    if (favoriteIds.contains(school.getId())) {
                        favorites.add(school);
                    }
                }

                LocationModeUtils.LatLng effective = LocationModeUtils.getEffectiveLocation(FavoritesActivity.this);
                boolean showDistance = effective != null;
                for (School s : favorites) {
                    if (showDistance) {
                        s.updateDistanceFrom(effective.lat, effective.lon);
                    } else {
                        s.clearDistance();
                    }
                }

                adapter.setShowDistance(showDistance);
                adapter.setData(favorites);
                recyclerView.setAlpha(0f);
                recyclerView.animate().alpha(1f).setDuration(180).start();
                if (favorites.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (emptyView instanceof TextView) {
                    ((TextView) emptyView).setText(R.string.load_error);
                }
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }
}
