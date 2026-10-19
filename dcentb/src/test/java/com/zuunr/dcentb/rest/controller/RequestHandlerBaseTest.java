package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the one case RequestHandlerBase can't delegate to a specific processor: an
 * uncaught exception from anywhere in the pipeline. Before this, it became a bare
 * {"status":500} with nothing a caller or an operator could use to find the one log line
 * that explains it. Every other response's body is a specific processor's own concern and
 * is asserted exactly by fixture-driven tests elsewhere, so requestId is deliberately only
 * added to this one.
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

    @Test
    void exceptionFromAProcessorReturns500WithAGeneratedRequestId() {
        TestRequestHandler handler = new TestRequestHandler(new ThrowingProcessor());

        Response response = handler.process(requestWithHeaders(JsonObject.EMPTY));

        assertEquals(500, response.getStatus());
        String requestId = response.asJsonObject().get("requestId").getString();
        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
    }

    @Test
    void inboundRequestIdHeaderIsEchoedBackRatherThanReplaced() {
        TestRequestHandler handler = new TestRequestHandler(new ThrowingProcessor());
        JsonObject headers = JsonObject.EMPTY.put("x-request-id", JsonArray.of("caller-assigned-id"));

        Response response = handler.process(requestWithHeaders(headers));

        assertEquals("caller-assigned-id", response.asJsonObject().get("requestId").getString());
    }

    @Test
    void twoFailingRequestsGetDifferentGeneratedRequestIds() {
        TestRequestHandler handler = new TestRequestHandler(new ThrowingProcessor());

        Response first = handler.process(requestWithHeaders(JsonObject.EMPTY));
        Response second = handler.process(requestWithHeaders(JsonObject.EMPTY));

        assertNotEquals(
                first.asJsonObject().get("requestId").getString(),
                second.asJsonObject().get("requestId").getString());
    }

    @Test
    void successfulResponseIsUnchangedNoRequestIdAdded() {
        TestRequestHandler handler = new TestRequestHandler(new RespondingProcessor(200));

        Response response = handler.process(requestWithHeaders(JsonObject.EMPTY));

        assertEquals(200, response.getStatus());
        assertNull(response.asJsonObject().get("requestId"));
    }
}
