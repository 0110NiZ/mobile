package com.example.smartschoolfinder.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.Review;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReviewRepository {
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ReviewRepository(Context context) {
        prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<Review> getReviews(String schoolId) {
        String json = prefs.getString(AppConstants.KEY_REVIEWS + "_" + schoolId, "[]");
        Type type = new TypeToken<List<Review>>() {}.getType();
        List<Review> list = gson.fromJson(json, type);
        return list == null ? new ArrayList<>() : list;
    }

    public void addReview(String schoolId, int rating, String comment) {
        List<Review> list = getReviews(schoolId);
        list.add(new Review(UUID.randomUUID().toString(), schoolId, rating, comment, System.currentTimeMillis()));
        prefs.edit().putString(AppConstants.KEY_REVIEWS + "_" + schoolId, gson.toJson(list)).apply();
    }
}
