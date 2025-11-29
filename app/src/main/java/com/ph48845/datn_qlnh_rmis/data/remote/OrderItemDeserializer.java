package com.ph48845.datn_qlnh_rmis.data.remote;

import com.google.gson.*;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.lang.reflect.Type;

/**
 * Gson deserializer cho Order.OrderItem (đã sửa) — giờ đọc thêm trường "note"
 * và một số fallback (menuItemName, imageUrl, menuItemId).
 */
public class OrderItemDeserializer implements JsonDeserializer<Order.OrderItem> {

    @Override
    public Order.OrderItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        // Trả về object không null để tránh NPE trong phần còn lại của app
        Order.OrderItem oi = new Order.OrderItem();

        if (json == null || json.isJsonNull()) return oi;
        JsonObject obj = json.getAsJsonObject();

        try {
            // menuItem can be string or object
            JsonElement menuElem = obj.get("menuItem");
            if (menuElem != null && !menuElem.isJsonNull()) {
                if (menuElem.isJsonPrimitive()) {
                    // menuItem: "id"
                    try { oi.setMenuItemId(menuElem.getAsString()); } catch (Exception ignored) {}
                } else if (menuElem.isJsonObject()) {
                    JsonObject menuObj = menuElem.getAsJsonObject();
                    // try common id fields
                    String id = null;
                    if (menuObj.has("_id") && !menuObj.get("_id").isJsonNull()) id = menuObj.get("_id").getAsString();
                    else if (menuObj.has("id") && !menuObj.get("id").isJsonNull()) id = menuObj.get("id").getAsString();

                    if (id != null) oi.setMenuItemId(id);

                    // optional: fill name/price/status snapshot from menu object if present
                    try {
                        if (menuObj.has("name") && !menuObj.get("name").isJsonNull()) {
                            String mname = menuObj.get("name").getAsString();
                            oi.setName(mname);
                            oi.setMenuItemName(mname);
                        }
                        if (menuObj.has("price") && !menuObj.get("price").isJsonNull()) {
                            oi.setPrice(menuObj.get("price").getAsDouble());
                        }
                        // image fields fallback
                        if (menuObj.has("imageUrl") && !menuObj.get("imageUrl").isJsonNull()) {
                            oi.setImageUrl(menuObj.get("imageUrl").getAsString());
                        } else if (menuObj.has("image") && !menuObj.get("image").isJsonNull()) {
                            oi.setImageUrl(menuObj.get("image").getAsString());
                        } else if (menuObj.has("thumbnail") && !menuObj.get("thumbnail").isJsonNull()) {
                            oi.setImageUrl(menuObj.get("thumbnail").getAsString());
                        }
                        if (menuObj.has("status") && !menuObj.get("status").isJsonNull()) {
                            oi.setStatus(menuObj.get("status").getAsString());
                        }
                    } catch (Exception ignored) {}
                }
            }

            // fallback direct fields on the OrderItem JSON object
            if (obj.has("menuItemId") && !obj.get("menuItemId").isJsonNull() && (oi.getMenuItemId() == null || oi.getMenuItemId().isEmpty())) {
                oi.setMenuItemId(obj.get("menuItemId").getAsString());
            }
            if (obj.has("menuItemName") && !obj.get("menuItemName").isJsonNull()) {
                oi.setMenuItemName(obj.get("menuItemName").getAsString());
            }
            if (obj.has("name") && !obj.get("name").isJsonNull()) {
                oi.setName(obj.get("name").getAsString());
                if (oi.getMenuItemName() == null || oi.getMenuItemName().isEmpty()) oi.setMenuItemName(oi.getName());
            }

            // quantity
            if (obj.has("quantity") && !obj.get("quantity").isJsonNull()) {
                try { oi.setQuantity(obj.get("quantity").getAsInt()); } catch (Exception ignored) {}
            }

            // price (order snapshot)
            if (obj.has("price") && !obj.get("price").isJsonNull()) {
                try { oi.setPrice(obj.get("price").getAsDouble()); } catch (Exception ignored) {}
            }

            // image fallback if not set from menuObj
            if ((oi.getImageUrl() == null || oi.getImageUrl().isEmpty())) {
                if (obj.has("imageUrl") && !obj.get("imageUrl").isJsonNull()) {
                    oi.setImageUrl(obj.get("imageUrl").getAsString());
                } else if (obj.has("image") && !obj.get("image").isJsonNull()) {
                    oi.setImageUrl(obj.get("image").getAsString());
                } else if (obj.has("thumbnail") && !obj.get("thumbnail").isJsonNull()) {
                    oi.setImageUrl(obj.get("thumbnail").getAsString());
                }
            }

            // status (order item status)
            if (obj.has("status") && !obj.get("status").isJsonNull()) {
                try { oi.setStatus(obj.get("status").getAsString()); } catch (Exception ignored) {}
            }

            // IMPORTANT: note (ghi chú) — đây là phần bạn cần
            if (obj.has("note") && !obj.get("note").isJsonNull()) {
                try { oi.setNote(obj.get("note").getAsString()); } catch (Exception ignored) {}
            } else if (obj.has("comment") && !obj.get("comment").isJsonNull()) {
                // fallback nếu backend dùng key khác
                try { oi.setNote(obj.get("comment").getAsString()); } catch (Exception ignored) {}
            }

        } catch (Exception ex) {
            // Nếu có lỗi parse, vẫn trả về object đã lấp được một phần để tránh crash app
        }

        // đảm bảo không null để UI dễ xử lý
        if (oi.getName() == null) oi.setName("");
        if (oi.getMenuItemName() == null) oi.setMenuItemName(oi.getName() != null ? oi.getName() : "");
        if (oi.getImageUrl() == null) oi.setImageUrl("");
        if (oi.getStatus() == null) oi.setStatus("");
        if (oi.getNote() == null) oi.setNote("");

        return oi;
    }
}