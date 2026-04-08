package com.example.smartschoolfinder.network;

import android.content.Context;
import android.util.Log;

import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.utils.LocaleUtils;

import java.util.List;

public class SchoolApiService {
    private final ApiClient apiClient = new ApiClient();
    private static final String COUNT_DEBUG_TAG = "COUNT_DEBUG";

    public void getSchools(Context context, ApiCallback<List<School>> callback) {
        // Keep English source-of-truth for counting/dedupe, but allow zh display enrichment.
        boolean zh = LocaleUtils.prefersChineseSchoolData(context);
        Log.d(COUNT_DEBUG_TAG, "school source mode = english master, displayLocale=" + (zh ? "zh" : "en"));
        apiClient.fetchSchools(context, callback, zh);
    }
}
