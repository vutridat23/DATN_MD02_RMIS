package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository dùng Retrofit để lấy danh sách MenuItem.
 * - Thêm logging để debug server response.
 * - Cố gắng parse alternative response formats nếu wrapper khác.
 */
public class MenuRepository {

    private static final String TAG = "MenuRepository";
    private final Gson gson = new Gson();

    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    public void getAllMenuItems(final RepositoryCallback<List<MenuItem>> callback) {
        Call<ApiResponse<List<MenuItem>>> call = RetrofitClient.getInstance().getApiService().getAllMenuItems();
        call.enqueue(new Callback<ApiResponse<List<MenuItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<MenuItem>>> call, Response<ApiResponse<List<MenuItem>>> response) {
                try {
                    if (response.isSuccessful()) {
                        ApiResponse<List<MenuItem>> apiResponse = response.body();
                        if (apiResponse != null) {
                            List<MenuItem> items = apiResponse.getData();
                            if (items != null) {
                                Log.d(TAG, "getAllMenuItems success: count=" + items.size());
                                if (!items.isEmpty()) Log.d(TAG, "first item: " + items.get(0).toString());
                                callback.onSuccess(items);
                                return;
                            } else {
                                // wrapper present but data null - try to read raw string (server may return data without wrapper)
                                Log.w(TAG, "getAllMenuItems: wrapper present but data null, message=" + apiResponse.getMessage());
                            }
                        } else {
                            Log.w(TAG, "getAllMenuItems: response.body() == null");
                        }

                        // Try fallback: try to parse raw response body (some APIs might return raw list)
                        try {
                            ResponseBody rb = response.raw().body();
                            if (rb != null) {
                                String raw = rb.string();
                                Log.d(TAG, "getAllMenuItems raw body (fallback) = " + raw);
                                Type listType = new TypeToken<List<MenuItem>>() {}.getType();
                                List<MenuItem> alt = gson.fromJson(raw, listType);
                                if (alt != null) {
                                    Log.d(TAG, "Parsed alt menu list from raw body, count=" + alt.size());
                                    callback.onSuccess(alt);
                                    return;
                                }
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Failed to parse raw response body: " + ex.getMessage());
                        }

                        // no usable data found
                        callback.onError("Server returned no menu items");
                        return;
                    }

                    // not successful - try to capture errorBody and possible alt formats
                    String err = "HTTP " + response.code() + " " + response.message();
                    try {
                        ResponseBody eb = response.errorBody();
                        if (eb != null) {
                            String ebStr = eb.string();
                            err += " - " + ebStr;
                            Log.w(TAG, "getAllMenuItems errorBody: " + ebStr);

                            // Try parse errorBody as direct list
                            Type listType = new TypeToken<List<MenuItem>>() {}.getType();
                            try {
                                List<MenuItem> alt = gson.fromJson(ebStr, listType);
                                if (alt != null) {
                                    Log.d(TAG, "Parsed menu list from errorBody alt format, count=" + alt.size());
                                    callback.onSuccess(alt);
                                    return;
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (IOException ignored) {}

                    Log.e(TAG, "getAllMenuItems failed: " + err);
                    callback.onError(err);
                } catch (Exception e) {
                    Log.e(TAG, "Exception handling getAllMenuItems response", e);
                    callback.onError("Response handling error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<MenuItem>>> call, Throwable t) {
                Log.e(TAG, "getAllMenuItems onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }
}