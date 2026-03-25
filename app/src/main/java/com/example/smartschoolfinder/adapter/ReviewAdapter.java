package com.example.smartschoolfinder.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import android.widget.BaseAdapter;
import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.Review;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReviewAdapter extends BaseAdapter {
    private final List<Review> data;

    public ReviewAdapter(List<Review> data) {
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        }

        TextView tvComment = view.findViewById(R.id.tvReviewComment);
        TextView tvDate = view.findViewById(R.id.tvReviewDate);
        RatingBar ratingBar = view.findViewById(R.id.ratingReview);

        Review review = data.get(position);
        tvComment.setText(review.getComment());
        ratingBar.setRating(review.getRating());
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(review.getTimestamp()));
        tvDate.setText(date);
        return view;
    }
}
