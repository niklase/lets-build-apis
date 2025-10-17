package com.zuunr.api.objectgen;

import com.zuunr.json.JsonObject;
import freemarker.template.*;

import java.net.URL;
import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {

        /* ------------------------------------------------------------------------ */
        /* You should do this ONLY ONCE in the whole application life-cycle:        */

        /* Create and adjust the configuration singleton */
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setDirectoryForTemplateLoading(getTemplateDirectory());
        // Recommended settings for new projects:
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setSQLDateAndTimeTimeZone(TimeZone.getDefault());

        /* ------------------------------------------------------------------------ */
        /* You usually do these for MULTIPLE TIMES in the application life-cycle:   */

        /* Create a data-model */
        JsonObject root = JsonObject.EMPTY;


        root = root.put("user", "Big Joe");
        JsonObject latest = JsonObject.EMPTY;
        latest = latest.put("url", "products/greenmouse.html");
        latest = latest.put("name", "green mouse");
        root = root.put("latestProduct", latest);

        /* Get the template (uses cache internally) */
        Template temp = cfg.getTemplate("test.ftlh");

        /* Merge data-model with template */
        Writer out = new OutputStreamWriter(System.out);
        temp.process(root.asMapsAndLists(), out);
        // Note: Depending on what `out` is, you may need to call `out.close()`.
        // This is usually the case for file output, but not for servlet output.

    }

    public static File getTemplateDirectory() {
        // Get the URL of the root level of resources
        URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("");
        Objects.requireNonNull(resourceUrl, "Resource directory not found!");

        // Convert the URL to a File object
        return new File(resourceUrl.getPath());
    }
}