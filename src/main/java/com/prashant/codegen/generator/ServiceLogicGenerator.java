package com.prashant.codegen.generator;

import com.prashant.codegen.llm.*;
import com.prashant.codegen.model.*;
import com.prashant.codegen.util.NamingUtils;
import com.prashant.codegen.util.SchemaRegistry;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
@Component
public class ServiceLogicGenerator {

    private final Configuration cfg;
    private final LlmClientFactory clientFactory;
    private final PromptBuilder promptBuilder;
    private final LlmOutputCleaner outputCleaner;

    /*
     * Maximum number of times the LLM may regenerate a method when
     * entity-field validation fails.
     */
    private static final int MAX_METHOD_GENERATION_ATTEMPTS = 3;

    @Autowired
    public ServiceLogicGenerator(
            Configuration cfg,
            LlmClientFactory clientFactory,
            PromptBuilder promptBuilder,
            LlmOutputCleaner outputCleaner) {
        this.cfg = cfg;
        this.clientFactory = clientFactory;
        this.promptBuilder = promptBuilder;
        this.outputCleaner = outputCleaner;
    }


    public String generate(ServiceLogicSpec service, ProjectSpec projectSpec,String provider ,String modelName)
            throws IOException, TemplateException {

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("service", service);

        // 1. Build entity map for PromptBuilder
        Map<String, EntitySpec> entityMap = projectSpec.getEntities().stream()
                .collect(Collectors.toMap(EntitySpec::getName, e -> e));

        // 2. Determine owner entity (first dependsOn that's an entity)
        EntitySpec ownerEntity = null;

        if (service.getDependsOn() != null
                && !service.getDependsOn().isEmpty()) {

            String firstDep = service.getDependsOn().get(0);
            ownerEntity = entityMap.get(firstDep);
        }

        List<String> failedMethods = new ArrayList<>();

        // Stores the final accepted method bodies
        Map<String, String> methodBodies = new LinkedHashMap<>();

        /*
         * NEW:
         * Keep track of entity classes referenced directly inside generated
         * method bodies. These will later be added to modelsMap.
         */
        Set<String> bodyReferencedEntities = new HashSet<>();

        // 3. Generate method bodies via LLM for each method
        for (MethodSpec method : service.getMethods()) {

            boolean methodAccepted = false;
            String previousErrorMessage = null;

            for (int attempt = 1;
                 attempt <= MAX_METHOD_GENERATION_ATTEMPTS;
                 attempt++) {

                try {

                    String prompt = promptBuilder.buildPrompt(
                            method,
                            entityMap,
                            ownerEntity,
                            service.getName(),
                            previousErrorMessage
                    );

                    System.out.println(
                            "DEBUG: Prompt for "
                                    + service.getName()
                                    + "."
                                    + method.getName()
                                    + " (attempt "
                                    + attempt
                                    + "/"
                                    + MAX_METHOD_GENERATION_ATTEMPTS
                                    + "):\n"
                                    + prompt
                    );
                    LlmClient client = clientFactory.getClient(provider);
                    String rawResponse =
                            client.generateCode(
                                    modelName,
                                    prompt
                            );

                    if (rawResponse == null) {
                        throw new IOException(
                                "LLM returned no candidates for "
                                        + service.getName()
                                        + "."
                                        + method.getName()
                        );
                    }

                    String cleanedBody =
                            outputCleaner.clean(rawResponse, 8);

                    System.out.println(
                            "DEBUG: Cleaned body for "
                                    + service.getName()
                                    + "."
                                    + method.getName()
                                    + ":\n"
                                    + cleanedBody
                    );
                    cleanedBody = substituteKnownFkMistakes(cleanedBody, entityMap);
                    /*
                     * NEW:
                     * Validate references such as:
                     *
                     * Project.owner_id
                     * Task.assigned_to
                     * User.id
                     *
                     * against the actual EntitySpec fields.
                     */
                    Set<String> primaryNames = computePrimaryEntities(method, entityMap);
                    EntitySpec primaryEntity = primaryNames.isEmpty()
                            ? null
                            : entityMap.get(primaryNames.iterator().next());

                    validateEntityFieldReferences(cleanedBody, entityMap);
                    validateNoRelationshipColumnChain(cleanedBody, entityMap);
                    validateNoRelationshipQueryChain(cleanedBody, entityMap);
                    validateRequiredColumnsPresent(cleanedBody, method, primaryEntity);
                    validateNoOneCall(cleanedBody);
                    /*
                     * NEW:
                     * Remember entities referenced inside the accepted
                     * method body so their imports can be generated later.
                     */
                    bodyReferencedEntities.addAll(
                            extractReferencedEntities(
                                    cleanedBody,
                                    entityMap.keySet()
                            )
                    );

                    /*
                     * Only put the body into methodBodies after ALL
                     * validation succeeds.
                     */
                    methodBodies.put(
                            method.getName(),
                            cleanedBody
                    );

                    methodAccepted = true;

                    System.out.println(
                            "DEBUG: Accepted generated body for "
                                    + service.getName()
                                    + "."
                                    + method.getName()
                    );

                    break;

                } catch (Exception e) {

                    // Store the exact failure reason for the next LLM attempt.
                    previousErrorMessage = e.getMessage();

                    System.err.println(
                            "WARN: Generation/validation failed for "
                                    + service.getName()
                                    + "."
                                    + method.getName()
                                    + " on attempt "
                                    + attempt
                                    + "/"
                                    + MAX_METHOD_GENERATION_ATTEMPTS
                                    + ": "
                                    + e.getMessage()
                    );

                    if (attempt == MAX_METHOD_GENERATION_ATTEMPTS) {

                        failedMethods.add(
                                service.getName()
                                        + "."
                                        + method.getName()
                        );
                    }
                }
            }

            if (!methodAccepted) {

                throw new IOException(
                        "Failed to generate a valid body for "
                                + service.getName()
                                + "."
                                + method.getName()
                                + " after "
                                + MAX_METHOD_GENERATION_ATTEMPTS
                                + " attempts. Last error: "
                                + previousErrorMessage
                );
            }
        }

        // Build per-method descriptors so the template writes a signature that
// exactly matches the parameters declared in the spec, plus `db` (always).
// No unconditional current_user: public methods must not receive it.
        List<Map<String, Object>> methodsForTemplate = new ArrayList<>();

        for (MethodSpec method : service.getMethods()) {

            Map<String, Object> m = new HashMap<>();
            m.put("name",    method.getName());
            m.put("returns", method.getReturns());
            m.put("intent",  method.getIntent());

            boolean needsCurrentUser = false;
            List<Map<String, String>> simpleParams = new ArrayList<>();

            if (method.getParams() != null) {
                for (ParamSpec p : method.getParams()) {
                    if ("current_user".equals(p.getName())) {
                        needsCurrentUser = true;
                    } else if (!"db".equals(p.getName())) {
                        Map<String, String> pm = new HashMap<>();
                        pm.put("name", p.getName());
                        pm.put("type", p.getType());
                        simpleParams.add(pm);
                    }
                }
            }

            m.put("needsCurrentUser", needsCurrentUser);
            m.put("simpleParams",     simpleParams);
            m.put("body",             methodBodies.get(method.getName()));

            methodsForTemplate.add(m);
        }

        dataModel.put("methods", methodsForTemplate);

        dataModel.put("methodBodies", methodBodies);

        // 4. Collect bare types using Regex (Solves Issue #2 and #3)
        Set<String> bareTypes = new HashSet<>();

        for (MethodSpec method : service.getMethods()) {

            bareTypes.addAll(
                    extractCustomTypes(
                            method.getReturns()
                    )
            );

            if (method.getParams() != null) {

                for (ParamSpec param : method.getParams()) {

                    bareTypes.addAll(
                            extractCustomTypes(
                                    param.getType()
                            )
                    );
                }
            }
        }

        // Build the type-to-module map from project spec
        Map<String, String> typeToSchemaMap =
                SchemaRegistry.buildTypeToSchemaModuleMap(
                        projectSpec
                );

        Set<String> entityNames =
                projectSpec.getEntities().stream()
                        .map(EntitySpec::getName)
                        .collect(Collectors.toSet());

        Map<String, Set<String>> modelsMap =
                new LinkedHashMap<>();

        Map<String, Set<String>> schemasMap =
                new LinkedHashMap<>();

        for (String type : bareTypes) {

            if (entityNames.contains(type)) {

                String module =
                        NamingUtils.toSnakeCase(type);

                modelsMap
                        .computeIfAbsent(
                                module,
                                k -> new TreeSet<>()
                        )
                        .add(type);

            } else {

                // Look up schema module from central registry,
                // fallback to NamingUtils
                String module =
                        typeToSchemaMap.get(type);

                if (module == null) {

                    String base =
                            stripSuffixes(
                                    type,
                                    "Create",
                                    "Update",
                                    "Response",
                                    "Request",
                                    "Login",
                                    "Register"
                            );

                    module =
                            NamingUtils.getSchemaModuleName(
                                    base
                            );
                }

                schemasMap
                        .computeIfAbsent(
                                module,
                                k -> new TreeSet<>()
                        )
                        .add(type);
            }
        }

        /*
         * NEW:
         *
         * Add entities that were referenced ONLY inside the generated
         * method body.
         *
         * This uses the SAME modelsMap already used by the existing
         * import-generation pipeline.
         *
         * No new import map is necessary.
         */
        for (String entityName : bodyReferencedEntities) {

            String module =
                    NamingUtils.toSnakeCase(entityName);

            modelsMap
                    .computeIfAbsent(
                            module,
                            k -> new TreeSet<>()
                    )
                    .add(entityName);
        }
        /*
         * The service template always writes `current_user: User` into every
         * method signature, regardless of whether the body uses it. So the auth
         * entity must always be imported, even when the method is public
         * (e.g. list_products, get_book) and never references it.
         */
        String authEntityName = findAuthEntityName(projectSpec);

        if (authEntityName != null) {
            String authModule =
                    NamingUtils.toSnakeCase(authEntityName);

            modelsMap
                    .computeIfAbsent(
                            authModule,
                            k -> new TreeSet<>()
                    )
                    .add(authEntityName);
        }

        Map<String, List<String>> modelsImports =
                new TreeMap<>();

        modelsMap.forEach(
                (mod, set) ->
                        modelsImports.put(
                                mod,
                                new ArrayList<>(set)
                        )
        );

        Map<String, List<String>> schemasImports =
                new TreeMap<>();

        schemasMap.forEach(
                (mod, set) ->
                        schemasImports.put(
                                mod,
                                new ArrayList<>(set)
                        )
        );

        dataModel.put(
                "modelsImports",
                modelsImports
        );

        dataModel.put(
                "schemasImports",
                schemasImports
        );

        // Check for List return
        boolean needsTypingList =
                service.getMethods().stream()
                        .anyMatch(
                                m ->
                                        m.getReturns() != null
                                                && (
                                                m.getReturns()
                                                        .startsWith("List[")
                                                        ||
                                                        m.getReturns()
                                                                .startsWith("list[")
                                        )
                        );

        dataModel.put(
                "needsTypingList",
                needsTypingList
        );

        // 5. Render the template
        Template template =
                cfg.getTemplate(
                        "service.py.ftl"
                );

        try (StringWriter writer =
                     new StringWriter()) {

            template.process(
                    dataModel,
                    writer
            );

            return writer.toString();
        }
    }

