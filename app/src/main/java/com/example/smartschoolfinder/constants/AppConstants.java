package com.example.smartschoolfinder.constants;

public final class AppConstants {
    public static final String PREFS_NAME = "smart_school_prefs";
    public static final String KEY_FAVORITES = "favorites";
    public static final String KEY_REVIEWS = "reviews";

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
