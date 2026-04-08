package com.example.smartschoolfinder.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.Review;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReviewRecyclerAdapter extends RecyclerView.Adapter<ReviewRecyclerAdapter.ViewHolder> {
    private static final String COMMENT_REPLY_DEBUG = "COMMENT_REPLY_DEBUG";
    private static final long REACTION_ANIM_DURATION_MS = 120L;
    private static final float REACTION_ANIM_SCALE = 1.3f;
    private static final int LIKE_FILLED_ICON = R.drawable.ic_thumb_up_filled_24;
    private static final int LIKE_OUTLINE_ICON = R.drawable.ic_thumb_up_outline_24;
    private static final int DISLIKE_FILLED_ICON = R.drawable.ic_thumb_down_filled_24;
    private static final int DISLIKE_OUTLINE_ICON = R.drawable.ic_thumb_down_outline_24;
    private final List<Review> data;
    private final Map<String, Integer> localReaction = new HashMap<>();
    private final Map<String, Boolean> expandedReplyInputs = new HashMap<>();
    private final OnReactListener onReactListener;
    private final OnOwnerActionListener onOwnerActionListener;
    private final OnReplyListener onReplyListener;

    public interface OnReactListener {
        void onReact(Review review, String action);
    }

    public interface OnOwnerActionListener {
        void onEdit(Review review);

        void onDelete(Review review);
    }

    public interface OnReplyListener {
        void onReplySubmit(Review parentReview, String replyContent);
    }

    public ReviewRecyclerAdapter(List<Review> data,
                                 OnReactListener onReactListener,
                                 OnOwnerActionListener onOwnerActionListener,
                                 OnReplyListener onReplyListener) {
        this.data = data;
        this.onReactListener = onReactListener;
        this.onOwnerActionListener = onOwnerActionListener;
        this.onReplyListener = onReplyListener;
        setHasStableIds(true);
    }

    public void setLocalReaction(String reviewId, int state) {
        if (reviewId == null) return;
        localReaction.put(reviewId, state);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        Review r = getTopLevelReview(position);
        String id = r == null ? null : r.getId();
        if (id == null) {
            return RecyclerView.NO_ID;
        }
        return id.hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        View view = holder.itemView;
        Review review = getTopLevelReview(position);
        if (review == null) return;

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
        applyReactionUi(holder, state, active, inactive);

        holder.btnLike.setOnClickListener(v -> {
            animateReaction(v);
            if (onReactListener != null) {
                onReactListener.onReact(review, "like");
            }
        });
        holder.btnDislike.setOnClickListener(v -> {
            animateReaction(v);
            if (onReactListener != null) {
                onReactListener.onReact(review, "dislike");
            }
        });
        holder.btnReply.setOnClickListener(v -> {
            String reviewId = review.getId() == null ? "" : review.getId();
            boolean expand = !expandedReplyInputs.getOrDefault(reviewId, false);
            expandedReplyInputs.put(reviewId, expand);
            holder.replyInputContainer.setVisibility(expand ? View.VISIBLE : View.GONE);
            if (!expand) {
                holder.etReplyInput.setText("");
            }
        });
        holder.btnReplyCancel.setOnClickListener(v -> {
            String reviewId = review.getId() == null ? "" : review.getId();
            expandedReplyInputs.put(reviewId, false);
            holder.replyInputContainer.setVisibility(View.GONE);
            holder.etReplyInput.setText("");
        });
        holder.btnReplySubmit.setOnClickListener(v -> {
            if (onReplyListener == null) return;
            String reply = holder.etReplyInput.getText() == null ? "" : holder.etReplyInput.getText().toString().trim();
            if (reply.isEmpty()) return;
            onReplyListener.onReplySubmit(review, reply);
            String reviewId = review.getId() == null ? "" : review.getId();
            expandedReplyInputs.put(reviewId, false);
            holder.replyInputContainer.setVisibility(View.GONE);
            holder.etReplyInput.setText("");
        });

        String reviewId = review.getId() == null ? "" : review.getId();
        boolean expanded = expandedReplyInputs.getOrDefault(reviewId, false);
        holder.replyInputContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        renderReplies(holder.repliesContainer, review);

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
    }

    @Override
    public int getItemCount() {
        return getTopLevelReviews().size();
    }

    private List<Review> getTopLevelReviews() {
        List<Review> top = new java.util.ArrayList<>();
        if (data == null) return top;
        for (Review r : data) {
            if (r == null) continue;
            if (r.getParentId() == null || r.getParentId().trim().isEmpty()) {
                top.add(r);
            }
        }
        return top;
    }

    private Review getTopLevelReview(int position) {
        List<Review> top = getTopLevelReviews();
        if (position < 0 || position >= top.size()) return null;
        return top.get(position);
    }

    private List<Review> getRepliesForParent(Review parent) {
        List<Review> replies = new java.util.ArrayList<>();
        if (data == null || parent == null || parent.getId() == null) return replies;
        String pid = parent.getId();
        for (Review r : data) {
            if (r == null) continue;
            String parentId = r.getParentId();
            if (parentId != null && parentId.equals(pid)) {
                replies.add(r);
            }
        }
        replies.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        Log.d(COMMENT_REPLY_DEBUG, "parentId=" + pid + ", replyCount=" + replies.size());
        return replies;
    }

    private void renderReplies(@NonNull LinearLayout container, @NonNull Review parent) {
        container.removeAllViews();
        List<Review> replies = getRepliesForParent(parent);
        if (replies.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int i = 0; i < replies.size(); i++) {
            Review reply = replies.get(i);
            View row = inflater.inflate(R.layout.item_review_reply, container, false);
            TextView tvName = row.findViewById(R.id.tvReplyName);
            TextView tvContent = row.findViewById(R.id.tvReplyContent);
            TextView tvTime = row.findViewById(R.id.tvReplyTime);
            LinearLayout replyOwnerActions = row.findViewById(R.id.replyOwnerActions);
            ImageView btnReplyEdit = row.findViewById(R.id.btnReplyEdit);
            ImageView btnReplyDelete = row.findViewById(R.id.btnReplyDelete);
            String name = reply.getReviewerName();
            tvName.setText(name == null || name.trim().isEmpty()
                    ? container.getContext().getString(R.string.guest_user)
                    : name);
            tvContent.setText(reply.getComment());
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(reply.getTimestamp()));
            tvTime.setText(date);

            if (reply.isOwner() && reply.isUserComment() && onOwnerActionListener != null) {
                replyOwnerActions.setVisibility(View.VISIBLE);
                btnReplyEdit.setOnClickListener(v -> onOwnerActionListener.onEdit(reply));
                btnReplyDelete.setOnClickListener(v -> onOwnerActionListener.onDelete(reply));
            } else {
                replyOwnerActions.setVisibility(View.GONE);
                btnReplyEdit.setOnClickListener(null);
                btnReplyDelete.setOnClickListener(null);
            }
            container.addView(row);
        }
    }

    private void applyReactionUi(@NonNull ViewHolder holder, int state, int activeColor, int inactiveColor) {
        holder.btnLike.animate().cancel();
        holder.btnDislike.animate().cancel();
        holder.btnLike.setScaleX(1f);
        holder.btnLike.setScaleY(1f);
        holder.btnDislike.setScaleX(1f);
        holder.btnDislike.setScaleY(1f);

        setReactionButtonVisual(holder.btnLike, state == 1, activeColor, inactiveColor,
                LIKE_FILLED_ICON, LIKE_OUTLINE_ICON);
        setReactionButtonVisual(holder.btnDislike, state == -1, activeColor, inactiveColor,
                DISLIKE_FILLED_ICON, DISLIKE_OUTLINE_ICON);
    }

    private void setReactionButtonVisual(@NonNull TextView button, boolean selected, int activeColor, int inactiveColor,
                                         int selectedIconRes, int unselectedIconRes) {
        int color = selected ? activeColor : inactiveColor;
        button.setTextColor(color);
        Drawable icon = AppCompatResources.getDrawable(button.getContext(), selected ? selectedIconRes : unselectedIconRes);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(color));
    }

    private void animateReaction(@NonNull View target) {
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.animate()
                .scaleX(REACTION_ANIM_SCALE)
                .scaleY(REACTION_ANIM_SCALE)
                .setDuration(REACTION_ANIM_DURATION_MS)
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(REACTION_ANIM_DURATION_MS)
                        .start())
                .start();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvBadge;
        final LinearLayout ownerActions;
        final ImageView btnEdit;
        final ImageView btnDelete;
        final TextView tvComment;
        final TextView tvDate;
        final RatingBar ratingBar;
        final TextView btnLike;
        final TextView btnDislike;
        final TextView btnReply;
        final LinearLayout replyInputContainer;
        final EditText etReplyInput;
        final Button btnReplySubmit;
        final Button btnReplyCancel;
        final LinearLayout repliesContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvReviewerName);
            tvBadge = itemView.findViewById(R.id.tvReviewBadge);
            ownerActions = itemView.findViewById(R.id.ownerActions);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            tvComment = itemView.findViewById(R.id.tvReviewComment);
            tvDate = itemView.findViewById(R.id.tvReviewDate);
            ratingBar = itemView.findViewById(R.id.ratingReview);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnDislike = itemView.findViewById(R.id.btnDislike);
            btnReply = itemView.findViewById(R.id.btnReply);
            replyInputContainer = itemView.findViewById(R.id.replyInputContainer);
            etReplyInput = itemView.findViewById(R.id.etReplyInput);
            btnReplySubmit = itemView.findViewById(R.id.btnReplySubmit);
            btnReplyCancel = itemView.findViewById(R.id.btnReplyCancel);
            repliesContainer = itemView.findViewById(R.id.repliesContainer);
        }
    }
}

