package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * Lets trusted, in-process dcentb code (typically a TaskProcessor reacting to a change-stream
 * event) call this same backend's own API as the built-in SUPERUSER identity - authorized for
 * anything any role could do via the API, on any collection, nothing more - without needing
 * real credentials or per-collection permission config.
 *
 * This re-enters the exact same pipeline a real inbound HTTP request would (deserialization,
 * item decoration, mongo, ...) via Controller.execute(...) - nothing is bypassed. No credentials
 * are supplied in headers - SUPERUSER is granted via a top-level "internalPrincipal" key on the
 * request JsonObject (a sibling of "headers"/"body", not a header value). AuthenticationProcessor
 * recognizes that key and skips every header-based authenticator entirely.
 *
 * By-reference only, as a real security boundary rather than an obfuscated secret:
 * RequestUtil.createRequest - the only code path from a real inbound HTTP request to a Request
 * object - only ever populates "method"/"uri"/"headers"/"query"/"body". It never sets
 * "internalPrincipal", so no external HTTP request can ever trigger SUPERUSER, regardless of
 * what headers or body it sends.
 *
 * A Spring-managed TaskProcessor gets this via normal constructor injection. A reflectively-
 * constructed (non-Spring) TaskProcessor can reach it via
 * DcentbApplicationContextHolder.get().getBean(SystemApiClient.class).
 *
 * Recursion caveat: this call is synchronous on the calling thread - a TaskProcessor must not
 * trigger an operation that re-invokes itself (directly or indirectly); there's no call-depth
 * guard, same as any recursive function call. It is also not part of any transaction with the
 * outer change-stream event - dcentb has no cross-request transaction concept today.
 *
 * No caching: every call re-enters the full pipeline (auth, access control, MongoDB,
 * ItemDecorator) for real. A response-caching seam was tried here and removed again - if one
 * gets built later it needs its own design pass, not a revival of this one.
 */
public class SystemApiClient {

    private final Controller controller;

    public SystemApiClient(Controller controller) {
        this.controller = controller;
    }

    public Response call(String method, String uri, JsonValue body) {

        JsonObject headers = body == null
                ? JsonObject.EMPTY
                : JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"));

        JsonObject requestObject = JsonObject.EMPTY
                .put("method", method)
                .put("uri", uri)
                .put("headers", headers)
                .put(Processor.INTERNAL_PRINCIPAL, "SYSTEM");
        if (body != null) {
            // OASRequestDeserializer expects a raw JSON string here, exactly as real HTTP
            // delivers a request body - same conversion ControllerIT's test harness applies.
            requestObject = requestObject.put("body", body.asJson());
        }
        return controller.execute(Request.of(requestObject));
    }
}
