package com.example.smartschoolfinder.network;

import android.content.Context;
import android.util.Log;

import com.example.smartschoolfinder.model.School;

import java.util.List;

public class SchoolApiService {
    private final ApiClient apiClient = new ApiClient();
    private static final String COUNT_DEBUG_TAG = "COUNT_DEBUG";

    public void getSchools(Context context, ApiCallback<List<School>> callback) {
        // Single source of truth: always build master list from English fields,
        // then let UI layer localize labels only.
        Log.d(COUNT_DEBUG_TAG, "school source mode = english master");
        apiClient.fetchSchools(callback, false);
    }
}