    /**
     * NEW:
     *
     * Finds Entity.field references in the generated method body and
     * verifies that the field actually exists in that EntitySpec.
     *
     * This intentionally validates ONLY field existence.
     * It does NOT attempt to validate business logic.
     */
    private void validateEntityFieldReferences(
            String body,
            Map<String, EntitySpec> entityMap)
            throws IOException {

        if (body == null || body.isBlank()) {
            return;
        }

        /*
         * Matches patterns such as:
         *
         * Project.owner_id
         * Project.user_id
         * Task.assigned_to
         * User.id
         */
        Pattern pattern =
                Pattern.compile(
                        "\\b([A-Za-z_]\\w*)\\s*\\.\\s*([A-Za-z_]\\w*)\\b"
                );

        Matcher matcher =
                pattern.matcher(body);

        while (matcher.find()) {

            String entityName =
                    matcher.group(1);

            String fieldName =
                    matcher.group(2);

            /*
             * Only validate Entity.field when the left-hand side is
             * actually one of our known entities.
             */
            EntitySpec entity =
                    entityMap.get(entityName);

            if (entity == null) {
                continue;
            }

            /*
             * A valid Entity.field reference can refer to either:
             *
             * 1. A scalar field such as:
             *      Task.project_id
             *
             * 2. A relationship field such as:
             *      Task.project
             */
            boolean scalarFieldExists =
                    entity.getFields() != null
                            && entity.getFields()
                            .stream()
                            .anyMatch(
                                    field ->
                                            fieldName.equals(
                                                    field.getName()
                                            )
                            );

            boolean relationshipFieldExists =
                    entity.getRelationships() != null
                            && entity.getRelationships()
                            .stream()
                            .anyMatch(
                                    relationship ->
                                            fieldName.equals(
                                                    relationship.getField()
                                            )
                            );

            boolean fieldExists =
                    scalarFieldExists
                            || relationshipFieldExists;

            if (!fieldExists) {

                // If the invalid field looks like <relationshipField>_id and the
// entity actually has a relationship named <relationshipField>,
// tell the LLM the correct column name explicitly. Otherwise the
// model sees the wrong name in the failure text and repeats it.
                String correction = null;

                if (fieldName.endsWith("_id") && entity.getRelationships() != null) {
                    String relField = fieldName.substring(0, fieldName.length() - 3);
                    for (RelationshipSpec r : entity.getRelationships()) {
                        if (!relField.equals(r.getField())) {
                            continue;
                        }
                        String correctFk = NamingUtils.resolveForeignKeyField(entity, r);
                        if (correctFk != null && !correctFk.equals(fieldName)) {
                            correction = correctFk;
                        }
                        break;
                    }
                }

                if (correction != null) {
                    throw new IOException(
                            "Generated code uses '"
                                    + entityName + "." + fieldName + "', which is invalid. "
                                    + "The correct FK column for the '"
                                    + fieldName.substring(0, fieldName.length() - 3)
                                    + "' relationship on '" + entityName + "' is '"
                                    + correction + "'. "
                                    + "Replace every occurrence of '"
                                    + entityName + "." + fieldName
                                    + "' with '" + entityName + "." + correction + "'. "
                                    + "Do not write '" + fieldName + "' anywhere in the method body."
                    );
                }

                throw new IOException(
                        "Generated code uses '"
                                + entityName + "." + fieldName
                                + "', which is not a field on entity '"
                                + entityName + "'. "
                                + "Use only field names listed for this entity in the prompt."
                );
            }
        }
    }

