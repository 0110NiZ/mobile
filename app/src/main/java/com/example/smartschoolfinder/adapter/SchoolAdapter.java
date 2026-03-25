package com.example.smartschoolfinder.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartschoolfinder.R;
import com.example.smartschoolfinder.model.School;

import java.util.ArrayList;
import java.util.List;

public class SchoolAdapter extends RecyclerView.Adapter<SchoolAdapter.SchoolViewHolder> {

    public interface OnSchoolClickListener {
        void onSchoolClick(School school);
    }

    private final List<School> schools = new ArrayList<>();
    private final OnSchoolClickListener listener;

    public SchoolAdapter(OnSchoolClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<School> data) {
        schools.clear();
        schools.addAll(data);
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
        holder.tvName.setText(school.getName());
        holder.tvDistrict.setText(school.getDistrict());
        holder.tvType.setText(school.getType());
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

        public SchoolViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSchoolName);
            tvDistrict = itemView.findViewById(R.id.tvSchoolDistrict);
            tvType = itemView.findViewById(R.id.tvSchoolType);
        }
    }
}
