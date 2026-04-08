package com.example.smartschoolfinder.constants;

public final class AppConstants {
    public static final String PREFS_NAME = "smart_school_prefs";
    public static final String KEY_FAVORITES = "favorites";
    public static final String KEY_REVIEWS = "reviews";

    /** AppCompatDelegate mode: MODE_NIGHT_FOLLOW_SYSTEM, MODE_NIGHT_NO, or MODE_NIGHT_YES */
    public static final String KEY_THEME_MODE = "theme_night_mode";
    /** When false, distance uses Hong Kong default coordinates (no GPS). */
    public static final String KEY_USE_LOCATION = "use_location_for_distance";
    public static final String KEY_LOCATION_MODE = "location_mode";
    public static final String KEY_LOCATION_INITIALIZED = "location_initialized";
    public static final String KEY_CUSTOM_LOCATION_NAME = "custom_location_name";
    public static final String KEY_CUSTOM_LOCATION_LAT = "custom_location_lat";
    public static final String KEY_CUSTOM_LOCATION_LON = "custom_location_lon";
    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String KEY_APP_LANGUAGE = "app_language";

    public static final String EXTRA_SCHOOL_ID = "extra_school_id";

    public static final String REVIEW_API_BASE_URL = "http://10.0.2.2:3000/";
    public static final String TRANSPORT_API_BASE_URL = "http://10.0.2.2:3000/";

    // School data source URLs (supports multiple sources and merge).
    // SCH_LOC_EDB.json usually contains a large/full school list from EDB.
    public static final String[] SCHOOL_API_URLS = new String[]{
            "https://www.edb.gov.hk/attachment/en/student-parents/sch-info/sch-search/sch-location-info/SCH_LOC_EDB.json"
            // You can append more official sources here if the teacher provides additional URLs.
    };

    private AppConstants() {
    }
}
