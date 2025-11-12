package com.ph48845.datn_qlnh_rmis.data.repository;


import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * TableRepository - giao tiếp với endpoint /tables...
 *
 * Những điểm chính:
 * - Không dùng alias import (đã sửa).
 * - Khi response.body() == null trên HTTP success, cố gắng đọc raw response (okhttp3.Response) và parse JSON bằng Gson.
 * - Log rõ errorBody để debug.
 */
public class TableRepository {

    private static final String TAG = "TableRepository";
    private final Gson gson = new Gson();

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    /**
     * Lấy tất cả bàn từ server
     */
    public void getAllTables(RepositoryCallback<List<TableItem>> callback) {
        Call<ApiResponse<List<TableItem>>> call = RetrofitClient.getInstance().getApiService().getAllTables();
        call.enqueue(new Callback<ApiResponse<List<TableItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<TableItem>>> call, Response<ApiResponse<List<TableItem>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<TableItem>> apiResp = response.body();
                    List<TableItem> items = apiResp.getData();
                    if (items == null) items = new ArrayList<>();

                    // normalize each item (map statusRaw -> enum, null checks)
                    for (TableItem t : items) {
                        if (t != null) t.normalize();
                    }

                    callback.onSuccess(items);
                } else {
                    String msg = "Server error: " + response.code();
                    try {
                        if (response.errorBody() != null) msg += " - " + response.errorBody().string();
                    } catch (Exception ignored) {}
                    Log.e(TAG, "onResponse error: " + msg);
                    callback.onError(msg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<TableItem>>> call, Throwable t) {
                Log.e(TAG, "onFailure getAllTables", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Cập nhật một hoặc nhiều field của table (partial update) như status.
     * newStatus: ví dụ "occupied", "reserved", "available", ...
     *
     * NOTE: Một số backend trả trực tiếp TableItem, nhưng có server khác trả wrapper ApiResponse<TableItem>.
     * Method này cố gắng xử lý cả hai dạng (best-effort) mà không gây lỗi generic.
     */
    public void updateTableStatus(String tableId, String newStatus, RepositoryCallback<TableItem> callback) {
        if (tableId == null || tableId.trim().isEmpty()) {
            callback.onError("Invalid table id");
            return;
        }

        Map<String, Object> body = Collections.singletonMap("status", newStatus);

        // Use same generic type as ApiService.updateTable signature (TableItem)
        Call<TableItem> call = RetrofitClient.getInstance().getApiService().updateTable(tableId, body);

        call.enqueue(new Callback<TableItem>() {
            @Override
            public void onResponse(Call<TableItem> call, Response<TableItem> response) {
                try {
                    if (response.isSuccessful()) {
                        TableItem t = response.body();
                        if (t != null) {
                            t.normalize();
                            callback.onSuccess(t);
                            return;
                        }

                        // Success but body == null: try to read raw response body as string and parse
                        String raw = null;
                        try {
                            // Use fully-qualified okhttp3.Response to avoid import conflict with retrofit2.Response
                            okhttp3.Response rawResp = response.raw();
                            if (rawResp != null && rawResp.body() != null) {
                                raw = rawResp.body().string();
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Failed to read raw response body: " + ex.getMessage());
                        }

                        if (raw == null || raw.isEmpty()) {
                            String msg = "Empty body on success (HTTP " + response.code() + ")";
                            Log.w(TAG, "updateTableStatus empty body: " + msg);
                            callback.onError(msg);
                            return;
                        }

                        // Try parse as ApiResponse<TableItem>
                        try {
                            Type wrapperType = new TypeToken<ApiResponse<TableItem>>() {}.getType();
                            ApiResponse<TableItem> apiResp = gson.fromJson(raw, wrapperType);
                            if (apiResp != null && apiResp.getData() != null) {
                                TableItem parsed = apiResp.getData();
                                parsed.normalize();
                                callback.onSuccess(parsed);
                                return;
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Cannot parse raw as ApiResponse<TableItem>: " + ex.getMessage());
                        }

                        // Try parse raw JSON object into TableItem directly
                        try {
                            TableItem parsedDirect = gson.fromJson(raw, TableItem.class);
                            if (parsedDirect != null) {
                                parsedDirect.normalize();
                                callback.onSuccess(parsedDirect);
                                return;
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Cannot parse raw as TableItem: " + ex.getMessage());
                        }

                        callback.onError("Empty body on success but cannot parse payload");
                    } else {
                        // not successful - try to extract useful info from errorBody
                        String err = "HTTP " + response.code() + " - " + response.message();
                        String errBodyStr = null;
                        try {
                            ResponseBody eb = response.errorBody();
                            if (eb != null) {
                                errBodyStr = eb.string();
                                err += " - " + errBodyStr;
                            }
                        } catch (IOException ignored) {}

                        // If errorBody is JSON wrapper, try to extract message
                        if (errBodyStr != null) {
                            try {
                                Type wrapperType = new TypeToken<ApiResponse<TableItem>>() {}.getType();
                                ApiResponse<TableItem> apiResp = gson.fromJson(errBodyStr, wrapperType);
                                if (apiResp != null) {
                                    String serverMsg = apiResp.getMessage();
                                    if (serverMsg != null && !serverMsg.isEmpty()) {
                                        err += " | serverMessage: " + serverMsg;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        Log.e(TAG, "updateTableStatus error: " + err);
                        callback.onError(err);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception handling updateTableStatus response", e);
                    callback.onError("Response handling error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<TableItem> call, Throwable t) {
                Log.e(TAG, "updateTableStatus onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Merge tables: try calling backend merge endpoint first; if it fails (non-2xx),
     * fallback to updating statuses: target -> occupied, source -> available (with rollback if needed).
     *
     * fromTableId: table to be merged (source)
     * targetTableId: table that receives merge (destination)
     */
    public void mergeTables(String fromTableId, String targetTableId, RepositoryCallback<TableItem> callback) {
        if (fromTableId == null || fromTableId.trim().isEmpty() || targetTableId == null || targetTableId.trim().isEmpty()) {
            callback.onError("Invalid table ids for merge");
            return;
        }

        // Attempt merge endpoint on server first
        Map<String, String> body = new HashMap<>();
        body.put("fromTableId", fromTableId);

        Call<TableItem> mergeCall = RetrofitClient.getInstance().getApiService().mergeTable(targetTableId, body);
        mergeCall.enqueue(new Callback<TableItem>() {
            @Override
            public void onResponse(Call<TableItem> call, Response<TableItem> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TableItem merged = response.body();
                    merged.normalize();
                    callback.onSuccess(merged);
                } else {
                    // merge endpoint not supported or returned error -> fallback to update statuses
                    String msg = "Merge endpoint error: HTTP " + response.code();
                    try {
                        if (response.errorBody() != null) msg += " - " + response.errorBody().string();
                    } catch (IOException ignored) {}
                    Log.w(TAG, "mergeTables endpoint failed, fallback to status-updates. " + msg);
                    // Fallback behavior: set target -> occupied, then set source -> available
                    performFallbackMerge(fromTableId, targetTableId, callback);
                }
            }

            @Override
            public void onFailure(Call<TableItem> call, Throwable t) {
                Log.w(TAG, "mergeTables call failed, fallback to status-updates", t);
                performFallbackMerge(fromTableId, targetTableId, callback);
            }
        });
    }

    /**
     * Fallback cho merge: set target -> occupied, rồi source -> available.
     * Nếu source update fails thì rollback target -> available.
     */
    private void performFallbackMerge(String fromTableId, String targetTableId, RepositoryCallback<TableItem> callback) {
        updateTableStatus(targetTableId, "occupied", new RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem updatedTarget) {
                updateTableStatus(fromTableId, "available", new RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedSource) {
                        callback.onSuccess(updatedTarget);
                    }

                    @Override
                    public void onError(String message) {
                        // rollback target -> available
                        updateTableStatus(targetTableId, "available", new RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem rollbackTable) {
                                callback.onError("Merge failed: " + message + " (rolled back target)");
                            }

                            @Override
                            public void onError(String rbMessage) {
                                callback.onError("Merge failed: " + message + " ; rollback failed: " + rbMessage);
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError("Cannot set target occupied: " + message);
            }
        });
    }
}