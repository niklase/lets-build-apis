package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * Lets a Processor (typically an ItemDecorator) call this same dcentb backend's own API
 * "by reference" - in-process, via Controller.execute(...), with no HTTP/servlet layer
 * and no JSON (de)serialization involved beyond building the Request itself.
 *
 * This re-enters the EXACT same pipeline a real inbound HTTP request would - the full
 * chain (authentication, access control, mongo, etc.) runs unchanged. Nothing is
 * bypassed: callers must put real credentials into "headers" (e.g.
 * "authorization": ["Bearer ..."] or "api-key": ["..."]) for whichever security scheme
 * applies to the target operation, exactly as any external client would.
 *
 * A Spring-managed ItemDecorator gets this via normal constructor injection. A
 * reflectively-constructed (non-Spring) ItemDecorator can reach it via
 * DcentbApplicationContextHolder.get().getBean(SelfApiClient.class).
 *
 * Recursion caveat: since this call happens synchronously on the calling thread, a
 * decorator must not invoke an operation that would itself re-invoke the same
 * decorator (directly or indirectly) - dcentb has no call-depth guard, same as any
 * recursive function call. The self-call also runs as a fully independent request (its
 * own Mongo commands); it is NOT part of any transaction with the outer request that
 * triggered the decorator, since dcentb has no cross-request transaction concept today.
 */
public class SelfApiClient {

    private final Controller controller;

    public SelfApiClient(Controller controller) {
        this.controller = controller;
    }

    public Response call(String method, String uri, JsonObject headers, JsonValue body) {
        JsonObject requestObject = JsonObject.EMPTY
                .put("method", method)
                .put("uri", uri)
                .put("headers", headers);
        if (body != null) {
            requestObject = requestObject.put("body", body);
        }
        return controller.execute(Request.of(requestObject));
    }
}
