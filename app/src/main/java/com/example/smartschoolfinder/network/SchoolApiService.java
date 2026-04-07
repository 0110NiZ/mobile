package com.example.smartschoolfinder.network;

import android.content.Context;

import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.utils.LocaleUtils;

import java.util.List;

public class SchoolApiService {
    private final ApiClient apiClient = new ApiClient();

    public void getSchools(Context context, ApiCallback<List<School>> callback) {
        boolean zh = LocaleUtils.prefersChineseSchoolData(context.getApplicationContext());
        apiClient.fetchSchools(callback, zh);
    }
}
