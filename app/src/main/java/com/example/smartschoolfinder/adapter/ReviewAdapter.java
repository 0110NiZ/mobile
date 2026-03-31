package com.example.smartschoolfinder.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import android.widget.BaseAdapter;
import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.Review;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReviewAdapter extends BaseAdapter {
    private final List<Review> data;
    private final Map<String, Integer> localReaction = new HashMap<>();
    private final OnReactListener onReactListener;
    private final OnOwnerActionListener onOwnerActionListener;

    public interface OnReactListener {
        void onReact(Review review, String action);
    }

    public interface OnOwnerActionListener {
        void onEdit(Review review);
        void onDelete(Review review);
    }

    public ReviewAdapter(List<Review> data, OnReactListener onReactListener, OnOwnerActionListener onOwnerActionListener) {
        this.data = data;
        this.onReactListener = onReactListener;
        this.onOwnerActionListener = onOwnerActionListener;
    }

    public void setLocalReaction(String reviewId, int state) {
        if (reviewId == null) return;
        localReaction.put(reviewId, state);
        notifyDataSetChanged();
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
        ViewHolder holder;
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
            holder = new ViewHolder();
            holder.tvName = view.findViewById(R.id.tvReviewerName);
            holder.tvBadge = view.findViewById(R.id.tvReviewBadge);
            holder.ownerActions = view.findViewById(R.id.ownerActions);
            holder.btnEdit = view.findViewById(R.id.btnEdit);
            holder.btnDelete = view.findViewById(R.id.btnDelete);
            holder.tvComment = view.findViewById(R.id.tvReviewComment);
            holder.tvDate = view.findViewById(R.id.tvReviewDate);
            holder.ratingBar = view.findViewById(R.id.ratingReview);
            holder.btnLike = view.findViewById(R.id.btnLike);
            holder.btnDislike = view.findViewById(R.id.btnDislike);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        Review review = data.get(position);
        String name = review.getReviewerName();
        holder.tvName.setText(name == null || name.trim().isEmpty()
                ? view.getContext().getString(R.string.guest_user)
                : name);
        holder.tvComment.setText(review.getComment());
        holder.ratingBar.setRating(review.getRating());
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(review.getTimestamp()));
        holder.tvDate.setText(date);

        if (review.isSeeded()) {
            holder.tvBadge.setText(R.string.sample_badge);
            holder.tvBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvBadge.setVisibility(View.GONE);
        }

        String id = review.getId();
        int state;
        if (id != null && localReaction.containsKey(id)) {
            state = localReaction.getOrDefault(id, 0);
        } else {
            String r = review.getUserReaction();
            if ("like".equalsIgnoreCase(r)) state = 1;
            else if ("dislike".equalsIgnoreCase(r)) state = -1;
            else state = 0;
        }
        holder.btnLike.setText(view.getContext().getString(R.string.like_label, review.getLikes()));
        holder.btnDislike.setText(view.getContext().getString(R.string.dislike_label, review.getDislikes()));

        int active = view.getContext().getColor(R.color.reaction_active);
        int inactive = view.getContext().getColor(R.color.ssf_text_hint);
        holder.btnLike.setTextColor(state == 1 ? active : inactive);
        holder.btnDislike.setTextColor(state == -1 ? active : inactive);

        holder.btnLike.setOnClickListener(v -> {
            if (onReactListener != null) {
                onReactListener.onReact(review, "like");
            }
        });
        holder.btnDislike.setOnClickListener(v -> {
            if (onReactListener != null) {
                onReactListener.onReact(review, "dislike");
            }
        });

        if (review.isOwner() && review.isUserComment()) {
            holder.ownerActions.setVisibility(View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> {
                if (onOwnerActionListener != null) {
                    onOwnerActionListener.onEdit(review);
                }
            });
            holder.btnDelete.setOnClickListener(v -> {
                if (onOwnerActionListener != null) {
                    onOwnerActionListener.onDelete(review);
                }
            });
        } else {
            holder.ownerActions.setVisibility(View.GONE);
            holder.btnEdit.setOnClickListener(null);
            holder.btnDelete.setOnClickListener(null);
        }
        return view;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvBadge;
        LinearLayout ownerActions;
        ImageView btnEdit;
        ImageView btnDelete;
        TextView tvComment;
        TextView tvDate;
        RatingBar ratingBar;
        TextView btnLike;
        TextView btnDislike;
    }
}
