# Hướng dẫn sửa Backend để trả về `checkItemsRequestedAt` và `checkItemsRequestedBy`

## Vấn đề
Backend hiện tại **KHÔNG trả về** các field `checkItemsRequestedAt` và `checkItemsRequestedBy` trong response của `GET /orders`, khiến phía Android không thể hiển thị danh sách yêu cầu kiểm tra bàn.

**⚠️ QUAN TRỌNG:** Ngay cả khi backend đã lưu các field này vào database, nếu không SELECT hoặc không include chúng trong response, Android sẽ nhận được `null` cho tất cả orders.

**✅ ĐÃ XÁC NHẬN:** Response từ `GET /orders` hiện tại KHÔNG có field `checkItemsRequestedAt` và `checkItemsRequestedBy` trong bất kỳ order nào, mặc dù có field `tempCalculationRequestedAt` và `tempCalculationRequestedBy`.

## Các file cần sửa

### 1. **Model/Schema Order** (MongoDB Schema)
**File:** `models/Order.js` hoặc `models/order.model.js` hoặc tương tự

**Cần đảm bảo schema có các field:**
```javascript
const orderSchema = new mongoose.Schema({
  // ... các field khác
  checkItemsRequestedAt: {
    type: Date,
    default: null
  },
  checkItemsRequestedBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null
  },
  // ... các field khác
});
```

### 2. **Controller - GET /orders** (Lấy tất cả orders)
**File:** `controllers/order.controller.js` hoặc `routes/orders.js` hoặc tương tự

**⚠️ VẤN ĐỀ:** Hiện tại code có thể đang dùng `.select()` và KHÔNG include `checkItemsRequestedAt` và `checkItemsRequestedBy`, hoặc các field này bị exclude.

**Cần sửa để SELECT các field này:**

**CÁCH 1: Nếu đang dùng `.select()` (chỉ trả về một số field):**
```javascript
// Tìm method getAllOrders hoặc tương tự
exports.getAllOrders = async (req, res) => {
  try {
    const orders = await Order.find({})
      .select('+checkItemsRequestedAt +checkItemsRequestedBy') // ✅ THÊM + để include các field này
      // HOẶC nếu không dùng select, bỏ dòng select đi
      .populate('checkItemsRequestedBy', 'name username') // ✅ Nếu checkItemsRequestedBy là ObjectId
      .sort({ createdAt: -1 });
    
    res.json({
      success: true,
      data: orders,
      message: 'Orders retrieved successfully'
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};
```

**CÁCH 2: Nếu KHÔNG dùng `.select()` (trả về tất cả field):**
```javascript
exports.getAllOrders = async (req, res) => {
  try {
    // ✅ Đảm bảo KHÔNG có .select() hoặc .select() include các field này
    const orders = await Order.find({})
      .populate('checkItemsRequestedBy', 'name username')
      .sort({ createdAt: -1 });
    
    res.json({
      success: true,
      data: orders,
      message: 'Orders retrieved successfully'
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};
```

**CÁCH 3: Nếu schema có `select: false` cho các field này:**
```javascript
// Trong schema, đảm bảo KHÔNG có:
checkItemsRequestedAt: { type: Date, select: false } // ❌ SAI
// Mà phải là:
checkItemsRequestedAt: { type: Date, default: null } // ✅ ĐÚNG
```

**HOẶC nếu dùng `.lean()`:**
```javascript
const orders = await Order.find({})
  .select('+checkItemsRequestedAt +checkItemsRequestedBy') // ✅ THÊM + để include
  .populate('checkItemsRequestedBy', 'name username')
  .lean();
```

### 3. **Controller - GET /orders/:id** (Lấy order theo ID)
**File:** Cùng file với trên

**Cần sửa:**
```javascript
exports.getOrderById = async (req, res) => {
  try {
    const order = await Order.findById(req.params.id)
      .select('checkItemsRequestedAt checkItemsRequestedBy') // ✅ THÊM DÒNG NÀY
      .populate('checkItemsRequestedBy', 'name username');
    
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
      message: error.message
    });
  }
};
```

### 4. **Controller - PUT /orders/:id** (Update order)
**File:** Cùng file với trên