    /**
     * NEW:
     *
     * Finds known entity class names referenced inside the generated
     * method body.
     *
     * Example:
     *
     * body:
     *     task = db.query(Task).filter(...)
     *     project = db.query(Project).filter(...)
     *
     * result:
     *     Task
     *     Project
     */
    private Set<String> extractReferencedEntities(
            String body,
            Set<String> entityNames) {

        Set<String> referencedEntities =
                new HashSet<>();

        if (body == null || body.isBlank()) {
            return referencedEntities;
        }

        for (String entityName : entityNames) {

            Pattern pattern =
                    Pattern.compile(
                            "\\b"
                                    + Pattern.quote(entityName)
                                    + "\\b"
                    );

            Matcher matcher =
                    pattern.matcher(body);

            if (matcher.find()) {
                referencedEntities.add(
                        entityName
                );
            }
        }

        return referencedEntities;
    }
    /**
     * Rejects class-level relationship-column chains such as:
     *
     *     Book.branch.manager_id
     *     Task.project.owner_id
     *
     * These are always wrong: SQLAlchemy relationship attributes are
     * descriptors, not columns. The correct forms are:
     *
     *     Entity.relationship.has(Target.column == value)
     *     db.query(Entity).join(Target).filter(Target.column == value)
     *
     * Without this check the LLM intermittently produces the invalid form,
     * which raises AttributeError at request time.
     */
    private void validateNoRelationshipColumnChain(
            String body,
            Map<String, EntitySpec> entityMap)
            throws IOException {

        if (body == null || body.isBlank()) {
            return;
        }

        Pattern pattern = Pattern.compile(
                "\\b([A-Z]\\w*)\\s*\\.\\s*([a-z_]\\w*)\\s*\\.\\s*([a-z_]\\w*)\\b"
        );

        Matcher matcher = pattern.matcher(body);

        while (matcher.find()) {

            String entityName        = matcher.group(1);
            String relationshipField = matcher.group(2);
            String memberName        = matcher.group(3);

            EntitySpec entity = entityMap.get(entityName);
            if (entity == null) {
                continue;
            }

            RelationshipSpec rel = null;
            if (entity.getRelationships() != null) {
                for (RelationshipSpec r : entity.getRelationships()) {
                    if (relationshipField.equals(r.getField())) {
                        rel = r;
                        break;
                    }
                }
            }
            if (rel == null) {
                continue;
            }

            // Only flag it when the third identifier is actually a field on the
            // target entity. If it is a SQLAlchemy method like `has`, `any`,
            // `is_`, we leave it alone.
            EntitySpec target = entityMap.get(rel.getTarget());
            if (target == null) {
                continue;
            }

            boolean memberIsScalarField = false;
            if (target.getFields() != null) {
                for (FieldSpec f : target.getFields()) {
                    if (memberName.equals(f.getName())) {
                        memberIsScalarField = true;
                        break;
                    }
                }
            }
            if (!memberIsScalarField) {
                continue;
            }

            throw new IOException(
                    "Generated code uses a class-level relationship-column chain '"
                            + entityName + "." + relationshipField + "." + memberName
                            + "', which is invalid in SQLAlchemy. "
                            + "Relationship attributes are not columns. "
                            + "Use either '"
                            + entityName + "." + relationshipField + ".has("
                            + rel.getTarget() + "." + memberName + " == ...)' "
                            + "or 'db.query(" + entityName + ").join("
                            + rel.getTarget() + ").filter("
                            + rel.getTarget() + "." + memberName + " == ...)'."
            );
        }
    }

