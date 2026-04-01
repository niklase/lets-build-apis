package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;

import java.util.ArrayDeque;

public class PathConfigMapper {

    private static final String ANY = "*";
    private static final JsonValue PATH_ELEM_ANY = JsonValue.of(ANY);

    private static final String NEXT_MAPPINGS = "nextMappings";
    private static final String NEXT_PATH = "nextPath";
    private static final String STATUS = "status";

    private JsonObject pathConfigs = JsonObject.EMPTY;

    public JsonObject getConfiguredPath(JsonObject configuredPaths, String realPath, String realMethod) {
        return getPathAndMethodConfig(configuredPaths, JsonValue.of(realPath).as(JsonPointer.class).asArray(), realMethod);
    }

    public JsonObject getConfig(String realPath, String realMethod) {
        return getPathAndMethodConfig(pathConfigs, JsonValue.of(realPath).as(JsonPointer.class).asArray(), realMethod);
    }

    public PathConfigMapper addConfigForPathAndMethod(String path, String method, JsonValue value) {
        pathConfigs = addConfigForPathAndMethod(pathConfigs, path, method, value);
        return this;
    }

    private static JsonObject addConfigForPathAndMethod(JsonObject toBeUpdated, String path, String method, JsonValue value) {
        JsonPointer pointer = JsonValue.of(path).as(JsonPointer.class);
        JsonArray pointerArray = pointer.asArray();
        JsonArrayBuilder updatedPointer = JsonArray.EMPTY.builder();
        JsonArrayBuilder varNameOnIndexBuilder = JsonArray.EMPTY.builder();
        for (int i = 0; i < pointerArray.size(); i++) {
            JsonValue varName = JsonValue.NULL;
            JsonValue pathItem = pointerArray.get(i);
            JsonValue updatedPathElement = pathItem;
            if (pathItem.isString()) {
                String pathItemString = pathItem.getString();
                if (pathItemString.length() > 2 && pathItemString.startsWith("{") && pathItemString.endsWith("}")) {
                    varName = JsonValue.of(pathItemString.substring(1, pathItemString.length() - 1));
                    updatedPathElement = PATH_ELEM_ANY;
                }
            }
            varNameOnIndexBuilder.add(varName);
            updatedPointer.add(updatedPathElement);
        }

        toBeUpdated = toBeUpdated.put(
                updatedPointer.add("#" + method).build(),
                JsonArray.of(value, varNameOnIndexBuilder.build())
        );
        return toBeUpdated;
    }

    private JsonObject getPathAndMethodConfig(JsonObject configuredPaths, JsonArray realPath, String method) throws PathNotFoundException, MethodNotFoundException {

        JsonObject response = getPathAndMethodConfigResponse(configuredPaths, realPath, method);
        int status = response.get(STATUS).getInteger();

        if (status == 404) {
            throw new PathNotFoundException();
        } else if (status == 405) {
            throw new MethodNotFoundException();
        }
        return response.get("body").getJsonObject();
    }

    private boolean isAnyMethodConfigured(JsonObject nextMappings) {
        for (JsonValue key : nextMappings.keys()) {
            if (key.getString().charAt(0) == '#') {
                return true;
            }
        }
        return false;
    }

    private JsonObject getPathAndMethodConfigResponse(JsonObject configuredPaths, JsonArray realPath, String method) throws PathNotFoundException, MethodNotFoundException {

        JsonArray nextPath = realPath;
        JsonObject nextMappings = configuredPaths;

        ArrayDeque<JsonObject> stack = new ArrayDeque<>();

        while (true) {
            if (nextPath.isEmpty()) {
                JsonObject result = getConfigByMethod(realPath, method, nextMappings);

                if (result != null) {
                    return result;
                } else if (stack.isEmpty()) {
                    return JsonObject.EMPTY.put(STATUS, 404);
                }
                JsonObject stackItem = stack.pop();
                nextMappings = stackItem.get(NEXT_MAPPINGS).getJsonObject();
                nextPath = stackItem.get(NEXT_PATH).getJsonArray();

            } else {

                String head = getNextPathHeadAsString(nextPath);

                JsonValue mapping = nextMappings.get(head);
                if (mapping == null) {
                    mapping = nextMappings.get(ANY);
                    if (mapping == null) {
                        if (stack.isEmpty()) {
                            return JsonObject.EMPTY.put(STATUS, 404);
                        }
                        JsonObject stackItem = stack.pop();
                        nextMappings = stackItem.get(NEXT_MAPPINGS).getJsonObject();
                        nextPath = stackItem.get(NEXT_PATH).getJsonArray();
                        continue;

                    }
                } else {
                    stack.push(JsonObject.EMPTY
                            .put(NEXT_PATH, nextPath)
                            .put(NEXT_MAPPINGS, nextMappings.remove(head))
                    );
                }
                nextPath = nextPath.tail();
                nextMappings = mapping.getJsonObject();
            }
        }
    }

    private String getNextPathHeadAsString(JsonArray nextPath) {
        JsonValue headJsonValue = nextPath.head();
        return headJsonValue.isString() ? headJsonValue.getString() : headJsonValue.toString();
    }

    private JsonObject getConfigByMethod(JsonArray realPath, String method, JsonObject nextMappings) {
        JsonValue methodMapping = nextMappings.get("#" + method);
        if (methodMapping == null) {
            methodMapping = nextMappings.get("#*");
            if (methodMapping == null) {
                if (isAnyMethodConfigured(nextMappings)) {
                    return JsonObject.EMPTY.put(STATUS, 405);
                } else {
                    return null;
                }
            }
        }
        return JsonObject.EMPTY
                .put(STATUS, 200)
                .put("body", JsonObject.EMPTY
                        .put("config", methodMapping.get(0))
                        .put("pathParams", setVariables(realPath, methodMapping.get(1).getJsonArray())));
    }

    private JsonObject setVariables(JsonArray realPath, JsonArray pathParams) {
        JsonObject result = JsonObject.EMPTY;
        for (int i = 0; i < pathParams.size(); i++) {
            JsonValue param = pathParams.get(i);
            if (param.isString()) {
                result = result.put(param.getString(), realPath.get(i));
            }
        }
        return result;
    }
}