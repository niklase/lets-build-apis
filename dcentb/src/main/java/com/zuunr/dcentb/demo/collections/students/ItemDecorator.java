package com.zuunr.dcentb.demo.collections.students;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * Demo-only illustration of the ItemDecorator convention: computes "attendanceStatus"
 * from "attendancePercent" on whichever state it's handed via "itemState". Picked up
 * automatically by CurrentStateItemDecorator/NewStateItemDecorator because
 * demo.openapi.json sets x-dcentb.decoratorBasePackage=com.zuunr.dcentb.demo and this
 * class sits at <that package>.collections.students.ItemDecorator.
 */
public class ItemDecorator extends Processor {

    public ItemDecorator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject itemState = requestContext.get("itemState", JsonValue.NULL).getJsonObject();

        JsonValue attendancePercent = itemState.get("attendancePercent");
        if (attendancePercent != null && attendancePercent.isJsonNumber()) {
            String attendanceStatus = attendancePercent.getInteger() < 60 ? "AT_RISK" : "OK";
            itemState = itemState.put("attendanceStatus", attendanceStatus);
        }

        JsonValue email = itemState.get("email");
        if (email != null) {
            itemState = itemState.put("email", email.getString().toLowerCase());
        }

        return requestContext.put("itemState", itemState);
    }
}
