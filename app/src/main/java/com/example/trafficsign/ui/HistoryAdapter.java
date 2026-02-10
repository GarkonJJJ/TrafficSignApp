package com.example.trafficsign.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.trafficsign.databinding.ItemHistoryBinding;
import com.example.trafficsign.model.HistoryItem;

import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private final List<HistoryItem> data = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public void submitList(List<HistoryItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHistoryBinding vb = ItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(vb);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryItem it = data.get(position);
        h.vb.txtTime.setText(sdf.format(new Date(it.timestamp)));
        h.vb.txtInfo.setText("ID: " + it.id);

        Glide.with(h.itemView)
                .load(it.imagePath)
                .centerCrop()
                .into(h.vb.imgThumb);
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ItemHistoryBinding vb;
        VH(ItemHistoryBinding vb) { super(vb.getRoot()); this.vb = vb; }
    }
}
