package com.prashant.codegen.generator;

import com.prashant.codegen.model.EndpointSpec;
import com.prashant.codegen.model.RouterSpec;
import com.prashant.codegen.util.NamingUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public class RouterGenerator {

    public String generate(RouterSpec router, Configuration cfg) throws IOException, TemplateException {

        Set<String> schemaImportSet = new HashSet<>();
        List<Map<String, Object>> endpointsForTemplate = new ArrayList<>();

        boolean hasListResponse = false;
        boolean hasDelete       = false;

        if (router.getEndpoints() != null) {
            for (EndpointSpec endpoint : router.getEndpoints()) {

                Map<String, Object> ep = new HashMap<>();

                String method = endpoint.getMethod() == null
                        ? "get"
                        : endpoint.getMethod().toLowerCase();

                String responseBody = endpoint.getResponseBody();
                String requestBody  = endpoint.getRequestBody();

                boolean isDelete       = "delete".equals(method);
                boolean isListResponse = false;
                String  responseModel  = "";

                if (!isDelete
                        && responseBody != null
                        && !responseBody.isBlank()) {

                    if (responseBody.startsWith("List[")
                            && responseBody.endsWith("]")) {

                        String inner = responseBody.substring(
                                5,
                                responseBody.length() - 1);

                        String base = inner.endsWith("Response")
                                ? inner.substring(0, inner.length() - "Response".length())
                                : inner;

                        responseModel  = base + "ListResponse";
                        isListResponse = true;
                        hasListResponse = true;

                        schemaImportSet.add(responseModel);

                    } else if (responseBody.endsWith("ListResponse")) {
                        responseModel   = responseBody;
                        isListResponse  = true;
                        hasListResponse = true;
                        schemaImportSet.add(responseBody);
                    } else {
                        responseModel = responseBody;
                        schemaImportSet.add(responseBody);
                    }
                }

                if (requestBody != null && !requestBody.isBlank()) {
                    schemaImportSet.add(requestBody);
                }

                // ---- path params ----
                List<String> pathParams = new ArrayList<>();
                if (endpoint.getPath() != null) {
                    for (String part : endpoint.getPath().split("/")) {
                        if (part.startsWith("{") && part.endsWith("}")) {
                            pathParams.add(
                                    part.substring(1, part.length() - 1));
                        }
                    }
                }

                if (isDelete) {
                    hasDelete = true;
                }

                ep.put("handler",        endpoint.getHandler());
                ep.put("method",         method);
                ep.put("path",           endpoint.getPath() == null ? "" : endpoint.getPath());
                ep.put("requestBody",    requestBody);
                ep.put("responseModel",  responseModel);
                ep.put("isListResponse", isListResponse);
                ep.put("isDelete",       isDelete);
                ep.put("pathParams",     pathParams);

                endpointsForTemplate.add(ep);
            }
        }

        List<String> sortedSchemaImports = new ArrayList<>(schemaImportSet);
        Collections.sort(sortedSchemaImports);

        String schemaModule = NamingUtils.getSchemaModuleName(router.getName());
        String routerTag    = NamingUtils.getRouterTag(router.getName());

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("router",          router);
        dataModel.put("endpoints",       endpointsForTemplate);
        dataModel.put("schemaImports",   sortedSchemaImports);
        dataModel.put("schemaModule",    schemaModule);
        dataModel.put("routerTag",       routerTag);
        dataModel.put("authRequired",    router.isAuthRequired());
        dataModel.put("hasListResponse", hasListResponse);
        dataModel.put("hasDelete",       hasDelete);

        Template template = cfg.getTemplate("router.py.ftl");
        StringWriter writer = new StringWriter();
        template.process(dataModel, writer);
        return writer.toString();
    }

    private String stripListWrapper(String typeStr) {
        if (typeStr == null) return null;
        if (typeStr.startsWith("List[") && typeStr.endsWith("]")) {
            return typeStr.substring(5, typeStr.length() - 1);
        }
        return typeStr;
    }
}