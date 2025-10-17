package com.zuunr.mermaidschema;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.net.URL;
import java.util.Objects;
import java.util.TimeZone;

@org.springframework.context.annotation.Configuration
public class FreeMarkerConfig {


    @Bean
    public Configuration createConfig() throws Exception {

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
        return cfg;
    }

    public static File getTemplateDirectory() {
        // Get the URL of the root level of resources
        URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("");
        Objects.requireNonNull(resourceUrl, "Resource directory not found!");

        // Convert the URL to a File object
        return new File(resourceUrl.getPath());
    }
}