    /**
     * Extracts only custom model/schema names from type hints like
     * "list[Project]" or "Optional[int]".
     * Filters out Python/SQLAlchemy built-in types.
     */
    private Set<String> extractCustomTypes(
            String typeStr) {

        Set<String> customTypes =
                new HashSet<>();

        if (typeStr == null
                || typeStr.isEmpty()) {

            return customTypes;
        }

        Set<String> builtIns =
                new HashSet<>(
                        Arrays.asList(
                                "int",
                                "str",
                                "float",
                                "bool",
                                "list",
                                "dict",
                                "set",
                                "tuple",
                                "bytes",
                                "Any",
                                "Optional",
                                "List",
                                "Dict",
                                "Set",
                                "Tuple",
                                "Union",
                                "UUID",
                                "datetime",
                                "date",
                                "time",
                                "timedelta",
                                "Session",
                                "UploadFile",
                                "File",
                                "None",
                                "null"
                        )
                );

        Matcher m =
                Pattern.compile(
                        "\\b[a-zA-Z_]\\w*\\b"
                ).matcher(typeStr);

        while (m.find()) {

            String word =
                    m.group();

            if (!builtIns.contains(word)) {
                customTypes.add(word);
            }
        }

        return customTypes;
    }
    private static final Set<String> QUERY_METHODS = Set.of(
            "filter", "filter_by", "count", "all", "first",
            "one", "one_or_none", "scalar", "order_by",
            "limit", "offset"
    );

