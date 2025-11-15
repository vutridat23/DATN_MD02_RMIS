# Hướng dẫn Test màn hình Thu Ngân

## Cách mở màn hình Thu Ngân

### Cách 1: Từ MainActivity (Đã được thêm vào)
1. Mở ứng dụng (MainActivity là màn hình chính)
2. Click vào icon **Avatar** (biểu tượng người) ở góc trên bên phải
3. Chọn **"Thu Ngân"** từ menu popup
4. Màn hình Thu Ngân sẽ được mở

### Cách 2: Sử dụng Intent trực tiếp (Trong code)
```java
Intent intent = new Intent(this, ThuNganActivity.class);
startActivity(intent);
```

### Cách 3: Sử dụng ADB (Để test nhanh)
```bash
adb shell am start -n com.ph48845.datn_qlnh_rmis/.ui.thungan.ThuNganActivity
```

## Điều kiện để test

### 1. Cần có dữ liệu trên server
- **Bàn đang hoạt động**: Cần có ít nhất một bàn với status:
  - `OCCUPIED` (Đã có khách)
  - `PENDING_PAYMENT` (Chờ thanh toán)
  - `FINISH_SERVE` (Đã phục vụ đủ món)

### 2. Cần có Orders trên server
- Để hiển thị trạng thái phục vụ đúng, cần có orders cho các bàn
- Orders cần có `items` với `status` để xác định "Đang phục vụ" hay "Đã phục vụ đủ"

## Các bước test

### Bước 1: Chuẩn bị dữ liệu test
1. **Tạo bàn trên server** với status `occupied`:
```json
{
  "tableNumber": 1,
  "capacity": 4,
  "status": "occupied",
  "location": "Tầng 1"
}
```

2. **Tạo order cho bàn**:
```json
{
  "tableNumber": 1,
  "items": [
    {
      "name": "Phở bò",
      "quantity": 1,
      "price": 60000,
      "status": "done"
    }
  ],
  "totalAmount": 60000,
  "finalAmount": 60000,
  "orderStatus": "pending"
}
```

### Bước 2: Test màn hình Thu Ngân
1. **Mở màn hình Thu Ngân** (theo cách 1 hoặc 2 ở trên)
2. **Kiểm tra**:
   - Toolbar có tiêu đề "Danh sách bàn ăn đang hoạt động"
   - Hiển thị các bàn đang hoạt động theo tầng
   - Bàn có màu xanh lá cây (đang phục vụ) hoặc đỏ (đã phục vụ đủ)
   - Text hiển thị: "Đã có khách" và trạng thái phục vụ

### Bước 3: Test click vào bàn
1. **Click vào một bàn** trong danh sách
2. **Kiểm tra**:
   - Màn hình hóa đơn (InvoiceActivity) được mở
   - Hiển thị đúng thông tin bàn
   - Hiển thị danh sách món ăn và tổng tiền

### Bước 4: Test các trường hợp khác nhau

#### Trường hợp 1: Bàn đang phục vụ (màu xanh)
- Bàn có status `OCCUPIED`
- Orders có items với status `pending` hoặc `preparing`
- Kỳ vọng: Hiển thị màu xanh lá cây, text "Đang phục vụ lên món"

#### Trường hợp 2: Bàn đã phục vụ đủ (màu đỏ)
- Bàn có status `FINISH_SERVE`
- Hoặc tất cả items trong orders có status `done`
- Kỳ vọng: Hiển thị màu đỏ, text "Đã phục vụ đủ món"

#### Trường hợp 3: Bàn chờ thanh toán
- Bàn có status `PENDING_PAYMENT`
- Kỳ vọng: Hiển thị trong danh sách với màu xanh lá cây

#### Trường hợp 4: Không có bàn đang hoạt động
- Tất cả bàn có status `available` hoặc `empty`
- Kỳ vọng: Không hiển thị bàn nào (hoặc hiển thị thông báo)

## Test với mock data (Nếu không có server)

Nếu bạn muốn test mà không cần server, có thể tạo một test activity hoặc sử dụng mock data:

### Tạo mock data trong ThuNganActivity:
```java
// Trong loadActiveTables(), thêm mock data để test
private void loadActiveTablesWithMockData() {
    List<TableItem> mockTables = new ArrayList<>();
    
    // Mock bàn đang hoạt động
    TableItem table1 = new TableItem();
    table1.setId("mock1");
    table1.setTableNumber(1);
    table1.setLocation("Tầng 1");
    table1.setStatus(TableItem.Status.OCCUPIED);
    mockTables.add(table1);
    
    TableItem table2 = new TableItem();
    table2.setId("mock2");
    table2.setTableNumber(5);
    table2.setLocation("Tầng 1");
    table2.setStatus(TableItem.Status.FINISH_SERVE);
    mockTables.add(table2);
    
    // Phân chia theo tầng và cập nhật adapter
    List<TableItem> floor1 = new ArrayList<>();
    List<TableItem> floor2 = new ArrayList<>();
    
    for (TableItem table : mockTables) {
        if (table.getLocation().contains("1")) {
            floor1.add(table);
        } else {
            floor2.add(table);
        }
    }
    
    adapterFloor1.updateList(floor1);
    adapterFloor2.updateList(floor2);
}
```

## Troubleshooting

### Lỗi: Không hiển thị bàn nào
- **Nguyên nhân**: Không có bàn nào có status `OCCUPIED`, `PENDING_PAYMENT`, hoặc `FINISH_SERVE`
- **Giải pháp**: Tạo bàn với status phù hợp trên server

### Lỗi: Màn hình trống hoặc crash
- **Nguyên nhân**: API không trả về dữ liệu hoặc lỗi network
- **Giải pháp**: 
  - Kiểm tra kết nối mạng
  - Kiểm tra API endpoint
  - Kiểm tra logcat để xem lỗi cụ thể

### Lỗi: Click vào bàn không mở hóa đơn
- **Nguyên nhân**: Intent không đúng hoặc InvoiceActivity chưa được đăng ký
- **Giải pháp**: Kiểm tra AndroidManifest.xml đã đăng ký InvoiceActivity

## Log để debug

Để debug, kiểm tra logcat với tag `ThuNganActivity`:
```bash
adb logcat | grep ThuNganActivity
```

Các log quan trọng:
- `Loaded tables from server`: Số lượng bàn đã load
- `Error loading tables`: Lỗi khi load bàn
- `Error loading orders for serving status`: Lỗi khi load orders

## Checklist test

- [ ] Màn hình Thu Ngân mở được từ MainActivity
- [ ] Hiển thị danh sách bàn đang hoạt động
- [ ] Bàn được phân chia theo tầng (TẦNG 1, TẦNG 2)
- [ ] Bàn có màu đúng (xanh = đang phục vụ, đỏ = đã phục vụ đủ)
- [ ] Click vào bàn mở được màn hình hóa đơn
- [ ] Toolbar có nút back hoạt động
- [ ] Bottom navigation bar hiển thị
- [ ] Refresh khi quay lại màn hình (onResume)
- [ ] Xử lý trường hợp không có dữ liệu
- [ ] Xử lý lỗi network/API

