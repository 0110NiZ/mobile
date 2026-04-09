package com.example.smartschoolfinder.ui;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.adapter.NotificationAdapter;
import com.example.smartschoolfinder.data.NotificationRepository;
import com.example.smartschoolfinder.model.NotificationItem;
import com.example.smartschoolfinder.model.NotificationListResponse;
import com.example.smartschoolfinder.network.ApiCallback;
import com.example.smartschoolfinder.utils.DeviceUserIdManager;

public class NotificationsActivity extends AppCompatActivity {
    private NotificationRepository repository;
    private NotificationAdapter adapter;
    private String deviceUserId;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        repository = new NotificationRepository(this);

        deviceUserId = DeviceUserIdManager.getOrCreate(this);
        RecyclerView recycler = findViewById(R.id.recyclerNotifications);
        tvEmpty = findViewById(R.id.tvNotificationsEmpty);
        View btnBack = findViewById(R.id.btnBackNotifications);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        adapter = new NotificationAdapter();
        adapter.setOnNotificationClickListener(this::openNotificationTarget);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        repository.getNotifications(deviceUserId, new ApiCallback<NotificationListResponse>() {
            @Override
            public void onSuccess(NotificationListResponse data) {
                if (data == null || data.getNotifications() == null || data.getNotifications().isEmpty()) {
                    adapter.setData(null);
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    adapter.setData(data.getNotifications());
                    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                }
                repository.markRead(deviceUserId, new ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean ignored) {
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openNotificationTarget(NotificationItem item) {
        if (item == null) return;
        String schoolId = item.getSchoolId() == null ? "" : item.getSchoolId().trim();
        if (schoolId.isEmpty()) return;
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("school_id", schoolId);
        String commentId = item.getCommentId() == null ? "" : item.getCommentId().trim();
        if (!commentId.isEmpty()) {
            intent.putExtra("focus_comment_id", commentId);
        }
        startActivity(intent);
    }
}