    /**
     * Rejects class-level relationship-query chains such as:
     *
     *     User.loans.filter(...)
     *     Post.comments.count()
     *
     * Relationship attributes are not query objects; these raise
     * AttributeError at request time. Uses the same shape as
     * validateNoRelationshipColumnChain.
     */
    private void validateNoRelationshipQueryChain(
            String body,
            Map<String, EntitySpec> entityMap)
            throws IOException {

        if (body == null || body.isBlank()) {
            return;
        }

        Pattern pattern = Pattern.compile(
                "\\b([A-Z]\\w*)\\s*\\.\\s*([a-z_]\\w*)\\s*\\.\\s*([a-z_]\\w*)\\s*\\("
        );

        Matcher matcher = pattern.matcher(body);

        while (matcher.find()) {

            String entityName        = matcher.group(1);
            String relationshipField = matcher.group(2);
            String methodName        = matcher.group(3);

            if (!QUERY_METHODS.contains(methodName)) {
                continue;
            }

            EntitySpec entity = entityMap.get(entityName);
            if (entity == null || entity.getRelationships() == null) {
                continue;
            }

            boolean isRelationship = false;
            String target = null;
            for (RelationshipSpec r : entity.getRelationships()) {
                if (relationshipField.equals(r.getField())) {
                    isRelationship = true;
                    target = r.getTarget();
                    break;
                }
            }
            if (!isRelationship) {
                continue;
            }

            throw new IOException(
                    "Generated code calls '."
                            + methodName + "()' on relationship attribute '"
                            + entityName + "." + relationshipField
                            + "'. Relationship attributes are not queries. "
                            + "Use db.query(" + target + ").filter(...)"
                            + (("count".equals(methodName))
                            ? ".count()"
                            : ".all() / .first() / etc.")
                            + " instead."
            );
        }
    }

