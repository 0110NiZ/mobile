package com.example.smartschoolfinder.utils;

import net.sourceforge.pinyin4j.PinyinHelper;

public final class PinyinUtils {
    private PinyinUtils() {
    }

    public static String firstLetter(String text) {
        if (text == null) return "#";
        String value = text.trim();
        if (value.isEmpty()) return "#";
        char ch = value.charAt(0);
        if (Character.isLetter(ch)) {
            return String.valueOf(Character.toUpperCase(ch));
        }
        try {
            String[] pinyin = PinyinHelper.toHanyuPinyinStringArray(ch);
            if (pinyin != null && pinyin.length > 0 && pinyin[0] != null && !pinyin[0].isEmpty()) {
                char first = Character.toUpperCase(pinyin[0].charAt(0));
                if (first >= 'A' && first <= 'Z') {
                    return String.valueOf(first);
                }
            }
        } catch (Exception ignored) {
        }
        return "#";
    }
}
