package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers requestId's one job: give every response - success or failure, from any processor
 * or none - a correlatable x-request-id header, honoring one the caller already sent instead
 * of replacing it. It's a header rather than a body field so ResponseAccessController's
 * per-role response-schema filtering (which runs on the body, not headers) can never strip
 * it for some roles and not others - see RequestHandlerBase.withRequestId.
 */
class RequestHandlerBaseTest {

    private static class ThrowingProcessor extends Processor {
        ThrowingProcessor() {
            super();
        }

        @Override
        public JsonObject process(JsonObject requestContext) {
            throw new RuntimeException("boom");
        }
    }

    private static class RespondingProcessor extends Processor {
        private final int status;

        RespondingProcessor(int status) {
            super();
            this.status = status;
        }

        @Override
        public JsonObject process(JsonObject requestContext) {
            // Short-circuits ResponseAccessController's filtering, which otherwise needs a
            // full permission-schema setup this test has no reason to build.
            return requestContext
                    .put(UNRESTRICTED_ACCESS, true)
                    .put(RESPONSE, JsonObject.EMPTY.put("status", status));
        }
    }

    private static class TestRequestHandler extends RequestHandlerBase {
        private final Processor[] processors;

        TestRequestHandler(Processor... processors) {
            super(JsonObject.EMPTY.jsonValue());
            this.processors = processors;
        }

        @Override
        public Processor[] getProcessors() {
            return processors;
        }
    }

    private static Request requestWithHeaders(JsonObject headers) {
        return new Request(JsonObject.EMPTY.put("headers", headers));
    }

    private static String requestIdHeader(Response response) {
        return response.getHeaders().get("x-request-id", JsonArray.EMPTY).getJsonArray().get(0).getString();
    }

    @Test
    void exceptionFromAProcessorReturns500WithAGeneratedRequestIdHeader() {
        TestRequestHandler handler = new TestRequestHandler(new ThrowingProcessor());

        Response response = handler.process(requestWithHeaders(JsonObject.EMPTY));

        assertEquals(500, response.getStatus());
        String requestId = requestIdHeader(response);
        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
    }

    @Test
    void inboundRequestIdHeaderIsEchoedBackRatherThanReplaced() {
        TestRequestHandler handler = new TestRequestHandler(new ThrowingProcessor());
        JsonObject headers = JsonObject.EMPTY.put("x-request-id", JsonArray.of("caller-assigned-id"));

        Response response = handler.process(requestWithHeaders(headers));

        assertEquals("caller-assigned-id", requestIdHeader(response));
    }

    @Test
    void twoFailingRequestsGetDifferentGeneratedRequestIds() {
        TestRequestHandler handler = new TestRequestHandler(new ThrowingProcessor());

        Response first = handler.process(requestWithHeaders(JsonObject.EMPTY));
        Response second = handler.process(requestWithHeaders(JsonObject.EMPTY));

        assertNotEquals(requestIdHeader(first), requestIdHeader(second));
    }

    @Test
    void successfulResponseAlsoGetsARequestIdHeader() {
        TestRequestHandler handler = new TestRequestHandler(new RespondingProcessor(200));
        JsonObject headers = JsonObject.EMPTY.put("x-request-id", JsonArray.of("caller-assigned-id"));

        Response response = handler.process(requestWithHeaders(headers));

        assertEquals(200, response.getStatus());
        assertEquals("caller-assigned-id", requestIdHeader(response));
    }
}
