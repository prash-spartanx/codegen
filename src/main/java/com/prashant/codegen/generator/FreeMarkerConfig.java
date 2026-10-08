package com.prashant.codegen.generator;

import freemarker.cache.NullCacheStorage;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;

public class FreeMarkerConfig {

    public static Configuration build() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);

        cfg.setClassForTemplateLoading(FreeMarkerConfig.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setTemplateUpdateDelayMilliseconds(0);
        cfg.setCacheStorage(new freemarker.cache.NullCacheStorage());



        return cfg;
    }
}