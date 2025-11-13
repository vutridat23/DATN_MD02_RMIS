package com.ph48845.datn_qlnh_rmis.data.remote;





import com.google.gson.*;
import com.ph48845.datn_qlnh_rmis.data.model.Order;


import java.lang.reflect.Type;

/**
 * Gson deserializer cho Order.OrderItem để chấp nhận 2 dạng JSON:
 * - "menuItem": "64a7..."    (string id)
 * - "menuItem": { "_id": "...", "name":"...", "price": 50, ... } (object)
 *
 * Khi menuItem là object, deserializer sẽ:
 * - lấy _id (hoặc id) để set menuItemId
 * - nếu có name/price/status sẽ set vào OrderItem.name / price / status (snapshot)
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
                    oi.setMenuItemId(menuElem.getAsString());
                } catch (Exception ignored) {}
            } else if (menuElem.isJsonObject()) {
                JsonObject menuObj = menuElem.getAsJsonObject();
                // try common id fields
                String id = null;
                if (menuObj.has("_id") && !menuObj.get("_id").isJsonNull()) id = menuObj.get("_id").getAsString();
                else if (menuObj.has("id") && !menuObj.get("id").isJsonNull()) id = menuObj.get("id").getAsString();

                if (id != null) oi.setMenuItemId(id);

                // optional: fill name/price/status snapshot from menu object if present
                try {
                    if (menuObj.has("name") && !menuObj.get("name").isJsonNull()) oi.setName(menuObj.get("name").getAsString());
                    if (menuObj.has("price") && !menuObj.get("price").isJsonNull()) oi.setPrice(menuObj.get("price").getAsDouble());
                    if (menuObj.has("status") && !menuObj.get("status").isJsonNull()) oi.setStatus(menuObj.get("status").getAsString());
                } catch (Exception ignored) {}
            }
        }

        // name (order snapshot) - override only if present
        if (obj.has("name") && !obj.get("name").isJsonNull()) {
            try { oi.setName(obj.get("name").getAsString()); } catch (Exception ignored) {}
        }

        // quantity
        if (obj.has("quantity") && !obj.get("quantity").isJsonNull()) {
            try { oi.setQuantity(obj.get("quantity").getAsInt()); } catch (Exception ignored) {}
        }

        // price (order snapshot)
        if (obj.has("price") && !obj.get("price").isJsonNull()) {
            try { oi.setPrice(obj.get("price").getAsDouble()); } catch (Exception ignored) {}
        }

        // status (order item status)
        if (obj.has("status") && !obj.get("status").isJsonNull()) {
            try { oi.setStatus(obj.get("status").getAsString()); } catch (Exception ignored) {}
        }

        return oi;
    }
}