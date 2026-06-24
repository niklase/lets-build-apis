package com.zuunr.dcentb.http;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.controller.Controller;
import com.zuunr.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * @author Niklas Eldberger
 */

@Order(Ordered.LOWEST_PRECEDENCE)
@RestController
public class HttpController {

    private final RequestUtil requestUtil = new RequestUtil();
    private final Controller restController;

    public HttpController(@Autowired Controller restController) {
        this.restController = restController;
    }

    @PutMapping(value = "**")
    public ResponseEntity<String> putAny(HttpServletRequest serverHttpRequest) {
        return executeRestOperation(serverHttpRequest);
    }

    @PatchMapping(value = "**")
    public ResponseEntity<String> patchAny(HttpServletRequest serverHttpRequest) {
        return executeRestOperation(serverHttpRequest);
    }

    @PostMapping(value = "**")
    public ResponseEntity<String> postAny(HttpServletRequest serverHttpRequest) {
        return executeRestOperation(serverHttpRequest);
    }

    @GetMapping(value = "**")
    public ResponseEntity<String> getAny(HttpServletRequest serverHttpRequest) {
        return executeRestOperation(serverHttpRequest);
    }

    @DeleteMapping(value = "**")
    public ResponseEntity<String> deleteAny(HttpServletRequest serverHttpRequest) {
        return executeRestOperation(serverHttpRequest);
    }

    private ResponseEntity<String> executeRestOperation(HttpServletRequest httpRequest) {
        Request request;
        try {
            request = requestUtil.createRequest(httpRequest);
        } catch (IOException ioException) {
            return ResponseEntity.status(400).body(JsonObject.EMPTY.put("error", "Invalid request").jsonValue().asJson());
        }
        return requestUtil.createJsonResponseEntity(restController.execute(request));
    }
}