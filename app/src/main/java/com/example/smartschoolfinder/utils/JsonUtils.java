package com.example.smartschoolfinder.utils;

import com.google.gson.Gson;

public class JsonUtils {
    private static final Gson gson = new Gson();

    private JsonUtils() {
    }

    public static String toJson(Object object) {
        return gson.toJson(object);
    }
}
