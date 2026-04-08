package com.example.smartschoolfinder.adapter;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.utils.SchoolDisplayUtils;

import java.util.ArrayList;
import java.util.List;

public class SchoolAdapter extends RecyclerView.Adapter<SchoolAdapter.SchoolViewHolder> {

    public interface OnSchoolClickListener {
        void onSchoolClick(School school);
    }

    private final List<School> schools = new ArrayList<>();
    private final OnSchoolClickListener listener;
    private boolean showDistance = true;

    public SchoolAdapter(OnSchoolClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<School> data) {
        schools.clear();
        if (data != null) {
            schools.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void setShowDistance(boolean showDistance) {
        this.showDistance = showDistance;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SchoolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_school, parent, false);
        return new SchoolViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SchoolViewHolder holder, int position) {
        School school = schools.get(position);
        holder.tvName.setText(SchoolDisplayUtils.displayName(holder.itemView.getContext(), school));
        holder.tvDistrict.setText(SchoolDisplayUtils.displayDistrict(holder.itemView.getContext(), school));
        holder.tvType.setText(SchoolDisplayUtils.displayType(holder.itemView.getContext(), school));
        holder.tvAddress.setText(SchoolDisplayUtils.displayAddress(holder.itemView.getContext(), school));
        double distanceInKm = school.getDistance();
        if (!showDistance || Double.isNaN(distanceInKm) || distanceInKm < 0) {
            holder.tvDistance.setVisibility(View.GONE);
        } else {
            holder.tvDistance.setVisibility(View.VISIBLE);
            holder.tvDistance.setText(holder.itemView.getContext().getString(
                    R.string.distance_label, distanceInKm));
        }
        holder.itemView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.96f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
            }
            return false;
        });
        holder.itemView.setOnClickListener(v -> listener.onSchoolClick(school));
    }

    @Override
    public int getItemCount() {
        return schools.size();
    }

    static class SchoolViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvDistrict;
        TextView tvType;
        TextView tvDistance;
        TextView tvAddress;

        public SchoolViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSchoolName);
            tvDistrict = itemView.findViewById(R.id.tvSchoolDistrict);
            tvType = itemView.findViewById(R.id.tvSchoolType);
            tvDistance = itemView.findViewById(R.id.tvSchoolDistance);
            tvAddress = itemView.findViewById(R.id.tvSchoolAddress);
        }
    }
}
