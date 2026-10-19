package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.accesscontrol.CredentialHeaderRedactor;
import com.zuunr.dcentb.rest.processor.accesscontrol.ResponseAccessController;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.UUID;

public abstract class RequestHandlerBase implements RequestHandler {

    private static final String REQUEST_ID_HEADER = "x-request-id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    Logger logger = org.slf4j.LoggerFactory.getLogger(RequestHandlerBase.class);

    public ResponseAccessController responseAccessController;

    public abstract Processor[] getProcessors();


    public RequestHandlerBase(JsonValue config) {
        responseAccessController = new ResponseAccessController(config);
    }


    @Override
    public Response process(Request request) {
        // Save/restore rather than clear: a Processor that re-enters this same thread
        // (SystemApiClient re-entry from a TaskProcessor) must get its own requestId without
        // clobbering, or being clobbered by, an outer call's.
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        try {
            Response response = processInternally(request);
            logger.info("Response: {}", response.toString());
            return response;
        } finally {
            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID_MDC_KEY);
            } else {
                MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
            }
        }
    }

    public Response processInternally(Request request) {
        String requestId = requestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        try {
            JsonObject updatedRequestContext = JsonObject.EMPTY.put(Processor.REQUEST, request.asJsonObject());
            for (Processor processor : getProcessors()) {
                logRequestContext(processor.getClass().getSimpleName(), updatedRequestContext);
                updatedRequestContext = processor.process(updatedRequestContext);
                JsonObject response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
                if (response != null) {
                    processor = responseAccessController;
                    logRequestContext(processor.getClass().getSimpleName(), updatedRequestContext);
                    updatedRequestContext = responseAccessController.process(updatedRequestContext);
                    response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
                    JsonObject headers = updatedRequestContext.get(Processor.RESPONSE, JsonObject.EMPTY).getJsonObject().get("headers", JsonObject.EMPTY).getJsonObject();
                    response = response.put("headers", withRequestId(headers, requestId));
                    return response.as(Response.class);
                }
            }
        } catch (Exception e) {
            logger.error("processInternally exception [requestId={}]", requestId, e);
        }
        return JsonObject.EMPTY.put("status", 500)
                .put("headers", withRequestId(JsonObject.EMPTY, requestId))
                .as(Response.class);
    }

    /**
     * x-request-id is set as a response header, on every response including this generic
     * failure, rather than as a body field: ResponseAccessController's per-role response
     * schema filtering runs on the body (not headers) before this point, and would silently
     * strip an unlisted body field for any role whose schema doesn't explicitly allow it. A
     * header bypasses that filtering entirely, so it's the only mechanism guaranteed to
     * survive regardless of who's calling.
     */
    private static JsonObject withRequestId(JsonObject headers, String requestId) {
        return headers.put(REQUEST_ID_HEADER, JsonArray.of(requestId));
    }

    /**
     * Honors an inbound X-Request-Id if the caller - or an upstream proxy/load balancer -
     * already assigned one, so correlation survives crossing into dcentb from a system that
     * assigns its own request IDs; generates a fresh one otherwise. Always lowercase, like
     * every other header lookup here - see RequestUtil.createRequest.
     */
    private static String requestId(Request request) {
        JsonArray existing = request.asJsonObject().get("headers", JsonObject.EMPTY).getJsonObject()
                .get(REQUEST_ID_HEADER, JsonValue.NULL).getJsonArray();
        if (existing != null && !existing.isEmpty()) {
            return existing.get(0).getString();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Diagnostic dump of the full requestContext as it enters a given processor. Logged
     * redacted at DEBUG by default (CredentialHeaderRedactor masks any raw "api-key"/
     * "authorization" header - the only point one can still be present in clear text is the
     * entry into AuthenticationProcessor itself, before it has had a chance to redact the
     * context it returns, so this call site redacts independently rather than relying on
     * that). TRACE opts into the real, unredacted values when actually tracking down an
     * auth-header problem - enabling it means credentials will appear in this logger's output.
     */
    private void logRequestContext(String processorName, JsonObject requestContext) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} requestContext (UNREDACTED - contains credentials): {}", processorName, requestContext);
        } else if (logger.isDebugEnabled()) {
            logger.debug("{} requestContext: {}", processorName, CredentialHeaderRedactor.redact(requestContext));
        }
    }
}
