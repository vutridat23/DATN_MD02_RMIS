package com.ph48845.datn_qlnh_rmis.data.remote;




import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;


/**
 * RetrofitClient singleton - sử dụng GsonBuilder để đăng ký custom deserializer cho Order.OrderItem
 */
public class RetrofitClient {

    private static final String BASE_URL = "http://192.168.110.85:3000/";

    private static RetrofitClient instance = null;
    private final ApiService apiService;

    private RetrofitClient() {
        // 1. Cấu hình Logging Interceptor để xem request/response trong Logcat
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 2. Tạo OkHttpClient và thêm Interceptor
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        // 3. Tạo Gson với custom deserializer cho Order.OrderItem
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Order.OrderItem.class, new OrderItemDeserializer())
                .create();

        // 4. Khởi tạo Retrofit với Gson đã cấu hình
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build();

        // 5. Tạo đối tượng Service
        apiService = retrofit.create(ApiService.class);
    }

    /**
     * Phương thức để lấy instance duy nhất của RetrofitClient (Singleton)
     */
    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    /**
     * Phương thức để lấy đối tượng ApiService
     */
    public ApiService getApiService() {
        return apiService;
    }
}