**Cần đảm bảo khi update, các field này được lưu và trả về:**
```javascript
exports.updateOrder = async (req, res) => {
  try {
    const { checkItemsRequestedAt, checkItemsRequestedBy, ...otherUpdates } = req.body;
    
    const updateData = {
      ...otherUpdates
    };
    
    // ✅ Đảm bảo lưu các field này nếu có trong request
    if (checkItemsRequestedAt !== undefined) {
      updateData.checkItemsRequestedAt = checkItemsRequestedAt;
    }
    if (checkItemsRequestedBy !== undefined) {
      updateData.checkItemsRequestedBy = checkItemsRequestedBy;
    }
    
    const order = await Order.findByIdAndUpdate(
      req.params.id,
      updateData,
      { new: true, runValidators: true } // new: true để trả về document đã update
    )
      .select('checkItemsRequestedAt checkItemsRequestedBy') // ✅ THÊM DÒNG NÀY
      .populate('checkItemsRequestedBy', 'name username');
    
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
      message: error.message
    });
  }
};
```

### 5. **Controller - GET /orders với query params** (Lấy orders theo tableNumber)
**File:** Cùng file với trên

**Cần sửa:**
```javascript
exports.getOrdersByTable = async (req, res) => {
  try {
    const { tableNumber, status } = req.query;
    
    const query = {};
    if (tableNumber) query.tableNumber = parseInt(tableNumber);
    if (status) query.status = status;
    
    const orders = await Order.find(query)
      .select('checkItemsRequestedAt checkItemsRequestedBy') // ✅ THÊM DÒNG NÀY
      .populate('checkItemsRequestedBy', 'name username')
      .sort({ createdAt: -1 });
    
    res.json({
      success: true,
      data: orders
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
};
```

## Checklist kiểm tra

- [ ] Schema Order có field `checkItemsRequestedAt` (Date) và `checkItemsRequestedBy` (ObjectId/String)
- [ ] `GET /orders` trả về các field này trong response
- [ ] `GET /orders/:id` trả về các field này trong response
- [ ] `PUT /orders/:id` lưu và trả về các field này
- [ ] `GET /orders?tableNumber=X` trả về các field này
- [ ] Test bằng Postman/Thunder Client để xác nhận response có các field này

## Cách test

### Test bằng Postman/Thunder Client:

1. **Test GET /orders:**
   ```
   GET http://192.168.1.164:3000/orders
   ```
   **KIỂM TRA KỸ:** Mở response JSON và tìm xem có field `checkItemsRequestedAt` và `checkItemsRequestedBy` trong **BẤT KỲ** order nào không.
   
   - ✅ **Nếu có:** Backend đã trả về đúng, vấn đề có thể ở Android parsing
   - ❌ **Nếu không có:** Backend chưa SELECT/include các field này → **CẦN SỬA BACKEND**
   
   **Ví dụ response đúng:**
   ```json
   {
     "success": true,
     "data": [
       {
         "_id": "6944b2680353a31593851e93",
         "tableNumber": 4,
         "checkItemsRequestedAt": "2024-12-19T09:30:00.000Z",  // ← PHẢI CÓ FIELD NÀY
         "checkItemsRequestedBy": "userId123",                  // ← PHẢI CÓ FIELD NÀY
         ...
       }
     ]
   }
   ```

2. **Test PUT /orders/:id:**
   ```
   PUT http://192.168.1.164:3000/orders/{orderId}
   Body (JSON):
   {
     "checkItemsRequestedAt": "2024-01-15T10:30:00.000Z",
     "checkItemsRequestedBy": "userId123"
   }
   ```
   Kiểm tra response có trả về các field này không.

3. **Test GET /orders/:id:**
   ```
   GET http://192.168.1.164:3000/orders/{orderId}
   ```
   Kiểm tra response có field `checkItemsRequestedAt` và `checkItemsRequestedBy` không.

## Lưu ý

- Nếu backend dùng **Mongoose**, `.select()` sẽ chỉ trả về các field được chỉ định. Nếu không dùng `.select()`, tất cả field sẽ được trả về (trừ khi có `select: false` trong schema).
- Nếu backend dùng **Sequelize** (SQL), cần đảm bảo model có các field này và không bị exclude trong query.
- Nếu backend dùng **TypeORM** (TypeScript), cần đảm bảo entity có các field này và không bị exclude.

## Sau khi sửa

1. Restart backend server
2. Test lại bằng Postman
3. Test lại trên Android app - danh sách yêu cầu kiểm tra bàn sẽ hiển thị