    private String stripSuffixes(
            String type,
            String... suffixes) {

        for (String suffix : suffixes) {

            if (type.endsWith(suffix)) {

                return type.substring(
                        0,
                        type.length()
                                - suffix.length()
                );
            }
        }

        return type;
    }
    /**
     * For create-style methods, verify that the primary entity's constructor
     * includes every required (non-nullable, no-default, non-PK) column.
     *
     * A missing required column means the generated code will raise a
     * NOT NULL violation at runtime. We reject before that reaches the DB.
     */
    private void validateRequiredColumnsPresent(
            String body,
            MethodSpec method,
            EntitySpec primaryEntity)
            throws IOException {

        if (body == null || body.isBlank()
                || primaryEntity == null
                || primaryEntity.getFields() == null) {
            return;
        }

        String methodName = method.getName() == null
                ? ""
                : method.getName().toLowerCase();

        if (!methodName.startsWith("create_")) {
            return;
        }

        // Did the body construct the primary entity?
        String ctorToken = primaryEntity.getName() + "(";
        if (!body.contains(ctorToken)) {
            return;
        }

        for (FieldSpec f : primaryEntity.getFields()) {

            if (f.isPrimaryKey()) continue;

            boolean required = !f.isNullable() && f.getDefaultValue() == null;
            if (!required) continue;

            // Match 'field = ...' or 'field=...' but not 'field == ...'
            Pattern kwarg = Pattern.compile(
                    "\\b" + Pattern.quote(f.getName()) + "\\s*=(?!=)"
            );

            if (!kwarg.matcher(body).find()) {
                throw new IOException(
                        "Generated body for " + method.getName()
                                + " constructs " + primaryEntity.getName()
                                + "(...) without the required column '"
                                + f.getName() + "'. "
                                + "This would raise a NOT NULL violation at runtime. "
                                + "If '" + f.getName() + "' is an ownership FK, set it to "
                                + "current_user.id. Otherwise set it from the request."
                );
            }
        }
    }
    /**
     * The entities a method is "about". For create_loan that is {Loan}.
     * For update_book that is {Book}. For list_tasks that is {Task}.
     *
     * Used by validateRequiredColumnsPresent to know which entity's
     * constructor to check.
     *
     * Resolution:
     *   1. The return type, if it resolves to an entity.
     *   2. Any entity whose snake_case name appears in the method name.
     */
    private Set<String> computePrimaryEntities(
            MethodSpec method,
            Map<String, EntitySpec> entityMap) {

        Set<String> primary = new HashSet<>();

        // 1. The return type, if it resolves to an entity.
        String returnBare = stripLocalWrapper(method.getReturns());
        String returnEntityName = matchEntityName(returnBare, entityMap);
        if (returnEntityName != null) {
            primary.add(returnEntityName);
        }

        // 2. Any entity whose snake_case name appears in the method name.
        String methodName = method.getName() == null
                ? ""
                : method.getName().toLowerCase();

        if (!methodName.isEmpty()) {
            for (EntitySpec e : entityMap.values()) {
                String entitySnake = NamingUtils.toSnakeCase(e.getName());
                if (entitySnake.isEmpty()) {
                    continue;
                }
                if (methodName.contains(entitySnake)) {
                    primary.add(e.getName());
                }
            }
        }

        return primary;
    }

