package com.example.smartschoolfinder.data;

public class CompareRepository {
    private static String schoolAId;
    private static String schoolBId;

    public static String getSchoolAId() {
        return schoolAId;
    }

    public static void setSchoolAId(String schoolAId) {
        CompareRepository.schoolAId = schoolAId;
    }

    public static String getSchoolBId() {
        return schoolBId;
    }

    public static void setSchoolBId(String schoolBId) {
        CompareRepository.schoolBId = schoolBId;
    }
}
