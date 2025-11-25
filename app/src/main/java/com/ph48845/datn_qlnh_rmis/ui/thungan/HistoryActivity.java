package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.adapter.HistoryAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList = new ArrayList<>();
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rvHistory);
        adapter = new HistoryAdapter(historyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        apiService = RetrofitClient.getInstance().getApiService();

        fetchHistory();
    }

    private void fetchHistory() {
        Call<ApiResponse<List<HistoryItem>>> call = apiService.getAllHistory(); // API giả định
        call.enqueue(new Callback<ApiResponse<List<HistoryItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<HistoryItem>>> call, Response<ApiResponse<List<HistoryItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    historyList.clear();
                    historyList.addAll(response.body().getData());
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(HistoryActivity.this, "Không có dữ liệu lịch sử", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<HistoryItem>>> call, Throwable t) {
                Toast.makeText(HistoryActivity.this, "Không thể kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
