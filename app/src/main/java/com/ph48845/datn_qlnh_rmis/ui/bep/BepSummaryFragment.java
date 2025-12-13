package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.BepSummaryAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Right fragment: aggregated totals (all tables).
 */
public class BepSummaryFragment extends Fragment {

    private RecyclerView rv;
    private BepSummaryAdapter adapter;

    public BepSummaryFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bep_summary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rv = view.findViewById(R.id.recycler_summary);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BepSummaryAdapter(new ArrayList<>());
        rv.setAdapter(adapter);
    }

    /**
     * Update summary list. Accepts a list of SummaryEntry (name, qty, imageUrl).
     */
    public void updateSummary(List<SummaryEntry> summary) {
        adapter.setItems(summary != null ? summary : new ArrayList<>());
    }
}