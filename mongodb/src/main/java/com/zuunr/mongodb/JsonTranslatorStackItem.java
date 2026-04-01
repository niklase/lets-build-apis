package com.zuunr.mongodb;

import com.zuunr.json.JsonValue;

public abstract class JsonTranslatorStackItem<T> {

    final JsonTranslatorStackItem parent;
    protected final String parentKey;

    final JsonValue jsonValue;
    private T translated;

    public JsonTranslatorStackItem(JsonValue jsonValue, JsonTranslatorStackItem parent, String parentKey) {
        this.jsonValue = jsonValue;
        this.parent = parent;
        this.parentKey = parentKey;
    }

    public final boolean isTranslationDone() {
        return jsonValue.isNull()   // the translation of JsonValue.NULL is null this check is needed
                || translated != null;
    }

    public final void addTranslatedToParent() {
        addTranslatedToParent(translated);
    }

    abstract void addTranslatedToParent(T value);

    /**
     * Translate the JsonObject of this stack item if possible and assign the result to the field <code>translated</code>.
     *
     * @return true if the JsonObject if this stack item was translated. This also means the field <code>translated</code> is set.
     */
    public abstract boolean translateObject();

    /**
     * Translate the JsonArray of this stack item if possible and assign the result to the field <code>translated</code>.
     *
     * @return true if the JsonArray if this stack item was translated. This also means the field <code>translated</code> is set.
     */
    public abstract boolean translateArray();

    /**
     * Translate the leaf (i.e not <code>JsonObject</code> or <code>JsonArray</code>)
     *
     * @return
     */
    public abstract boolean translateLeaf();

    public T getTranslated() {
        return translated;
    }

    public void setTranslated(T translated) {
        this.translated = translated;
    }

    /**
     * @param toBeTranslated
     * @param parentKey      the filed name if parent is JsonObject or null if parent is JsonArray
     * @return
     */
    public abstract JsonTranslatorStackItem<T> createChild(JsonValue toBeTranslated, String parentKey);
}
