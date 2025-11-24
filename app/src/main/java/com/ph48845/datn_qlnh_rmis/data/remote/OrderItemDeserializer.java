package com.ph48845.datn_qlnh_rmis.data.remote;

import com.google.gson.*;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.lang.reflect.Type;

/**
 * Gson deserializer cho Order.OrderItem.
 * - Chỉ lấy ảnh từ menuItem (menuItem.image, menuItem.imageUrl, thumbnail) và gán vào OrderItem.imageUrl.
 * - Bổ sung: đọc trường "note" (nếu server trả) từ level item và gán vào OrderItem.note.
 */
public class OrderItemDeserializer implements JsonDeserializer<Order.OrderItem> {

    @Override
    public Order.OrderItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) return null;
        JsonObject obj = json.getAsJsonObject();

        Order.OrderItem oi = new Order.OrderItem();

        // menuItem can be string or object
        JsonElement menuElem = obj.get("menuItem");
        if (menuElem != null && !menuElem.isJsonNull()) {
            if (menuElem.isJsonPrimitive()) {
                // menuItem: "id"
                try {
                    String id = menuElem.getAsString();
                    oi.setMenuItemId(id);
                    oi.setMenuItemRaw(id);
                } catch (Exception ignored) {}
            } else if (menuElem.isJsonObject()) {
                JsonObject menuObj = menuElem.getAsJsonObject();
                // id
                String id = null;
                if (menuObj.has("_id") && !menuObj.get("_id").isJsonNull()) id = menuObj.get("_id").getAsString();
                else if (menuObj.has("id") && !menuObj.get("id").isJsonNull()) id = menuObj.get("id").getAsString();
                if (id != null) oi.setMenuItemId(id);

                // name/price/status snapshots (optional)
                try {
                    if (menuObj.has("name") && !menuObj.get("name").isJsonNull()) oi.setName(menuObj.get("name").getAsString());
                    if (menuObj.has("price") && !menuObj.get("price").isJsonNull()) oi.setPrice(menuObj.get("price").getAsDouble());
                    if (menuObj.has("status") && !menuObj.get("status").isJsonNull()) oi.setStatus(menuObj.get("status").getAsString());
                } catch (Exception ignored) {}

                // ONLY: lấy ảnh từ menuItem (image > imageUrl > thumbnail)
                try {
                    if (menuObj.has("image") && !menuObj.get("image").isJsonNull()) {
                        oi.setImageUrl(menuObj.get("image").getAsString());
                    } else if (menuObj.has("imageUrl") && !menuObj.get("imageUrl").isJsonNull()) {
                        oi.setImageUrl(menuObj.get("imageUrl").getAsString());
                    } else if (menuObj.has("thumbnail") && !menuObj.get("thumbnail").isJsonNull()) {
                        oi.setImageUrl(menuObj.get("thumbnail").getAsString());
                    } else {
                        oi.setImageUrl("");
                    }
                } catch (Exception ignored) {}

                // lưu raw object as JSON string (cho debug nếu cần)
                try { oi.setMenuItemRaw(menuObj.toString()); } catch (Exception ignored) {}
            }
        }

        // override name/menuItemName/etc nếu server trả snapshot trên level item
        if (obj.has("menuItemName") && !obj.get("menuItemName").isJsonNull()) {
            try { oi.setMenuItemName(obj.get("menuItemName").getAsString()); } catch (Exception ignored) {}
        }
        if (obj.has("name") && !obj.get("name").isJsonNull()) {
            try { oi.setName(obj.get("name").getAsString()); } catch (Exception ignored) {}
        }

        if (obj.has("quantity") && !obj.get("quantity").isJsonNull()) {
            try { oi.setQuantity(obj.get("quantity").getAsInt()); } catch (Exception ignored) {}
        }

        if (obj.has("price") && !obj.get("price").isJsonNull()) {
            try { oi.setPrice(obj.get("price").getAsDouble()); } catch (Exception ignored) {}
        }

        if (obj.has("status") && !obj.get("status").isJsonNull()) {
            try { oi.setStatus(obj.get("status").getAsString()); } catch (Exception ignored) {}
        }

        // NEW: read top-level "note" field on the item (if server returns it)
        if (obj.has("note") && !obj.get("note").isJsonNull()) {
            try { oi.setNote(obj.get("note").getAsString()); } catch (Exception ignored) {}
        }

        // IMPORTANT: theo yêu cầu, KHÔNG gán image từ top-level item fields.
        // (Không đọc obj.get("image") / obj.get("imageUrl"))

        // đảm bảo không null để UI xử lý
        if (oi.getName() == null) oi.setName("");
        if (oi.getMenuItemName() == null) oi.setMenuItemName("");
        if (oi.getImageUrl() == null) oi.setImageUrl("");
        if (oi.getStatus() == null) oi.setStatus("");
        if (oi.getNote() == null) oi.setNote("");

        return oi;
    }
}