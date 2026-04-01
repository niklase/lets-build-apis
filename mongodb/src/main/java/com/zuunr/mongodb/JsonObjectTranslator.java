package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;

public class JsonObjectTranslator<T> {

    public T translate(JsonTranslatorStackItem<T> initialStackItem) {
        Deque<JsonTranslatorStackItem<T>> stack = new ArrayDeque<>();

        initialStackItem.setTranslated(null);
        JsonTranslatorStackItem<T> top = initialStackItem;
        stack.push(top);
        return iterate(stack);
    }


    private T iterate(Deque<JsonTranslatorStackItem<T>> stack) {

        JsonTranslatorStackItem<T> top = null;

        while (!stack.isEmpty()) {
            top = stack.peek();
            if (top.isTranslationDone()) {
                top.addTranslatedToParent();
                stack.pop();
            } else if (top.jsonValue.isJsonObject()) {
                if (!top.translateObject()) {
                    pushObjectFieldsToStack(top, stack);
                }
            } else if (top.jsonValue.isJsonArray()) {
                if (!top.translateArray()) {
                    pushArrayItemsToStack(top, stack);
                }
            } else {
                top.translateLeaf();
            }
        }
        return top.getTranslated();
    }

    private void pushObjectFieldsToStack(JsonTranslatorStackItem<T> top, Deque<JsonTranslatorStackItem<T>> stack) {
        JsonObject topJsonObject = top.jsonValue.getJsonObject();
        for (int i = 0; i < topJsonObject.size(); i++) {
            stack.push(top.createChild(topJsonObject.values().get(i), topJsonObject.keys().get(i).getString()));
        }
    }


    private void pushArrayItemsToStack(JsonTranslatorStackItem<T> top, Deque<JsonTranslatorStackItem<T>> stack) {
        JsonArray topJsonArray = top.jsonValue.getJsonArray();
        for (int i = topJsonArray.size() - 1; i >= 0; i--) { // Put last first to make first element appear highest in stack
            stack.push(top.createChild(topJsonArray.get(i), null));
        }
    }
}