    /**
     * Strips List[...] / Optional[...] wrappers from a type hint so
     * the bare name can be matched against an entity.
     */
    private String stripLocalWrapper(String type) {

        if (type == null) {
            return null;
        }

        if (type.startsWith("List[") && type.endsWith("]")) {
            return type.substring(5, type.length() - 1);
        }

        if (type.startsWith("list[") && type.endsWith("]")) {
            return type.substring(5, type.length() - 1);
        }

        if (type.startsWith("Optional[") && type.endsWith("]")) {
            return type.substring(9, type.length() - 1);
        }

        return type;
    }

    /**
     * Matches a bare type name against the entity map, stripping
     * common schema suffixes (Create / Update / Response / Request)
     * so "LoanResponse" matches entity "Loan".
     */
    private String matchEntityName(
            String bareType,
            Map<String, EntitySpec> entityMap) {

        if (bareType == null || bareType.isEmpty()) {
            return null;
        }

        if (entityMap.containsKey(bareType)) {
            return bareType;
        }

        for (String suffix : new String[]{"Create", "Update", "Response", "Request"}) {
            if (bareType.endsWith(suffix)) {
                String base = bareType.substring(0, bareType.length() - suffix.length());
                if (entityMap.containsKey(base)) {
                    return base;
                }
            }
        }

        return null;
    }
    /**
     * The auth entity is the entity with a `password` field. For all
     * three existing specs and the shop spec that is User. Returns null
     * if no such entity is defined (a project with no auth).
     */
    private String findAuthEntityName(ProjectSpec projectSpec) {

        if (projectSpec == null || projectSpec.getEntities() == null) {
            return null;
        }

        for (EntitySpec e : projectSpec.getEntities()) {
            if (e.getFields() == null) continue;
            for (FieldSpec f : e.getFields()) {
                if ("password".equals(f.getName())) {
                    return e.getName();
                }
            }
        }

        return null;
    }
    private static final Pattern ONE_CALL = Pattern.compile(
            "\\.\\s*(one|scalar_one)\\s*\\("
    );

    /**
     * Rejects .one() and .scalar_one() lookups. These raise NoResultFound when
     * nothing matches, which becomes an uncaught 500 instead of a clean 404.
     */
    private void validateNoOneCall(String body) throws IOException {

        if (body == null || body.isBlank()) {
            return;
        }

        Matcher m = ONE_CALL.matcher(body);

        if (m.find()) {
            throw new IOException(
                    "Generated code uses '." + m.group(1) + "()' for a lookup. "
                            + "This raises NoResultFound when nothing matches, producing a 500. "
                            + "Use '.first()' and then "
                            + "'if not row: raise HTTPException(status_code=404, detail=\"... not found\")'."
            );
        }
    }
    /**
     * Before validation, rewrite any <Entity>.<relationshipField>_id reference
     * to the entity's actual FK column, when the entity has a relationship
     * named <relationshipField> and the FK resolver finds a different name.
     *
     * This is a deterministic last-resort correction for a well-known LLM
     * failure mode: the model appends "_id" to a relationship attribute
     * even after being told the correct column name.
     */
    private String substituteKnownFkMistakes(
            String body,
            Map<String, EntitySpec> entityMap) {

        if (body == null || body.isBlank()) {
            return body;
        }

        String result = body;

        for (EntitySpec entity : entityMap.values()) {

            if (entity.getRelationships() == null) continue;

            for (RelationshipSpec r : entity.getRelationships()) {

                if (!"many_to_one".equals(r.getType())) continue;

                String naive = r.getField() + "_id";
                String correct = NamingUtils.resolveForeignKeyField(entity, r);

                if (correct == null || correct.equals(naive)) continue;

                String wrongRef = "\\b"
                        + Pattern.quote(entity.getName())
                        + "\\."
                        + Pattern.quote(naive)
                        + "\\b";

                result = result.replaceAll(wrongRef,
                        entity.getName() + "." + correct);
            }
        }

        return result;
    }
}