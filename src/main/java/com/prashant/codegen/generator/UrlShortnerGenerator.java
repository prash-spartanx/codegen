package com.prashant.codegen.generator;

import com.prashant.codegen.model.UrlShortenerSpec;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class UrlShortnerGenerator {
    public String generate(UrlShortenerSpec spec , Configuration cfg) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put("baseUrlEnv", spec.getBaseUrl());
        data.put("codeLength", spec.getCodeLength());
        data.put("expiryDays", spec.getExpiryDays());
        data.put("analytics", spec.isAnalytics());

        var template = cfg.getTemplate("url_shortener.py.ftl");
        StringWriter out = new StringWriter();
        template.process(data,out);
        return out.toString();

    }
}
