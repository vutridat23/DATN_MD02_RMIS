# Hướng dẫn Debug Popup Thông Báo Yêu Cầu Tạm Tính

## Vấn đề
Popup thông báo yêu cầu tạm tính mới không xuất hiện.

## Các bước kiểm tra

### 1. Kiểm tra Socket có kết nối được không

**Xem Logcat với filter:** `ThuNganActivity` hoặc `SocketManager`

**Log cần tìm:**
- ✅ `Socket: ✅ Connected successfully` - Socket đã kết nối
- ❌ `Socket: ❌ Error occurred` - Socket lỗi
- ❌ `xhr poll error` - Socket không kết nối được đến server

**Nếu socket chưa kết nối:**
- Kiểm tra server có đang chạy không
- Kiểm tra URL socket có đúng không (phải giống với API URL: `http://192.168.0.104:3000`)
- Xem logcat để biết URL đang dùng: `Using API BASE_URL for socket: ...`

### 2. Kiểm tra Socket có nhận được events không

**Xem Logcat với filter:** `ThuNganActivity`

**Log cần tìm khi có order mới:**
- `Socket: onOrderCreated received - payload: {...}`
- `Socket: onOrderUpdated received - payload: {...}`

**Nếu không thấy log này:**
- Socket có thể chưa kết nối
- Hoặc server không emit events `order_created`/`order_updated`

### 3. Kiểm tra Logic phát hiện yêu cầu mới

**Xem Logcat với filter:** `checkForNewTempCalculationRequest`

**Log cần tìm:**
- `checkForNewTempCalculationRequest: Checking payload: {...}`
- `checkForNewTempCalculationRequest: Order ID: ...`
- `checkForNewTempCalculationRequest: tempCalculationRequestedAt=..., orderStatus=...`
- `checkForNewTempCalculationRequest: ✅ New temp calculation request detected!` - **QUAN TRỌNG**

**Nếu không thấy log "New temp calculation request detected":**
- Kiểm tra payload có `tempCalculationRequestedAt` không
- Kiểm tra `orderStatus` có phải `temp_bill_printed` không (nếu có thì sẽ không hiển thị)

### 4. Kiểm tra Popup có được gọi không

**Xem Logcat với filter:** `showTempCalculationNotification`

**Log cần tìm:**
- `showTempCalculationNotification: Called with tableNumber=..., orderId=...`
- `showTempCalculationNotification: Showing dialog`
- `showTempCalculationNotification: Progress bar animation started`

**Nếu không thấy log này:**
- Method `showTempCalculationNotification` chưa được gọi
- Có thể do logic phát hiện yêu cầu mới chưa đúng

### 5. Kiểm tra Layout có tồn tại không

**File cần có:** `app/src/main/res/layout/dialog_temp_calc_notification.xml`

**Nếu file không tồn tại:**
- Popup sẽ crash khi cố gắng inflate layout
- Xem logcat có lỗi `ResourceNotFoundException` không

## Cách test thủ công

### Test 1: Tạo yêu cầu tạm tính mới

1. Mở màn hình **Phục vụ** hoặc **Bếp**
2. Tạo order mới hoặc cập nhật order có sẵn
3. Yêu cầu tạm tính cho order đó
4. Quay lại màn hình **Thu Ngân**
5. Xem logcat để kiểm tra các log ở trên

### Test 2: Kiểm tra danh sách known orders

**Xem Logcat:**
- `checkForNewTempCalculationRequest: Known order IDs count: X`
- `loadTempCalculationRequestsCount: ...`

**Nếu count = 0:**
- Có thể chưa có orders nào có yêu cầu tạm tính
- Hoặc `loadTempCalculationRequestsCount()` chưa được gọi

### Test 3: Kiểm tra socket payload

Khi có order được tạo/cập nhật, xem logcat:
```
Socket: onOrderCreated received - payload: {"_id":"...","tableNumber":2,"tempCalculationRequestedAt":"2025-12-22T...","orderStatus":"..."}
```

**Kiểm tra:**
- `tempCalculationRequestedAt` có giá trị không null và không rỗng
- `orderStatus` không phải `"temp_bill_printed"`

## Các vấn đề thường gặp

### 1. Socket chưa kết nối
**Triệu chứng:** Logcat hiển thị `xhr poll error` hoặc `Socket: ❌ Error occurred`
**Giải pháp:** 
- Kiểm tra server có chạy không
- Kiểm tra URL socket có đúng không
- Xem file `RetrofitClient.java` để biết BASE_URL

### 2. Payload không có đầy đủ thông tin
**Triệu chứng:** Logcat hiển thị `checkForNewTempCalculationRequest: No order ID in payload`
**Giải pháp:**
- Kiểm tra backend có emit đầy đủ thông tin order không
- Backend cần emit cả `tempCalculationRequestedAt` trong payload

### 3. Order đã có trong danh sách known
**Triệu chứng:** Logcat hiển thị `checkForNewTempCalculationRequest: Order ... already known, skipping`
**Giải pháp:**
- Đây là hành vi đúng - chỉ hiển thị popup cho yêu cầu MỚI
- Để test lại, cần tạo yêu cầu tạm tính mới cho order khác

### 4. Layout không tồn tại
**Triệu chứng:** App crash khi cố gắng hiển thị popup
**Giải pháp:**
- Đảm bảo file `dialog_temp_calc_notification.xml` tồn tại
- Rebuild project

## Log mẫu khi hoạt động đúng

```
Socket: ✅ Connected successfully
Socket: Connected, initializing known temp calc request order IDs
loadTempCalculationRequestsCount: ...
checkForNewTempCalculationRequest: Checking payload: {...}
checkForNewTempCalculationRequest: Order ID: 6948c5295fe1778bc9024627
checkForNewTempCalculationRequest: tempCalculationRequestedAt=2025-12-22T13:12:47.018Z, orderStatus=temp_calculation
checkForNewTempCalculationRequest: ✅ New temp calculation request detected! Order: 6948c5295fe1778bc9024627, Table: 2
showTempCalculationNotification: Called with tableNumber=2, orderId=6948c5295fe1778bc9024627
showTempCalculationNotification: Showing dialog
showTempCalculationNotification: Progress bar animation started
```

## Nếu vẫn không hoạt động

1. **Kiểm tra logcat đầy đủ** với filter `ThuNganActivity|SocketManager|checkForNewTempCalculationRequest|showTempCalculationNotification`
2. **Chụp screenshot logcat** và gửi cho tôi
3. **Kiểm tra server** có emit events `order_created`/`order_updated` không
4. **Test với order mới** - tạo order hoàn toàn mới và yêu cầu tạm tính


