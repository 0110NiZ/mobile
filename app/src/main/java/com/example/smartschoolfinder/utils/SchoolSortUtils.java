package com.example.smartschoolfinder.utils;

import android.content.Context;

import com.example.smartschoolfinder.model.School;

import net.sourceforge.pinyin4j.PinyinHelper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SchoolSortUtils {
    private SchoolSortUtils() {
    }

    private static final Map<Character, String> JYUTPING_CHAR_MAP = new HashMap<>();
    static {
        JYUTPING_CHAR_MAP.put('香', "HOENG");
        JYUTPING_CHAR_MAP.put('港', "GONG");
        JYUTPING_CHAR_MAP.put('仔', "ZAI");
        JYUTPING_CHAR_MAP.put('灣', "WAAN");
        JYUTPING_CHAR_MAP.put('會', "WUI");
        JYUTPING_CHAR_MAP.put('學', "HOK");
        JYUTPING_CHAR_MAP.put('校', "HAAU");
        JYUTPING_CHAR_MAP.put('書', "SYU");
        JYUTPING_CHAR_MAP.put('院', "JYUN");
        JYUTPING_CHAR_MAP.put('道', "DOU");
        JYUTPING_CHAR_MAP.put('教', "GAAU");
        JYUTPING_CHAR_MAP.put('佛', "FAT");
        JYUTPING_CHAR_MAP.put('基', "GEI");
        JYUTPING_CHAR_MAP.put('督', "DUK");
        JYUTPING_CHAR_MAP.put('天', "TIN");
        JYUTPING_CHAR_MAP.put('主', "ZYU");
        JYUTPING_CHAR_MAP.put('幼', "JAU");
        JYUTPING_CHAR_MAP.put('稚', "ZI");
        JYUTPING_CHAR_MAP.put('園', "JYUN");
        JYUTPING_CHAR_MAP.put('英', "JING");
        JYUTPING_CHAR_MAP.put('藝', "NGAI");
        JYUTPING_CHAR_MAP.put('黃', "WONG");
        JYUTPING_CHAR_MAP.put('大', "DAAI");
        JYUTPING_CHAR_MAP.put('仙', "SIN");
        JYUTPING_CHAR_MAP.put('九', "GAU");
        JYUTPING_CHAR_MAP.put('龍', "LUNG");
        JYUTPING_CHAR_MAP.put('新', "SAN");
        JYUTPING_CHAR_MAP.put('界', "GAAI");
        JYUTPING_CHAR_MAP.put('中', "ZUNG");
        JYUTPING_CHAR_MAP.put('西', "SAI");
        JYUTPING_CHAR_MAP.put('東', "DUNG");
        JYUTPING_CHAR_MAP.put('南', "NAM");
        JYUTPING_CHAR_MAP.put('北', "BAK");
    }

    public static String getSortKeyForSchool(Context context, School school) {
        String display = SchoolDisplayUtils.displayName(context, school);
        if (display == null) return "ZZZZ";
        String value = display.trim();
        if (value.isEmpty()) return "ZZZZ";
        if (containsCjk(value)) {
            return normalizeLatin(toRomanizedKey(value));
        }
        return normalizeLatin(value);
    }

    public static String getInitialForSchool(Context context, School school) {
        String key = getSortKeyForSchool(context, school);
        return getInitialFromSortKey(key);
    }

    public static String getInitialFromSortKey(String key) {
        if (key == null) return "#";
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return String.valueOf(c);
            }
        }
        return "#";
    }

    private static String toRomanizedKey(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isAsciiLetter(ch) || Character.isDigit(ch)) {
                out.append(Character.toUpperCase(ch));
                continue;
            }
            if (Character.isWhitespace(ch)) {
                out.append(' ');
                continue;
            }
            String mapped = JYUTPING_CHAR_MAP.get(ch);
            if (mapped != null && !mapped.isEmpty()) {
                out.append(mapped).append(' ');
                continue;
            }
            String fallback = fallbackRomanize(ch);
            if (!fallback.isEmpty()) {
                out.append(fallback).append(' ');
            }
        }
        return out.toString();
    }

    private static String fallbackRomanize(char ch) {
        try {
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(ch);
            if (arr != null && arr.length > 0 && arr[0] != null) {
                return arr[0].replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String normalizeLatin(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) return "ZZZZ";
        v = v.replaceAll("\\s+", " ");
        return v;
    }

    private static boolean containsCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
