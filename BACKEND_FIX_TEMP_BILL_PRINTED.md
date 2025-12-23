# Hướng dẫn sửa Backend để hỗ trợ `temp_bill_printed` status

## Vấn đề hiện tại

Backend trả về lỗi khi client cố gắng set `orderStatus = "temp_bill_printed"`:
```
Validation failed: orderStatus: `temp_bill_printed` is not a valid enum value for path `orderStatus`.
```

## Giải pháp

Cần thêm `temp_bill_printed` vào enum `orderStatus` trong MongoDB schema.

---

## Bước 1: Sửa Order Schema (models/Order.js hoặc tương tự)

Tìm file định nghĩa Order model (thường là `models/Order.js` hoặc `models/order.model.js`).

### Tìm enum orderStatus hiện tại:

```javascript
// Ví dụ hiện tại có thể là:
orderStatus: {
  type: String,
  enum: ['pending', 'paid', 'cancelled', 'temp_calculation'],
  default: 'pending'
}
```

### Sửa thành:

```javascript
orderStatus: {
  type: String,
  enum: ['pending', 'paid', 'cancelled', 'temp_calculation', 'temp_bill_printed'],
  default: 'pending'
}
```

**Lưu ý:** Thêm `'temp_bill_printed'` vào mảng enum.

---

## Bước 2: Kiểm tra Controller (controllers/order.controller.js)

Đảm bảo controller không có logic nào chặn giá trị `temp_bill_printed`.

### Ví dụ controller update order:

```javascript
// controllers/order.controller.js
exports.updateOrder = async (req, res) => {
  try {
    const { id } = req.params;
    const updates = req.body;
    
    // Cho phép cập nhật orderStatus, bao gồm temp_bill_printed
    const order = await Order.findByIdAndUpdate(
      id,
      updates,
      { new: true, runValidators: true }
    );
    
    if (!order) {
      return res.status(404).json({
        success: false,
        message: 'Order not found'
      });
    }
    
    res.json({
      success: true,
      data: order
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: 'Lỗi khi cập nhật order',
      error: error.message
    });
  }
};
```

**Lưu ý:** 
- `runValidators: true` sẽ đảm bảo enum validation hoạt động
- Không cần thêm logic đặc biệt cho `temp_bill_printed`, chỉ cần thêm vào enum là đủ

---

## Bước 3: Kiểm tra Routes (routes/orders.js hoặc tương tự)

Đảm bảo route PUT/PATCH cho phép cập nhật `orderStatus`:

```javascript
// routes/orders.js
router.put('/orders/:id', orderController.updateOrder);
// hoặc
router.patch('/orders/:id', orderController.updateOrder);
```

---

## Bước 4: Test sau khi sửa

### Test 1: Cập nhật orderStatus thành temp_bill_printed

**Request:**
```http
PUT http://your-server:3000/orders/69484ebfb060690c5d306f80
Content-Type: application/json

{
  "orderStatus": "temp_bill_printed",
  "tempCalculationRequestedAt": null,
  "tempCalculationRequestedBy": null
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "_id": "69484ebfb060690c5d306f80",
    "orderStatus": "temp_bill_printed",
    "tempCalculationRequestedAt": null,
    "tempCalculationRequestedBy": null,
    ...
  }
}
```

### Test 2: Query orders với temp_bill_printed status

**Request:**
```http
GET http://your-server:3000/orders
```

**Expected:** Orders với `orderStatus: "temp_bill_printed"` phải được trả về trong response.

---

## Bước 5: Migration (nếu cần)

Nếu bạn muốn giữ dữ liệu cũ, không cần migration vì chỉ thêm giá trị mới vào enum.

Nếu muốn cập nhật các orders đã in hóa đơn tạm tính trước đó (nếu có), có thể chạy script:

```javascript
// scripts/migrate-temp-bill-printed.js
const mongoose = require('mongoose');
const Order = require('../models/Order');

async function migrate() {
  try {
    await mongoose.connect('mongodb://localhost:27017/your-database');
    
    // Nếu có logic nào đó để xác định orders đã in hóa đơn tạm tính
    // Ví dụ: orders có tempBillPrinted = true nhưng orderStatus chưa phải temp_bill_printed
    const result = await Order.updateMany(
      { 
        tempBillPrinted: true,
        orderStatus: { $ne: 'temp_bill_printed' }
      },
      { 
        $set: { orderStatus: 'temp_bill_printed' }
      }
    );
    
    console.log(`Updated ${result.modifiedCount} orders`);
    process.exit(0);
  } catch (error) {
    console.error('Migration failed:', error);
    process.exit(1);
  }
}

migrate();
```

**Lưu ý:** Script này chỉ cần chạy nếu bạn muốn cập nhật dữ liệu cũ. Không bắt buộc.

---

## Tóm tắt các file cần sửa

1. ✅ **models/Order.js** - Thêm `'temp_bill_printed'` vào enum `orderStatus`
2. ✅ **controllers/order.controller.js** - Đảm bảo không có logic chặn (thường không cần sửa)
3. ✅ **routes/orders.js** - Đảm bảo route update tồn tại (thường không cần sửa)

---

## Sau khi sửa xong

Sau khi backend đã hỗ trợ `temp_bill_printed`, bạn có thể uncomment dòng code trong Android app:

**File:** `app/src/main/java/com/ph48845/datn_qlnh_rmis/ui/thungan/InvoiceActivity.java`

**Tìm dòng:**
```java
// TODO: Khi backend đã thêm "temp_bill_printed" vào enum, uncomment dòng sau:
// updates.put("orderStatus", "temp_bill_printed");
```

**Sửa thành:**
```java
updates.put("orderStatus", "temp_bill_printed");
```

---

## Kiểm tra nhanh

Sau khi sửa, test bằng Postman hoặc curl:

```bash
curl -X PUT http://192.168.1.10:3000/orders/69484ebfb060690c5d306f80 \
  -H "Content-Type: application/json" \
  -d '{
    "orderStatus": "temp_bill_printed",
    "tempCalculationRequestedAt": null,
    "tempCalculationRequestedBy": null
  }'
```

Nếu trả về `200 OK` với order có `orderStatus: "temp_bill_printed"` → ✅ Thành công!

Nếu vẫn lỗi validation → Kiểm tra lại enum trong schema.



