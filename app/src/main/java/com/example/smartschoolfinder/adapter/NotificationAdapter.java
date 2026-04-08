package com.example.smartschoolfinder.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private final List<NotificationItem> data = new ArrayList<>();
    private OnNotificationClickListener onNotificationClickListener;

    public interface OnNotificationClickListener {
        void onClick(NotificationItem item);
    }

    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.onNotificationClickListener = listener;
    }

    public void setData(List<NotificationItem> notifications) {
        data.clear();
        if (notifications != null) data.addAll(notifications);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = data.get(position);
        if (item == null) return;
        holder.tvMessage.setText(messageForType(holder.itemView, item.getType()));
        holder.tvTime.setText(formatTime(item.getCreatedAt()));
        holder.tvMeta.setText(item.getSchoolId() == null || item.getSchoolId().trim().isEmpty()
                ? ""
                : holder.itemView.getContext().getString(R.string.notification_school_meta, item.getSchoolId()));
        holder.tvMeta.setVisibility(holder.tvMeta.getText().length() == 0 ? View.GONE : View.VISIBLE);
        holder.itemView.setAlpha(item.isRead() ? 0.85f : 1f);
        holder.itemView.setOnClickListener(v -> {
            if (onNotificationClickListener != null) {
                onNotificationClickListener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;
        final TextView tvTime;
        final TextView tvMeta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            tvMeta = itemView.findViewById(R.id.tvNotificationMeta);
        }
    }

    private String messageForType(View view, String type) {
        if ("like".equalsIgnoreCase(type)) return view.getContext().getString(R.string.notification_like);
        if ("dislike".equalsIgnoreCase(type)) return view.getContext().getString(R.string.notification_dislike);
        if ("reply".equalsIgnoreCase(type)) return view.getContext().getString(R.string.notification_reply);
        return view.getContext().getString(R.string.notification_unknown);
    }

    private String formatTime(String createdAt) {
        try {
            if (createdAt == null || createdAt.trim().isEmpty()) return "";
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
            Date date = in.parse(createdAt);
            if (date == null) return "";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            return "";
        }
    }
}

