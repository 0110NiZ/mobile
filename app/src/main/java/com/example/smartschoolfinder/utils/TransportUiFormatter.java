package com.example.smartschoolfinder.utils;

import android.content.Context;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.TransportInfo;

/**
 * Formats {@link TransportInfo} for UI the same way as the school detail screen
 * (line templates, N/A handling, localized distance units).
 */
public final class TransportUiFormatter {

    private TransportUiFormatter() {
    }

    public static String transportNa(Context ctx) {
        return ctx.getString(R.string.transport_not_available);
    }

    public static String formatPlace(Context ctx, String raw) {
        if (raw == null || raw.trim().isEmpty() || "N/A".equalsIgnoreCase(raw.trim())) {
            return transportNa(ctx);
        }
        return raw.trim();
    }

    public static String formatDistance(Context ctx, String distRaw) {
        if (distRaw == null || distRaw.trim().isEmpty() || "N/A".equalsIgnoreCase(distRaw.trim())) {
            return transportNa(ctx);
        }
        String t = distRaw.trim();
        if (t.matches("^\\d+(\\.\\d+)?m$")) {
            double meters = Double.parseDouble(t.substring(0, t.length() - 1));
            double km = meters / 1000.0;
            return ctx.getString(R.string.transport_distance_meters, km);
        }
        return t;
    }

    public static String formatConvenience(Context ctx, String raw) {
        if (raw == null || raw.trim().isEmpty() || "N/A".equalsIgnoreCase(raw.trim())) {
            return transportNa(ctx);
        }
        return raw.trim();
    }

    private static String lineWithDistance(Context ctx, int lineFmtRes, int simpleNaRes,
                                           String placeRaw, String distRaw) {
        String place = formatPlace(ctx, placeRaw);
        String dist = formatDistance(ctx, distRaw);
        String na = transportNa(ctx);
        if (na.equals(place) && na.equals(dist)) {
            return ctx.getString(simpleNaRes);
        }
        return ctx.getString(lineFmtRes, place, dist);
    }

    public static String lineMtr(Context ctx, TransportInfo info) {
        if (info == null) {
            return ctx.getString(R.string.transport_na_mtr);
        }
        return lineWithDistance(ctx, R.string.transport_line_mtr, R.string.transport_na_mtr,
                info.getMtrStation(), info.getMtrDistance());
    }

    public static String lineBus(Context ctx, TransportInfo info) {
        if (info == null) {
            return ctx.getString(R.string.transport_na_bus);
        }
        return lineWithDistance(ctx, R.string.transport_line_bus, R.string.transport_na_bus,
                info.getBusStation(), info.getBusDistance());
    }

    public static String lineMinibus(Context ctx, TransportInfo info) {
        if (info == null) {
            return ctx.getString(R.string.transport_na_minibus);
        }
        return lineWithDistance(ctx, R.string.transport_line_minibus, R.string.transport_na_minibus,
                info.getMinibusStation(), info.getMinibusDistance());
    }

    public static String lineConvenience(Context ctx, TransportInfo info) {
        if (info == null) {
            return ctx.getString(R.string.transport_na_convenience);
        }
        return ctx.getString(R.string.transport_line_convenience,
                formatConvenience(ctx, info.getConvenienceScore()));
    }
}
