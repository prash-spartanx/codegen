package com.prashant.codegen.generator;

import com.prashant.codegen.model.ProjectSpec;
import com.prashant.codegen.model.RouterSpec;
import com.prashant.codegen.util.NamingUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainGenerator {

    /**
     * Information required by main.py.ftl for each router.
     */
    public static class RouterInfo {

        private String modulePath;
        private String aliasName;

        public RouterInfo() {
        }

        public RouterInfo(String modulePath, String aliasName) {
            this.modulePath = modulePath;
            this.aliasName = aliasName;
        }

        public String getModulePath() {
            return modulePath;
        }

        public String getAliasName() {
            return aliasName;
        }

        public void setModulePath(String modulePath) {
            this.modulePath = modulePath;
        }

        public void setAliasName(String aliasName) {
            this.aliasName = aliasName;
        }
    }

    public String generate(
            ProjectSpec spec,
            Configuration cfg)
            throws IOException, TemplateException {

        // ============================================================
        // 1. Guard against null routers
        // ============================================================

        List<RouterSpec> routers = spec.getRouters();

        if (routers == null) {
            routers = new ArrayList<>();
        }

        // ============================================================
        // 2. Convert RouterSpec -> RouterInfo
        // ============================================================

        List<RouterInfo> routerInfos =
                new ArrayList<>();

        for (RouterSpec routerSpec : routers) {

            if (routerSpec == null) {
                continue;
            }

            RouterInfo info =
                    new RouterInfo();

            // Example:
            // TaskRouter -> task_router
            String snakeName =
                    NamingUtils.toSnakeCase(
                            routerSpec.getName()
                    );

            info.setModulePath(
                    snakeName
            );

            info.setAliasName(
                    snakeName
            );

            routerInfos.add(info);
        }

        // ============================================================
        // 3. Build FreeMarker data model
        // ============================================================

        Map<String, Object> dataModel =
                new HashMap<>();

        dataModel.put(
                "routers",
                routerInfos
        );

        // Project name
        dataModel.put(
                "projectName",
                spec.getName() != null
                        ? spec.getName()
                        : ""
        );

        // Project description
        dataModel.put(
                "projectDescription",
                spec.getDescription() != null
                        ? spec.getDescription()
                        : ""
        );

        // ============================================================
        // 4. JWT configuration
        //
        // IMPORTANT:
        // Only add this if ProjectSpec actually contains a JWT
        // configuration/getter.
        //
        // If your ProjectSpec has:
        //
        //     boolean isJwtAuth()
        //
        // use:
        //
        // dataModel.put("jwtAuth", spec.isJwtAuth());
        //
        // If it has:
        //
        //     getJwtAuth()
        //
        // use:
        //
        // dataModel.put("jwtAuth", spec.getJwtAuth());
        //
        // ============================================================

        /*
        dataModel.put(
                "jwtAuth",
                spec.isJwtAuth()
        );
        */

        // ============================================================
        // 5. Render template
        // ============================================================

        Template template =
                cfg.getTemplate(
                        "main.py.ftl"
                );

        StringWriter out =
                new StringWriter();

        template.process(
                dataModel,
                out
        );

        return out.toString();
    }
}