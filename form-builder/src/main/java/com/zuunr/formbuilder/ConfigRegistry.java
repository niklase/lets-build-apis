package com.zuunr.formbuilder;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;

public class ConfigRegistry {



    private final JsonValue transitionSchema = JsonValueFactory.create("""
            {
              "properties": {
                "newState": {
                  "properties": {
                    "status": {
                      "enum": ["TODO", "DOING", "DONE"]
                    }
                  }
                }
              }
            }
            
            """);



    JsonObject getConfig(JsonObject request) {
        return JsonObject.EMPTY
                .put("requestSchema", JsonObject.EMPTY)       // ie /request/body/...
                .put("transitionSchema", transitionSchema);   // /currentState/status and newState/status...

    }




}
