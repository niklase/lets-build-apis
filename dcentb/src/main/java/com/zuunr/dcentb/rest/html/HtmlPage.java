package com.zuunr.dcentb.rest.html;

/** The full-page shell wrapped around a fragment on a non-htmx (first/full) navigation - see design doc section 7. */
public final class HtmlPage {

    private static final String CSS = """
            body { font-family: system-ui, -apple-system, sans-serif; max-width: 900px; margin: 2rem auto; padding: 0 1rem; color: #1a1a1a; }
            table { border-collapse: collapse; width: 100%; margin-top: 0.5rem; }
            th, td { border: 1px solid #ddd; padding: 0.4rem 0.6rem; text-align: left; vertical-align: top; }
            th { background: #f5f5f5; }
            dl { margin: 0; }
            dt { font-weight: 600; margin-top: 0.6rem; }
            dd { margin: 0; }
            dd dl { margin-left: 1rem; }
            nav.pagination a { margin-right: 1rem; }
            .empty { color: #888; }
            header { margin-bottom: 1.5rem; border-bottom: 1px solid #ddd; padding-bottom: 0.5rem; }
            """;

    private HtmlPage() {
    }

    public static String fullPage(String title, String fragment) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + HtmlEscaper.escape(title) + "</title>"
                + "<script src=\"https://unpkg.com/htmx.org@2.0.3\"></script>"
                + "<style>" + CSS + "</style></head>"
                + "<body hx-headers='{\"Accept\":\"text/html\"}'>"
                + "<header><a href=\"/ui\">Collections</a></header>"
                + "<main>" + fragment + "</main>"
                + "</body></html>";
    }
}
