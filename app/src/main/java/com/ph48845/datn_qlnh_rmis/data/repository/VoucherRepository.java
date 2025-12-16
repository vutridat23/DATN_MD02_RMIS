package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ph48845.datn_qlnh_rmis.data.model.Voucher;
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
 * Repository để lấy danh sách Voucher từ server
 */
public class VoucherRepository {

    private static final String TAG = "VoucherRepository";
    private final Gson gson = new Gson();

    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    /**
     * Lấy tất cả vouchers (có thể filter theo status)
     */
    public void getAllVouchers(String status, final RepositoryCallback<List<Voucher>> callback) {
        Call<ApiResponse<List<Voucher>>> call = RetrofitClient.getInstance().getApiService().getAllVouchers(status);
        call.enqueue(new Callback<ApiResponse<List<Voucher>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Voucher>>> call, Response<ApiResponse<List<Voucher>>> response) {
                try {
                    if (response.isSuccessful()) {
                        ApiResponse<List<Voucher>> apiResponse = response.body();
                        if (apiResponse != null) {
                            List<Voucher> vouchers = apiResponse.getData();
                            if (vouchers != null) {
                                Log.d(TAG, "getAllVouchers success: count=" + vouchers.size());
                                if (!vouchers.isEmpty()) {
                                    Log.d(TAG, "First voucher: " + vouchers.get(0).toString());
                                }
                                // Lọc chỉ lấy các voucher active và hợp lệ
                                List<Voucher> validVouchers = new ArrayList<>();
                                for (Voucher v : vouchers) {
                                    if (v != null) {
                                        // Không filter theo isValid() ở đây, để user thấy tất cả
                                        // Chỉ filter những voucher null
                                        validVouchers.add(v);
                                    }
                                }
                                Log.d(TAG, "getAllVouchers: " + validVouchers.size() + " valid vouchers");
                                callback.onSuccess(validVouchers);
                                return;
                            } else {
                                // wrapper present but data null - try to read raw string
                                Log.w(TAG, "getAllVouchers: wrapper present but data null, message=" + apiResponse.getMessage());
                            }
                        } else {
                            Log.w(TAG, "getAllVouchers: response.body() == null");
                        }

                        // Try fallback: parse raw response body (server may return raw list)
                        try {
                            ResponseBody rb = response.raw().body();
                            if (rb != null) {
                                String raw = rb.string();
                                Log.d(TAG, "getAllVouchers raw body (fallback) = " + raw);
                                
                                // Try parse as ApiResponse first
                                try {
                                    Type apiResponseType = new TypeToken<ApiResponse<List<Voucher>>>() {}.getType();
                                    ApiResponse<List<Voucher>> altApiResp = gson.fromJson(raw, apiResponseType);
                                    if (altApiResp != null && altApiResp.getData() != null) {
                                        Log.d(TAG, "Parsed ApiResponse from raw body, count=" + altApiResp.getData().size());
                                        callback.onSuccess(altApiResp.getData());
                                        return;
                                    }
                                } catch (Exception ignored) {}
                                
                                // Try parse as direct list
                                Type listType = new TypeToken<List<Voucher>>() {}.getType();
                                List<Voucher> alt = gson.fromJson(raw, listType);
                                if (alt != null && !alt.isEmpty()) {
                                    Log.d(TAG, "Parsed voucher list from raw body, count=" + alt.size());
                                    callback.onSuccess(alt);
                                    return;
                                }
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Failed to parse raw response body: " + ex.getMessage(), ex);
                        }

                        callback.onError("Server returned no vouchers");
                        return;
                    }

                    // Error response - try to parse errorBody as data (some servers return data in errorBody)
                    String err = "HTTP " + response.code() + " " + response.message();
                    try {
                        ResponseBody eb = response.errorBody();
                        if (eb != null) {
                            String ebStr = eb.string();
                            err += " - " + ebStr;
                            Log.w(TAG, "getAllVouchers errorBody: " + ebStr);
                            
                            // Try parse errorBody as list (some servers return data in errorBody on non-2xx)
                            try {
                                Type listType = new TypeToken<List<Voucher>>() {}.getType();
                                List<Voucher> alt = gson.fromJson(ebStr, listType);
                                if (alt != null && !alt.isEmpty()) {
                                    Log.d(TAG, "Parsed voucher list from errorBody, count=" + alt.size());
                                    callback.onSuccess(alt);
                                    return;
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (IOException ignored) {}

                    Log.e(TAG, "getAllVouchers failed: " + err);
                    callback.onError(err);
                } catch (Exception e) {
                    Log.e(TAG, "Exception handling getAllVouchers response", e);
                    callback.onError("Response handling error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Voucher>>> call, Throwable t) {
                Log.e(TAG, "getAllVouchers onFailure", t);
                if (t != null) {
                    Log.e(TAG, "Failure details: " + t.getClass().getName() + " - " + t.getMessage());
                    if (t.getCause() != null) {
                        Log.e(TAG, "Cause: " + t.getCause().getMessage());
                    }
                }
                callback.onError(t != null && t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Lấy voucher theo code
     */
    public void getVoucherByCode(String code, final RepositoryCallback<Voucher> callback) {
        if (code == null || code.trim().isEmpty()) {
            callback.onError("Voucher code is required");
            return;
        }

        Call<ApiResponse<Voucher>> call = RetrofitClient.getInstance().getApiService().getVoucherByCode(code.trim());
        call.enqueue(new Callback<ApiResponse<Voucher>>() {
            @Override
            public void onResponse(Call<ApiResponse<Voucher>> call, Response<ApiResponse<Voucher>> response) {
                try {
                    if (response.isSuccessful()) {
                        ApiResponse<Voucher> apiResponse = response.body();
                        if (apiResponse != null && apiResponse.getData() != null) {
                            Voucher voucher = apiResponse.getData();
                            if (voucher.isValid()) {
                                Log.d(TAG, "getVoucherByCode success: " + voucher.getCode());
                                callback.onSuccess(voucher);
                            } else {
                                callback.onError("Voucher không hợp lệ hoặc đã hết hạn");
                            }
                            return;
                        }
                    }

                    String err = "HTTP " + response.code() + " " + response.message();
                    try {
                        ResponseBody eb = response.errorBody();
                        if (eb != null) {
                            err += " - " + eb.string();
                        }
                    } catch (IOException ignored) {}

                    Log.e(TAG, "getVoucherByCode failed: " + err);
                    callback.onError("Không tìm thấy voucher với code: " + code);
                } catch (Exception e) {
                    Log.e(TAG, "Exception handling getVoucherByCode response", e);
                    callback.onError("Response handling error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Voucher>> call, Throwable t) {
                Log.e(TAG, "getVoucherByCode onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }
}

