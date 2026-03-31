package com.example.smartschoolfinder.model;

import java.util.List;

public class ReviewListResponse {
    private double averageRating;
    private int totalReviews;
    private List<Review> reviews;

    public double getAverageRating() {
        return averageRating;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public List<Review> getReviews() {
        return reviews;
    }
}

