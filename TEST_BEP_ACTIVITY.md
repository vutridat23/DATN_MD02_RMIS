# Manual Test Checklist for BepActivity Update

## Test Overview
This document describes how to manually test the changes made to BepActivity and the new BepOrderActivity.

## Prerequisites
- App is installed and running on a device or emulator
- Backend server is running and accessible
- Database has at least one table with an active order containing pending items

## Test Case 1: BepActivity Shows Only Active Tables

### Steps:
1. Navigate to BepActivity (Kitchen screen)
2. Observe the screen layout

### Expected Results:
- Screen title shows "Danh sách bàn đang hoạt động" (List of active tables)
- Only tables with orders are displayed (status: OCCUPIED, PENDING_PAYMENT, or FINISH_SERVE)
- Tables are displayed in a 3-column grid layout
- Each table card shows:
  - Table number (e.g., "Bàn 1")
  - Status text "Có món cần làm" (Has items to prepare)
  - Serving status "- Đang phục vụ" (Being served)
  - Green background color (#4CAF50)
  - White text color

### Pass/Fail Criteria:
- [ ] PASS: Only active tables are shown
- [ ] PASS: Grid layout displays correctly with 3 columns
- [ ] PASS: Card styling matches description
- [ ] FAIL: If all tables are shown or layout is incorrect

---

## Test Case 2: Refresh Button Functionality

### Steps:
1. In BepActivity, tap the "Làm mới" (Refresh) button
2. Observe loading indicator and table list

### Expected Results:
- Progress bar appears while loading
- Table list refreshes with current data from server
- Progress bar disappears when loading completes
- Toast message appears if no active tables exist

### Pass/Fail Criteria:
- [ ] PASS: Loading indicator shows during refresh
- [ ] PASS: Data refreshes correctly
- [ ] FAIL: If error occurs or data doesn't refresh

---

## Test Case 3: Navigate to BepOrderActivity

### Steps:
1. In BepActivity, tap on any active table card
2. Observe navigation to BepOrderActivity

### Expected Results:
- BepOrderActivity opens
- Toolbar shows title "Món cần làm" (Items to prepare)
- Back button is visible in toolbar
- Table info header shows correct table number (e.g., "Bàn 1")
- List of order items displays below

### Pass/Fail Criteria:
- [ ] PASS: Navigation works smoothly
- [ ] PASS: Correct table number is displayed
- [ ] PASS: Toolbar and UI elements are correct
- [ ] FAIL: If crash occurs or wrong table data is shown

---

## Test Case 4: BepOrderActivity Displays Correct Items

### Steps:
1. In BepOrderActivity (after opening from a table)
2. Observe the list of order items

### Expected Results:
- Only items with status "pending", "preparing", or "processing" are shown
- Items with status "done" are NOT shown
- Each item card shows:
  - Item name
  - Quantity
  - Note (if any)
  - Status buttons (received/preparing/ready/soldout)
- If no items need preparation, toast message "Không có món nào cần làm" appears

### Pass/Fail Criteria:
- [ ] PASS: Only pending/preparing/processing items are shown
- [ ] PASS: Item details are displayed correctly
- [ ] PASS: Empty state message shows when appropriate
- [ ] FAIL: If wrong items are shown or display is incorrect

---

## Test Case 5: Update Item Status

### Steps:
1. In BepOrderActivity, tap a status button on an item (e.g., "Preparing")
2. Observe the result

### Expected Results:
- Progress indicator may appear briefly
- Item status updates successfully
- Toast message "Đã cập nhật trạng thái" appears
- Item remains in list if status is not "done"

### Pass/Fail Criteria:
- [ ] PASS: Status updates successfully on server
- [ ] PASS: Toast confirmation appears
- [ ] PASS: UI updates correctly
- [ ] FAIL: If error occurs or status doesn't update

---

## Test Case 6: Complete Item (Set to Done)

### Steps:
1. In BepOrderActivity, change an item's status to "done" (ready/complete)
2. Observe the result

### Expected Results:
- Item status updates to "done"
- Item is removed from the list immediately
- Toast message "Đã hoàn thành món" appears
- If this was the last item, empty state may appear

### Pass/Fail Criteria:
- [ ] PASS: Item is removed from list after setting to "done"
- [ ] PASS: Toast confirmation appears
- [ ] PASS: List updates correctly
- [ ] FAIL: If item remains visible or error occurs

---

## Test Case 7: Back Navigation

### Steps:
1. In BepOrderActivity, tap the back button in toolbar or device back button
2. Return to BepActivity

### Expected Results:
- BepOrderActivity closes
- Returns to BepActivity
- BepActivity refreshes its table list (via onResume)
- If all items for a table are completed, that table may disappear from the list

### Pass/Fail Criteria:
- [ ] PASS: Navigation back works correctly
- [ ] PASS: BepActivity refreshes automatically
- [ ] FAIL: If crash occurs or list doesn't refresh

---

## Test Case 8: No Active Tables Scenario

### Steps:
1. Ensure no tables have active orders in the database
2. Open BepActivity
3. Observe the result

### Expected Results:
- Empty table list is shown
- Toast message "Không có bàn nào đang hoạt động" appears
- Refresh button still works

### Pass/Fail Criteria:
- [ ] PASS: Empty state is handled gracefully
- [ ] PASS: Toast message appears
- [ ] FAIL: If crash occurs

---

## Test Case 9: Multiple Tables with Different Order Counts

### Setup:
- Table 1: Has 2 pending items
- Table 2: Has 5 pending items
- Table 3: All items done (should not appear)

### Steps:
1. Open BepActivity
2. Verify only Table 1 and Table 2 appear
3. Open Table 1, verify 2 items shown
4. Open Table 2, verify 5 items shown

### Expected Results:
- Only tables with pending items appear in BepActivity
- Each table's BepOrderActivity shows correct number of items
- All item details are accurate

### Pass/Fail Criteria:
- [ ] PASS: Correct tables appear
- [ ] PASS: Correct item counts for each table
- [ ] FAIL: If wrong tables appear or item counts are incorrect

---

## Test Case 10: Screen Rotation (Optional)

### Steps:
1. Open BepActivity
2. Rotate device/emulator
3. Observe if layout adapts
4. Repeat for BepOrderActivity

### Expected Results:
- Layout adapts to new orientation
- Data is preserved
- Grid columns may adjust based on available width
- No crashes occur

### Pass/Fail Criteria:
- [ ] PASS: Screen rotation is handled gracefully
- [ ] FAIL: If crash occurs or data is lost

---

## Summary

Total Test Cases: 10
Passed: ___
Failed: ___

## Notes:
- If any test case fails, document the specific error or unexpected behavior
- Include screenshots or screen recordings if possible
- Note device/emulator details and Android version
- Check logcat for any error messages

## Known Limitations:
- Real-time updates are not implemented (manual refresh required)
- Network errors may occur if server is unavailable
- Build system configuration may need adjustment for compilation
