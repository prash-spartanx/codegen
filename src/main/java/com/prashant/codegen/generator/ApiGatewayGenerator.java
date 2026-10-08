package com.prashant.codegen.generator;

import com.prashant.codegen.model.ApiGatewaySpec;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class ApiGatewayGenerator {
    public String generate(ApiGatewaySpec spec , Configuration cfg) throws IOException, TemplateException{
        Map<String,Object> data = new HashMap<>();
        data.put("routes",spec.getRoutes());
        var template = cfg.getTemplate("api_gateway.py.ftl");
        StringWriter out = new StringWriter();
        template.process(data,out);
        return out.toString();
    }
}
