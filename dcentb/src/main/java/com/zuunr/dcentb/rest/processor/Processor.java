package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.controller.RequestHandlerConfig;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public abstract class Processor {

    public static final String X_DCENTB = "x-dcentb";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String MONGODB = "mongodb";

    /**
     * Reserved permission name, granted only via {@code INTERNAL_PRINCIPAL} (see below) -
     * never declarable in any collection's own {@code x-dcentb.collections.*.permissions}.
     * A core dcentb capability, not per-collection/per-API config: authorized for anything
     * any role could do via the API, on every collection, with no request/response
     * filtering - see SystemApiClient and PreOperationAccessController/ResponseAccessController.
     */
    public static final String SUPERUSER_PERMISSION = "SUPERUSER";

    /**
     * Internal requestContext key telling ResponseAccessController to skip response
     * filtering entirely - set by PreOperationAccessController when the caller is
     * SUPERUSER, and also by the HTML UI's own framework-owned handlers (the "/ui"
     * dashboard, login, logout - see html-ui-rendering-design.md section 2/8) whose
     * response is never a per-collection API resource with a field-level response schema
     * to filter against in the first place.
     */
    public static final String UNRESTRICTED_ACCESS = "unrestrictedAccess";

    /**
     * Internal requestContext key: this response's body is already a complete, final HTML
     * string, not JSON data for HtmlRenderingProcessor to render - set by the HTML UI's
     * hand-written pages (login, logout) that have no JSON resource behind them, the same
     * way "/swagger" is hand-written outside the generic rendering machinery entirely. See
     * html-ui-rendering-design.md section 8.
     */
    public static final String HTML_ALREADY_RENDERED = "htmlAlreadyRendered";

    /**
     * Top-level key on the request JsonObject (a sibling of "method"/"uri"/"headers"/"body"),
     * set only by SystemApiClient to grant SUPERUSER - by reference, not by header. The only
     * code path from a real inbound HTTP request to a Request object (RequestUtil.createRequest)
     * never sets this key, so no external HTTP request can ever trigger it, regardless of what
     * headers or body it sends.
     */
    public static final String INTERNAL_PRINCIPAL = "internalPrincipal";

    protected RequestHandlerConfig requestHandlerConfig;

    public abstract JsonObject process(JsonObject requestContext);

    public Processor(JsonValue config){
        requestHandlerConfig = config.as(RequestHandlerConfig.class);
    }

    /**
     * For Processor subclasses built as Spring beans instead of reflectively via
     * config.as(...) (see ItemDecoratorProcessor) - no per-operation config is available
     * at construction time in that case, so requestHandlerConfig stays null.
     */
    protected Processor() {
    }

    @Override
    public String toString(){
        return getClass().getSimpleName();
    }
}
