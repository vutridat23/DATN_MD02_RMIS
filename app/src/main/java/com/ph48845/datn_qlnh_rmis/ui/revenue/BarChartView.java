package com.ph48845.datn_qlnh_rmis.ui.revenue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom View để vẽ biểu đồ cột hiển thị doanh thu theo ngày trong tuần.
 */
public class BarChartView extends View {

    private static final String TAG = "BarChartView";
    
    // Màu sắc
    private static final int COLOR_BAR_DARK = 0xFF2BB673; // Xanh lá cây đậm
    private static final int COLOR_BAR_LIGHT = 0xFF7ED4A3; // Xanh lá cây nhạt
    private static final int COLOR_GRID_LINE = 0xFFE0E0E0; // Xám nhạt cho đường lưới
    private static final int COLOR_TEXT = 0xFF000000; // Đen cho text
    private static final int COLOR_AXIS = 0xFF808080; // Xám cho trục
    
    // Dữ liệu
    private List<BarData> barDataList = new ArrayList<>();
    private double maxValue = 0;
    
    // Paint objects
    private Paint barPaintDark;
    private Paint barPaintLight;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint axisPaint;
    
    // Kích thước và padding
    private float paddingLeft = 60f;
    private float paddingRight = 20f;
    private float paddingTop = 40f;
    private float paddingBottom = 60f;
    private float barWidth;
    private float barSpacing;
    
    // Labels
    private String[] dayLabels = {"M", "T", "W", "T", "F", "S", "Today"};
    private String[] valueLabels = {"$0", "$5tr", "$10tr"};
    
    // Dynamic value labels based on max value
    private void updateValueLabels() {
        if (maxValue <= 0) {
            valueLabels = new String[]{"$0", "$5tr", "$10tr"};
            return;
        }
        
        // Tính toán giá trị cho labels
        double step = maxValue / 2; // 3 labels: 0, step, maxValue
        valueLabels = new String[]{
            "$0",
            String.format(Locale.getDefault(), "$%.1ftr", step),
            String.format(Locale.getDefault(), "$%.1ftr", maxValue)
        };
    }

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Khởi tạo Paint cho cột đậm
        barPaintDark = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaintDark.setColor(COLOR_BAR_DARK);
        barPaintDark.setStyle(Paint.Style.FILL);
        
        // Khởi tạo Paint cho cột nhạt
        barPaintLight = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaintLight.setColor(COLOR_BAR_LIGHT);
        barPaintLight.setStyle(Paint.Style.FILL);
        
