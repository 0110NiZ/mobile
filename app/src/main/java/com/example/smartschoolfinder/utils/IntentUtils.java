package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class IntentUtils {
    public static void openDial(Context context, String phone) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phone));
        context.startActivity(intent);
    }

    public static void openMap(Context context, String queryAddress, double lat, double lng) {
        String uri = "geo:" + lat + "," + lng + "?q=" + Uri.encode(queryAddress);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + Uri.encode(queryAddress)));
        }
        context.startActivity(intent);
    }
}
