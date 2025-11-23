# BepActivity Update - Testing Guide

## Overview
This document provides testing instructions for the BepActivity updates that enable the kitchen staff to view only active tables and see their order items in read-only mode.

## Changes Made

### 1. BepActivity Behavior Change
**Before:** Displayed all order items from all tables in a single list with status update buttons.

**After:** Displays only active tables in a 3-column grid. Clicking a table opens BepOrderActivity to view its order items (read-only).

### 2. New Activity: BepOrderActivity
A new read-only activity that shows all order items for a specific table. No status update functionality.

## Manual Testing Steps

### Prerequisites
1. Ensure the backend server is running and accessible
2. Have at least 2-3 tables with different statuses:
   - At least 1 table with status: OCCUPIED (has active orders)
   - At least 1 table with status: AVAILABLE (no orders)
   - Optional: 1 table with status: PENDING_PAYMENT or FINISH_SERVE

### Test Case 1: Verify Active Tables Display
**Steps:**
1. Open the app
2. Navigate to BepActivity (Kitchen screen)
3. Observe the displayed tables

**Expected Result:**
- Only tables with status OCCUPIED, PENDING_PAYMENT, or FINISH_SERVE are shown
- Tables are displayed in a 3-column grid
- Each table card shows:
  - Table number (e.g., "Bàn 1")
  - Status text: "Có order"
  - Green background color (#4CAF50)
- Tables with status AVAILABLE are NOT shown

### Test Case 2: Verify Empty State
**Steps:**
1. Ensure all tables have status AVAILABLE (no active orders)
2. Open BepActivity

**Expected Result:**
- Empty message is displayed: "Không có bàn nào đang hoạt động"
- No table cards are shown
- Refresh button is still visible and functional

### Test Case 3: View Order Items for a Table
**Steps:**
1. Ensure at least one table has active orders (status: OCCUPIED)
2. Open BepActivity
3. Click on an active table card

**Expected Result:**
- BepOrderActivity opens
- Toolbar shows: "Món cần làm - Bàn X" (where X is the table number)
- Back button is visible in toolbar
- All order items for that table are displayed in a list

### Test Case 4: Verify Order Items Display (Read-only)
**Steps:**
1. From Test Case 3, observe the order items list in BepOrderActivity

**Expected Result:**
Each order item card shows:
- Item name (bold, 16sp)
- Quantity (e.g., "SL: 2") in green on the right
- Note (if exists): "Ghi chú: [note text]"
- Status with appropriate color:
  - "Đã hoàn thành" - Green (#4CAF50) for "done"/"ready" status
  - "Đang chế biến" - Blue (#2196F3) for "preparing"/"cooking" status
  - "Đã nhận" - Purple (#9C27B0) for "received" status
  - "Chờ chế biến" - Orange (#FF9800) for "pending" or unknown status
- NO status update buttons (read-only mode)

### Test Case 5: Verify Refresh Functionality
**Steps:**
1. Open BepActivity
2. While on another device/session, change a table's status from AVAILABLE to OCCUPIED
3. Click "Làm mới" button in BepActivity

**Expected Result:**
- Loading indicator appears briefly
- The newly active table appears in the grid
- Previously visible tables remain if still active

### Test Case 6: Navigation and Back Button
**Steps:**
1. Open BepActivity → Click a table → Open BepOrderActivity
2. Click back button in toolbar

**Expected Result:**
- Returns to BepActivity
- Active tables list is refreshed (onResume is called)

### Test Case 7: Multiple Tables with Orders
**Steps:**
1. Ensure 3+ tables have active orders
2. Open BepActivity
3. Click each table one by one

**Expected Result:**
- Each table opens BepOrderActivity showing only its own orders
- Order items are correctly associated with the selected table
- No mixing of orders between tables

## Known Limitations

1. **Build System**: The project has a pre-existing build configuration issue with AGP version 8.13.0 which doesn't exist. This needs to be fixed separately (change to a valid version like 8.1.0 or 8.2.0 in gradle/libs.versions.toml).

2. **Real-time Updates**: The old realtime button has been removed from BepActivity. If real-time updates are needed, they should be implemented at the repository level.

3. **Status Updates**: BepOrderActivity is intentionally read-only. If kitchen staff needs to update order status, that would be a separate feature (not included in this PR per requirements).

## API Dependencies

### Required Endpoints:
1. `GET /tables` - Returns all tables with their status
2. `GET /orders?tableNumber={number}` - Returns orders for a specific table

### Expected Data Format:

**TableItem:**
```json
{
  "_id": "table123",
  "tableNumber": 1,
  "status": "occupied", // or "pending_payment", "finish_serve"
  "capacity": 4,
  "location": "Tầng 1"
}
```

**Order with Items:**
```json
{
  "_id": "order123",
  "tableNumber": 1,
  "items": [
    {
      "menuItemId": "item1",
      "name": "Phở bò",
      "quantity": 2,
      "status": "pending",
      "note": "Không hành",
      "price": 50000
    }
  ]
}
```

## Troubleshooting

### Issue: No tables shown even though orders exist
**Solution:** Check table status in database. Only tables with status "occupied", "pending_payment", or "finish_serve" are displayed.

### Issue: Wrong orders shown for a table
**Solution:** Verify that orders in database have correct `tableNumber` field matching the table's `tableNumber`.

### Issue: Empty order items in BepOrderActivity
**Solution:** Check that orders for that table have `items` array populated with OrderItem objects.

## Code Quality Notes

- All new code follows Java conventions matching existing codebase
- Minimal changes approach: reused existing ThuNganViewModel logic
- Consistent UI patterns with ThuNganActivity (3-column grid, similar layouts)
- Proper separation of concerns: Activity → ViewModel → Repository → API
- Read-only design as per requirements (no status update functionality)
