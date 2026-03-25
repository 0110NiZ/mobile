package com.example.smartschoolfinder.network;

import com.example.smartschoolfinder.model.School;

import java.util.List;

public class SchoolApiService {
    private final ApiClient apiClient = new ApiClient();

    public void getSchools(ApiCallback<List<School>> callback) {
        apiClient.fetchSchools(callback);
    }
}
