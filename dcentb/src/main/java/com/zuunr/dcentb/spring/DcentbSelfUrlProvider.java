package com.zuunr.dcentb.spring;

/**
 * Supplies the base URL (scheme+host+port) a Processor should use to make a real
 * outbound HTTP call back to this same dcentb backend, configured via
 * "dcentb.selfBaseUrl". Wrapped in a dedicated class (rather than exposing a raw String
 * bean) so it doesn't collide by type with any other String bean in a consuming app.
 *
 * dcentb supplies only where to call - not a bespoke HTTP client. A caller uses its own
 * HTTP client (e.g. plain java.net.http.HttpClient, the existing precedent in this
 * module - see JwtAuthenticator) and attaches real credentials to the request exactly
 * as any external caller would; nothing about authentication is bypassed for self-calls.
 */
public class DcentbSelfUrlProvider {

    private final String baseUrl;

    public DcentbSelfUrlProvider(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
