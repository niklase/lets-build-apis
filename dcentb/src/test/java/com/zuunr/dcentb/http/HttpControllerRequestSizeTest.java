package com.zuunr.dcentb.http;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.Controller;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real servlet request-parsing path (HttpController -> RequestUtil), unlike
 * ControllerIT which builds a Request straight from parsed JSON and never reads raw bytes.
 * @author Niklas Eldberger
 */
class HttpControllerRequestSizeTest {

    private static final int MAX_BODY_SIZE = 100;
    private static final int OVERSIZED_LENGTH = MAX_BODY_SIZE + 1;

    private final Controller controller = mock(Controller.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new HttpController(controller, new RequestUtil(MAX_BODY_SIZE)))
            .build();

    @Test
    void rejectsBodyDeclaredTooLargeViaContentLengthHeader() throws Exception {
        String body = "a".repeat(OVERSIZED_LENGTH);

        mockMvc.perform(post("/students")
                        .contentType("application/json")
                        .header("Content-Length", String.valueOf(body.length()))
                        .content(body))
                .andExpect(status().is(413));

        verify(controller, never()).execute(any());
    }

    @Test
    void rejectsBodyTooLargeWhenContentLengthHeaderIsAbsent() throws Exception {
        String body = "a".repeat(OVERSIZED_LENGTH);

        // Chunked transfer encoding has no Content-Length, so this exercises the
        // in-loop cap in RequestUtil#createStringBody rather than the header check.
        // (MockMvc would otherwise auto-add an accurate Content-Length header for us.)
        mockMvc.perform(post("/students")
                        .contentType("application/json")
                        .header("Transfer-Encoding", "chunked")
                        .content(body))
                .andExpect(status().is(413));

        verify(controller, never()).execute(any());
    }

    @Test
    void acceptsBodyWithinLimit() throws Exception {
        when(controller.execute(any(Request.class)))
                .thenReturn(Response.create(201, JsonObject.EMPTY.put("name", "Anna").jsonValue()));

        mockMvc.perform(post("/students")
                        .contentType("application/json")
                        .content("{\"name\":\"Anna\"}"))
                .andExpect(status().isCreated());

        verify(controller).execute(any());
    }
}
