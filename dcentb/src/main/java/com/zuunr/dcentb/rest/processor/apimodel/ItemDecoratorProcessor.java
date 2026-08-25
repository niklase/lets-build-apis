package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.util.CollectionNameProvider;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Resolves, once per operation, an app-provided "ItemDecorator" Processor for the
 * operation's collection - by convention, not configuration - and delegates to it,
 * translating the state this instance is responsible for (see stateKey()) in and out
 * of a neutral "itemState" property so the custom decorator never has to know whether
 * it's decorating currentState or newState. See CurrentStateItemDecorator and
 * NewStateItemDecorator for the two concrete instances wired into CUDItemRequestHandler.
 *
 * The collection's common name (e.g. "students", or "domain-1/houses" for a nested
 * resource) is derived via CollectionNameProvider, exactly as PreOperationAccessController
 * already does for "x-dcentb.collections.<name>...". Each "/"-separated segment has its
 * hyphens replaced with underscores (Java package names can't contain hyphens) and the
 * segments are joined with ".", then appended to the app's configured
 * "x-dcentb.decoratorBasePackage" + ".collections." to form a fully-qualified class name,
 * e.g. "com.acme.myapp" + ".collections." + "domain_1.houses" + ".ItemDecorator".
 *
 * Writing a decorator is entirely optional: if "decoratorBasePackage" isn't configured,
 * or no class exists at the derived name for a given collection, this Processor is a
 * no-op passthrough for that collection. A decorator class that exists but is shaped
 * wrong (no (JsonValue) constructor, doesn't extend Processor, etc.) still fails loudly -
 * "optional" only covers "no one wrote one," not bugs in the one that was written.
 *
 * An ItemDecorator is a plain Processor: it reads/writes only "itemState" on the
 * requestContext it's handed - it never touches "currentState"/"newState" directly.
 */
public abstract class ItemDecoratorProcessor extends Processor {

    private final Processor delegate;

    protected ItemDecoratorProcessor(JsonValue config) {
        super(config);
        this.delegate = resolveDelegate(config);
    }

    /**
     * Which requestContext key this instance decorates: "currentState" or "newState".
     */
    protected abstract String stateKey();

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (delegate == null) {
            return requestContext;
        }

        String stateKey = stateKey();

        JsonValue itemState = requestContext.get(stateKey);
        if (itemState == null || !itemState.isJsonObject()) {
            return requestContext;
        }
        JsonObject decorated = delegate.process(requestContext.put("itemState", itemState).remove("currentState").remove("newState"));

        return requestContext.put(stateKey, decorated.get("itemState"));
    }

    private static Processor resolveDelegate(JsonValue config) {
        JsonObject fullConfig = config.getJsonObject();
        String basePackage = fullConfig.get(X_DCENTB, JsonObject.EMPTY)
                .get("decoratorBasePackage", JsonValue.NULL).getString();
        if (basePackage == null || basePackage.isBlank()) {
            return null;
        }

        String commonName = CollectionNameProvider.getCollectionName(fullConfig);
        String packageSuffix = Arrays.stream(commonName.split("/"))
                .map(segment -> segment.replace("-", "_"))
                .collect(Collectors.joining("."));
        String fullyQualifiedClassName = basePackage + ".collections." + packageSuffix + ".ItemDecorator";

        try {
            Class<?> decoratorClass = Class.forName(fullyQualifiedClassName);
            return config.as(decoratorClass.asSubclass(Processor.class));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