        // Khởi tạo Paint cho đường lưới
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(COLOR_GRID_LINE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{5f, 5f}, 0f));
        
        // Khởi tạo Paint cho text
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        // Khởi tạo Paint cho trục
        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(COLOR_AXIS);
        axisPaint.setStrokeWidth(2f);
        
        // Dữ liệu mẫu (sẽ được cập nhật từ Activity)
        setupSampleData();
    }

    /**
     * Thiết lập dữ liệu mẫu (sẽ bị ghi đè bởi setData)
     */
    private void setupSampleData() {
        barDataList.clear();
        barDataList.add(new BarData(2.5, false)); // M - nhạt
        barDataList.add(new BarData(5.5, true));  // T - đậm
        barDataList.add(new BarData(2.5, false)); // W - nhạt
        barDataList.add(new BarData(6.0, true));  // T - đậm
        barDataList.add(new BarData(5.5, true));  // F - đậm
        barDataList.add(new BarData(3.5, false)); // S - nhạt
        barDataList.add(new BarData(2.5, false)); // Today - nhạt
        maxValue = 10.0;
        invalidate();
    }

    /**
     * Thiết lập dữ liệu cho biểu đồ
     * @param dataList Danh sách giá trị doanh thu (triệu đồng) theo ngày
     */
    public void setData(List<Double> dataList) {
        barDataList.clear();
        maxValue = 0;
        
        if (dataList == null || dataList.isEmpty()) {
            invalidate();
            return;
        }
        
        // Tìm giá trị lớn nhất
        for (Double value : dataList) {
            if (value != null && value > maxValue) {
                maxValue = value;
            }
        }
        
        // Thêm padding để giá trị lớn nhất không sát trên cùng
        maxValue = maxValue * 1.2f;
        if (maxValue < 1) maxValue = 10.0; // Giá trị mặc định
        
        // Cập nhật labels dựa trên maxValue
        updateValueLabels();
        
        // Tạo BarData với màu xen kẽ
        // Màu: M (0) = nhạt, T (1) = đậm, W (2) = nhạt, T (3) = đậm, F (4) = đậm, S (5) = nhạt, Today (6) = nhạt
        boolean[] darkPattern = {false, true, false, true, true, false, false};
        
        for (int i = 0; i < dataList.size() && i < dayLabels.length; i++) {
            Double value = dataList.get(i);
            boolean isDark = i < darkPattern.length ? darkPattern[i] : (i % 2 == 1);
            barDataList.add(new BarData(value != null ? value : 0, isDark));
        }
        
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float width = getWidth();
        float height = getHeight();
        
        if (width <= 0 || height <= 0) return;
        
        // Tính toán kích thước vùng vẽ
        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;
        
        // Tính toán kích thước cột
        int barCount = barDataList.size();
        if (barCount == 0) return;
        
        barWidth = (chartWidth / barCount) * 0.6f; // Chiều rộng cột = 60% khoảng cách
        barSpacing = (chartWidth / barCount) * 0.4f; // Khoảng cách giữa các cột = 40%
        
        // Vẽ đường lưới ngang
        drawGridLines(canvas, chartWidth, chartHeight);
        
        // Vẽ các cột
        drawBars(canvas, chartWidth, chartHeight);
        
        // Vẽ labels cho trục Y (bên trái)
        drawYAxisLabels(canvas, chartHeight);
        
        // Vẽ labels cho trục X (dưới cùng)
        drawXAxisLabels(canvas, chartWidth, chartHeight);
    }

    /**
     * Vẽ đường lưới ngang
     */
    private void drawGridLines(Canvas canvas, float chartWidth, float chartHeight) {
        int gridLineCount = valueLabels.length;
        for (int i = 0; i < gridLineCount; i++) {
            float y = paddingTop + (chartHeight / (gridLineCount - 1)) * i;
            Path path = new Path();
            path.moveTo(paddingLeft, y);
            path.lineTo(paddingLeft + chartWidth, y);
            canvas.drawPath(path, gridPaint);
        }
    }

    /**
     * Vẽ các cột
     */
    private void drawBars(Canvas canvas, float chartWidth, float chartHeight) {
        for (int i = 0; i < barDataList.size(); i++) {
            BarData barData = barDataList.get(i);
            if (barData == null) continue;
            
            // Tính toán vị trí x của cột
            float x = paddingLeft + (barWidth + barSpacing) * i + barSpacing / 2;
            
            // Tính toán chiều cao cột
            float barHeight = (float) (chartHeight * (barData.value / maxValue));
            
            // Tính toán vị trí y của cột (từ dưới lên)
            float y = paddingTop + chartHeight - barHeight;
            
            // Chọn màu
            Paint paint = barData.isDark ? barPaintDark : barPaintLight;
            
            // Vẽ cột với góc bo tròn
            RectF rect = new RectF(x, y, x + barWidth, paddingTop + chartHeight);
            float cornerRadius = 4f;
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }
    }

    /**
     * Vẽ labels cho trục Y (bên trái)
     */
    private void drawYAxisLabels(Canvas canvas, float chartHeight) {
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setColor(COLOR_TEXT);
        
        int labelCount = valueLabels.length;
        for (int i = 0; i < labelCount; i++) {
            float y = paddingTop + (chartHeight / (labelCount - 1)) * i;
            canvas.drawText(valueLabels[i], paddingLeft - 10, y + 10, textPaint);
        }
    }

    /**
     * Vẽ labels cho trục X (dưới cùng)
     */
    private void drawXAxisLabels(Canvas canvas, float chartWidth, float chartHeight) {
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(COLOR_TEXT);
        
        // Đảm bảo luôn có 7 labels: M, T, W, T, F, S, Today
        int labelCount = Math.min(dayLabels.length, 7);
        for (int i = 0; i < labelCount; i++) {
            if (i >= barDataList.size()) {
                // Vẽ label ngay cả khi không có dữ liệu
                float x = paddingLeft + (barWidth + barSpacing) * i + barSpacing / 2 + barWidth / 2;
                float y = paddingTop + chartHeight + 40;
                canvas.drawText(dayLabels[i], x, y, textPaint);
            } else {
                float x = paddingLeft + (barWidth + barSpacing) * i + barSpacing / 2 + barWidth / 2;
                float y = paddingTop + chartHeight + 40;
                canvas.drawText(dayLabels[i], x, y, textPaint);
            }
        }
    }

    /**
     * Class để lưu dữ liệu của một cột
     */
    private static class BarData {
        double value;
        boolean isDark;
        
        BarData(double value, boolean isDark) {
            this.value = value;
            this.isDark = isDark;
        }
    }
}

