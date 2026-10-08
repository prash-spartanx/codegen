package com.prashant.codegen.generator;

import com.prashant.codegen.model.EntitySpec;
import com.prashant.codegen.model.FieldSpec;
import com.prashant.codegen.model.JwtAuthSpec;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class JwtAuthGenerator {

    public String generate(JwtAuthSpec jwtAuth,
                           EntitySpec authEntity,
                           Configuration cfg) throws IOException, TemplateException {

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("jwtAuth", jwtAuth);
        dataModel.put("authEntity", authEntity);
        dataModel.put("identityField", resolveIdentityField(authEntity));

        Template template = cfg.getTemplate("auth.py.ftl");
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return out.toString();
    }

    public String generateService(String serviceName,
                                  JwtAuthSpec jwtAuth,
                                  EntitySpec authEntity,
                                  Configuration cfg) throws IOException, TemplateException {

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("jwtAuth", jwtAuth);
        dataModel.put("authEntity", authEntity);
        dataModel.put("identityField", resolveIdentityField(authEntity));

        String templateName;
        switch (serviceName) {
            case "register_user": templateName = "register_service.py.ftl"; break;
            case "login_user":    templateName = "login_service.py.ftl";    break;
            default: throw new IllegalArgumentException("Unsupported auth service: " + serviceName);
        }

        Template template = cfg.getTemplate(templateName);
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return out.toString();
    }

    /**
     * The auth entity's identity field — the unique string field used for login.
     * For User: "email". Falls back to "email" if nothing matches.
     */
    private static String resolveIdentityField(EntitySpec authEntity) {
        if (authEntity.getFields() != null) {
            for (FieldSpec f : authEntity.getFields()) {
                if (!f.isPrimaryKey()
                        && "str".equals(f.getType())
                        && f.isUnique()) {
                    return f.getName();
                }
            }
        }
        return "email";
    }

    /**
     * Generates a service file for a specific auth-related handler.
     * Supported service names: "register_user", "login_user".
     */
    public String generateService(String serviceName, JwtAuthSpec jwtAuth, Configuration cfg)
            throws IOException, TemplateException {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("jwtAuth", jwtAuth);

        // Select template based on service name
        String templateName;
        switch (serviceName) {
            case "register_user":
                templateName = "register_service.py.ftl";
                break;
            case "login_user":
                templateName = "login_service.py.ftl";
                break;
            default:
                throw new IllegalArgumentException("Unsupported auth service: " + serviceName);
        }

        Template template = cfg.getTemplate(templateName);
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return out.toString();
    }
}