package com.prashant.codegen.llm;

import com.prashant.codegen.generator.ServiceLogicGenerator;
import com.prashant.codegen.model.EntitySpec;
import com.prashant.codegen.model.FieldSpec;
import com.prashant.codegen.model.MethodSpec;
import com.prashant.codegen.model.ParamSpec;
import com.prashant.codegen.model.RelationshipSpec;
import com.prashant.codegen.util.NamingUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
@Component
public class PromptBuilder {

    /**
     * Builds a prompt asking the model to fill in ONE method body.
     *
     * @param method          the method to implement (name, params, returns, intent)
     * @param entityMap       map of entity name -> EntitySpec (for describing referenced entities)
     * @param ownerEntity     the entity this service method operates on, if known
     * @param serviceName     the class this method lives on (context only)
     * @param previousFailure reason why the previous generation attempt was rejected
     */
    public String buildPrompt(MethodSpec method, Map<String, EntitySpec> entityMap,
                              EntitySpec ownerEntity, String serviceName,
                              String previousFailure) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are completing ONE Python method inside a FastAPI service class.\n");
        sb.append("Return ONLY the method body — the lines that go INSIDE the function, ")
                .append("indented with exactly 8 spaces per line, nothing else.\n");
        sb.append("Do NOT repeat the 'def' line. Do NOT include the docstring. ")
                .append("Do NOT use markdown code fences (no ``` anywhere). ")
                .append("Do NOT add any explanation before or after the code.\n\n");

        sb.append("Example of correctly indented output (for a different method, for format reference only):\n");
        sb.append("        item = Widget(name=data.name)\n");
        sb.append("        db.add(item)\n");
        sb.append("        db.commit()\n");
        sb.append("        db.refresh(item)\n");
        sb.append("        return item\n\n");

        sb.append("Class: ").append(serviceName).append("\n");
        sb.append("Method signature:\n");
        sb.append("    def ").append(method.getName()).append("(");

        List<ParamSpec> params = method.getParams();

        if (params != null) {
            for (ParamSpec p : params) {
                /*
                 * current_user and db are supplied by the service template itself.
                 * Do not duplicate them in the prompt signature.
                 */
                if ("current_user".equals(p.getName()) || "db".equals(p.getName())) {
                    continue;
                }

                sb.append(p.getName())
                        .append(": ")
                        .append(p.getType())
                        .append(", ");
            }
        }

        sb.append("current_user: User, db: Session) -> ")
                .append(method.getReturns())
                .append(":\n\n");

        sb.append("What this method should do:\n");
        sb.append(method.getIntent()).append("\n\n");
        sb.append("Implement EVERY clause of the intent above.\n");

        sb.append("- If the intent describes multiple conditions ('if X and Y', 'when X', 'only if X'), ");
        sb.append("check each of them separately and raise HTTPException(400) when a condition fails. ");
        sb.append("Do not skip a condition because it seems optional.\n");

        sb.append("- If the intent describes a side effect ('reduce the count by one', 'increment the counter'), ");
        sb.append("perform it in the same transaction as the main operation.\n");

        sb.append("- If the intent describes ownership ('only the owner can', 'owned by the current user', ");
        sb.append("'borrowed by the current user'), add the ownership condition to the WHERE clause ");
        sb.append("of the primary query. Return 404 when no row matches, so ownership violations ");
        sb.append("are indistinguishable from not-found.\n");

        sb.append("- If the intent says to reject something that is 'already' in some state ");
        sb.append("(already returned, already published, already paid, already active), do NOT put ");
        sb.append("that state check inside the primary lookup filter. Fetch the row by id and ownership ");
        sb.append("only. Then, in a separate step, check the state and raise HTTPException(400) if the ");
        sb.append("state is already set. Use 404 only when the row does not exist or does not belong to ");
        sb.append("the current user.\n");

        sb.append("- If the intent says something must be available, active, or have enough ");
        sb.append("remaining (copies available, stock, balance, seats left), fetch the row ");
        sb.append("first, then check that condition separately and raise HTTPException(400) ");
        sb.append("when it fails. Do not merge this check into the primary lookup's 404 filter.\n");

        sb.append("- For any boolean field that represents the state this action sets, use the ");
        sb.append("value the intent implies, not the value from the request. Examples: borrowing ");
        sb.append("sets returned=False, returning sets returned=True, publishing sets ");
        sb.append("is_published=True. The client does not choose the starting state of a state machine.\n");

        sb.append("- When a method modifies more than one row, use ONE db.commit() at the very end. ");
        sb.append("Do not commit between sub-steps; if an error occurs partway, the database must be unchanged.\n\n");
        sb.append("- Raise HTTPException only for conditions the intent states explicitly. ")
                .append("The allowed rejections are: (1) the row does not exist or does not belong to the ")
                .append("current user, status 404; (2) a state the intent describes as already set, status 400; ")
                .append("(3) a check the intent explicitly lists (e.g. 'reject if quantity exceeds stock', ")
                .append("'reject if user is inactive'), status 400 or 403. ")
                .append("Do NOT invent additional validation. In particular, a method that increases a count ")
                .append("(return, restore, cancel) must not check whether the count is already above some limit.\n");
        sb.append("- Never use .one(), .scalar_one(), or any method that raises NoResultFound when ")
                .append("nothing matches. For a lookup that may return zero rows, use .first() and then ")
                .append("'if not row: raise HTTPException(status_code=404, detail=\"... not found\")'.\n");
        /*
         * Describe all entities directly involved in the method and their
         * related entities. This gives the LLM enough information to follow
         * relationship chains such as Task -> Project -> User.
         */
        appendEntityContext(sb, method, entityMap);

        /*
         * Dynamic, data-driven requirements checklist.
         *
         * Important:
         * Do NOT tell the LLM to use every field of every parameter.
         * It should only use fields required by the method's actual intent.
         */
        List<String> requirements = buildRequirements(
                method,
                params,
                entityMap,
                ownerEntity
        );

        if (!requirements.isEmpty()) {
            sb.append("Requirements (do ALL of these):\n");

            for (String req : requirements) {
                sb.append("- ").append(req).append("\n");
            }

            sb.append("\n");
        }

        sb.append("General conventions:\n");
        sb.append("- 'current_user' is a SQLAlchemy User object already fetched from the DB by the caller.\n");
        sb.append("- Use 'current_user.id' whenever you need the user's ID (e.g. for foreign keys or filters). Never assign current_user itself into an integer column.\n");
        sb.append("- 'current_user' is already loaded. Read its fields directly ")
                .append("(current_user.is_active, current_user.max_loans). ")
                .append("Never re-query the User table when you already have current_user.\n");
        sb.append("- Never call .filter, .filter_by, .count, .all, .first, .one, .scalar, ")
                .append(".order_by, .limit, or .offset on a relationship attribute. ")
                .append("Attributes like User.loans, Post.comments, Category.expenses are not ")
                .append("queries. To count related rows, use ")
                .append("db.query(Model).filter(...).count(). ")
                .append("Example: to count active loans for the current user, write ")
                .append("db.query(Loan).filter(Loan.borrower_id == current_user.id, ")
                .append("Loan.returned == False).count(). ")
                .append("Do NOT write User.loans.filter(...) — it will raise AttributeError at runtime.\n");
        sb.append("- Filtering by a related entity's attribute: NEVER write Model.relationship.column. ")
                .append("A relationship attribute on a class is not the related table's column. ")
                .append("Use one of these two forms instead:\n");
        sb.append("    * Model.relationship.has(Related.column == value)\n");
        sb.append("    * db.query(Model).join(Related).filter(Related.column == value)\n");
        sb.append("- Ownership through a parent relationship (the common case in this project) must use one of those two forms. ");
        sb.append("Example: to filter Tasks belonging to a Project owned by the current user, write either\n");
        sb.append("      Task.project.has(Project.owner_id == current_user.id)\n");
        sb.append("  or\n");
        sb.append("      db.query(Task).join(Project).filter(Project.owner_id == current_user.id)\n");
        sb.append("- Never write 'Model.relationship.column' inside a filter clause, ever. It will raise AttributeError at runtime.\n");
        sb.append("- A SQLAlchemy session is available via a variable named 'db'.\n");
        sb.append("- Raise HTTPException(status_code=..., detail=\"...\") for not-found/invalid cases.\n");
        sb.append("- Return exactly the type declared in the signature.\n");
        sb.append("- Only use field names that were explicitly listed for an entity in this prompt. ")
                .append("Never invent or guess a field/column name (e.g. do not assume Django/Flask-style ")
                .append("conventions like 'user_id').\n");
        sb.append("- When filtering by ownership or a foreign key, use the exact FK column ")
                .append("name shown in the 'relationship-to-FK mapping' section. ")
                .append("For example, if the mapping says relationship 'owner' → FK column 'user_id', ")
                .append("write 'Category.user_id', never 'Category.owner_id'.\n");
        sb.append("- Follow the method intent exactly. Do not add unrelated validation, mutation, or fields that the method does not require.\n\n");

        sb.append(buildPreviousFailureFeedback(previousFailure));

        sb.append("Method body:\n");

        return sb.toString();
    }

    /**
     * Builds the retry feedback section for a previous failed generation attempt.
     *
     * Returns an empty string when there is no previous failure.
     */
    private String buildPreviousFailureFeedback(String previousFailure) {

        if (previousFailure == null || previousFailure.isBlank()) {
            return "";
        }

        return """
                Previous attempt was rejected.

                Reason:
                %s

                Generate the method body again and fix this issue.

                """.formatted(previousFailure);
    }

    /**
     * Builds requirements from the actual MethodSpec/ParamSpec/EntitySpec data.
     *
     * No requirement tells the LLM to use every field from current_user.
     */
    private List<String> buildRequirements(
            MethodSpec method,
            List<ParamSpec> params,
            Map<String, EntitySpec> entityMap,
            EntitySpec ownerEntity) {

        List<String> reqs = new ArrayList<>();

        String name = method.getName() == null
                ? ""
                : method.getName().toLowerCase();

        /*
         * Rule 1:
         * For input schemas/entities, tell the LLM which fields exist and
         * which fields are actually required for creation.
         *
         * We intentionally DO NOT say "use every field".
         */
        if (params != null) {
            for (ParamSpec p : params) {

                if ("current_user".equals(p.getName())
                        || "db".equals(p.getName())) {
                    continue;
                }

                String bareType = stripWrapper(p.getType());

                EntitySpec matched = matchEntityForSchema(
                        bareType,
                        entityMap
                );

                if (matched == null) {
                    continue;
                }

                List<String> requiredFields = new ArrayList<>();
                List<String> defaultedFields = new ArrayList<>();
                String authEntityName = findAuthEntityName(entityMap);
                String ownershipFk    = findOwnershipFk(matched, authEntityName);

                if (matched.getFields() != null) {

                    for (FieldSpec f : matched.getFields()) {

                        if (f.isPrimaryKey()) {
                            continue;
                        }
                        // Ownership FK is set from current_user.id, never from the request.
                        if (ownershipFk != null && ownershipFk.equals(f.getName())) continue;

                        if (isTrulyRequired(f)) {
                            requiredFields.add(f.getName());
                        } else {
                            defaultedFields.add(f.getName());
                        }
                    }
                }

                if (name.startsWith("create_")
                        && !requiredFields.isEmpty()) {

                    reqs.add(
                            "For fields supplied by '" + p.getName()
                                    + "', validate only these fields as required: "
                                    + String.join(", ", requiredFields)
                                    + "."
                    );
                }

                if (name.startsWith("create_")
                        && !defaultedFields.isEmpty()) {

                    reqs.add(
                            "Do not reject fields merely because they are falsy when they have "
                                    + "a default or are nullable: "
                                    + String.join(", ", defaultedFields)
                                    + "."
                    );
                }
                if (name.startsWith("create_")) {

                    StringBuilder fieldList = new StringBuilder();

                    for (FieldSpec f : matched.getFields()) {

                        if (f.isPrimaryKey()) {
                            continue;
                        }

                        String source;

                        if (f.isReadOnly()) {
                            source = "ALWAYS the default value " + pythonLiteralFor(f)
                                    + " (never from the request)";
                        } else if (ownershipFk != null && ownershipFk.equals(f.getName())) {
                            source = "ALWAYS current_user.id (never from the request)";
                        } else {
                            source = "from " + p.getName() + "." + f.getName();
                        }

                        fieldList.append("  - ")
                                .append(f.getName())
                                .append(": ")
                                .append(source)
                                .append("\n");
                    }

                    if (fieldList.length() > 0) {
                        reqs.add(
                                "When constructing the model for '" + p.getName()
                                        + "', use these keyword arguments (one per line):\n"
                                        + fieldList
                                        + "Do NOT omit a field from this list. "
                                        + "Do NOT copy ownership FKs or read-only state fields from the request; "
                                        + "the source line says what to use instead."
                        );
                    }
                }
            }
        }

        /*
         * Rule 2:
         *
         * Do NOT automatically invent an ownership rule from dependsOn.
         *
         * Ownership is already specified by MethodSpec.intent and the
         * relationship/entity context supplied above.
         *
         * This is important for methods such as:
         * Task -> Project -> User
         * where ownership cannot be inferred from one direct relationship.
         */

        /*
         * Rule 3: CRUD-shape instructions from method name prefix.
         */
        if (name.startsWith("update_")) {

            reqs.add(
                    "Fetch the existing record first; raise HTTPException(status_code=404) "
                            + "if it does not exist."
            );

            reqs.add(
                    "Apply every field the client actually sent, and only those fields. "
                            + "Use the Pydantic Update schema's 'model_dump(exclude_unset=True)' "
                            + "to get that dict, then assign each key with setattr on the ORM object."
            );

            reqs.add(
                    "Do NOT hardcode a single field to update (for example, do not only set "
                            + "'is_published' or only set 'title'). Loop over every key from "
                            + "the dumped dict."
            );

            reqs.add(
                    "After applying the changes: db.commit(); db.refresh(existing); return existing."
            );

        } else if (name.startsWith("delete_")) {

            reqs.add(
                    "Fetch the existing record first; raise HTTPException(status_code=404) "
                            + "if it does not exist, then delete it."
            );

        } else if (name.startsWith("get_")) {

            reqs.add(
                    "Raise HTTPException(status_code=404) if no matching record is found. "
                            + "Do not mutate any data."
            );

        } else if (name.startsWith("list_")) {

            reqs.add(
                    "Return a list. Do not mutate any data. "
                            + "An empty list is a valid result, not an error."
            );
        }

        return reqs;
    }

    /**
     * Adds entity context for the method parameters and return type.
     *
     * Related entities are also described so that relationship chains are
     * available to the LLM.
     */
    private void appendEntityContext(
            StringBuilder sb,
            MethodSpec method,
            Map<String, EntitySpec> entityMap) {

        Set<String> entitiesSeen = new HashSet<>();
        Set<String> primaryEntities = computePrimaryEntities(method, entityMap);

        if (method.getParams() != null) {
            for (ParamSpec p : method.getParams()) {

                if ("current_user".equals(p.getName())
                        || "db".equals(p.getName())) {
                    continue;
                }

                describeEntityTree(
                        sb,
                        stripWrapper(p.getType()),
                        entityMap,
                        entitiesSeen,
                        primaryEntities
                );
            }
        }

        describeEntityTree(
                sb,
                stripWrapper(method.getReturns()),
                entityMap,
                entitiesSeen,
                primaryEntities
        );

        describeEntityTree(
                sb,
                "User",
                entityMap,
                entitiesSeen,
                primaryEntities
        );
    }
    /**
     * The entities a method is "about". For create_loan that is {Loan}.
     * For update_book that is {Book}. For list_tasks that is {Task}.
     *
     * Used to decide which entities get an ownership scope line.
     * An entity that is only looked up (Book inside create_loan) does
     * NOT get a scope line, because the method is not scoping to the user
     * through it.
     */
    private Set<String> computePrimaryEntities(
            MethodSpec method,
            Map<String, EntitySpec> entityMap) {

        Set<String> primary = new HashSet<>();

        // 1. The return type, if it resolves to an entity.
        String returnBare = stripWrapper(method.getReturns());
        EntitySpec returnEntity = matchEntityForSchema(returnBare, entityMap);
        if (returnEntity != null) {
            primary.add(returnEntity.getName());
        }

        // 2. The entity whose snake_case name appears in the method name.
        //    create_loan        -> Loan
        //    update_book        -> Book
        //    list_tasks         -> Task
        //    return_loan        -> Loan
        //    delete_category    -> Category
        String methodName = method.getName() == null
                ? ""
                : method.getName().toLowerCase();

        if (!methodName.isEmpty()) {
            for (EntitySpec e : entityMap.values()) {
                String entitySnake = NamingUtils.toSnakeCase(e.getName());
                if (entitySnake.isEmpty()) continue;

                if (methodName.contains(entitySnake)) {
                    primary.add(e.getName());
                }
            }
        }

        return primary;
    }

    /**
     * Recursively describes an entity and the entities referenced by its
     * relationships.
     *
     * The visited set prevents circular relationships from producing
     * an infinite prompt.
     */
    private void describeEntityTree(
            StringBuilder sb,
            String bareType,
            Map<String, EntitySpec> entityMap,
            Set<String> entitiesSeen,
            Set<String> primaryEntities) {

        if (bareType == null || bareType.isEmpty()) {
            return;
        }

        EntitySpec entity = matchEntityForSchema(
                bareType,
                entityMap
        );

        if (entity == null) {
            return;
        }

        if (entitiesSeen.contains(entity.getName())) {
            return;
        }

        entitiesSeen.add(entity.getName());

        sb.append("Entity '")
                .append(entity.getName())
                .append("' has fields:\n");

        List<FieldSpec> fields = entity.getFields();

        if (fields != null) {
            for (FieldSpec f : fields) {

                sb.append("  - ")
                        .append(f.getName())
                        .append(": ")
                        .append(f.getType());

                if (f.isPrimaryKey()) {
                    sb.append(" (primary key)");
                }

                if (f.getDefaultValue() != null) {
                    sb.append(" (default: ")
                            .append(f.getDefaultValue())
                            .append(")");
                }

                if (f.isNullable()) {
                    sb.append(" (nullable)");
                }

                sb.append("\n");
            }
        }

        /*
         * Relationships are described using their actual RelationshipSpec
         * field name. No artificial field name is created here.
         */
        if (entity.getRelationships() != null
                && !entity.getRelationships().isEmpty()) {

            sb.append("Entity '")
                    .append(entity.getName())
                    .append("' has relationships:\n");

            for (RelationshipSpec r : entity.getRelationships()) {

                sb.append("  - ")
                        .append(r.getType())
                        .append(": ")
                        .append(r.getTarget());

                if (r.getField() != null
                        && !r.getField().isEmpty()) {

                    sb.append(" using field '")
                            .append(r.getField())
                            .append("'");
                }

                sb.append("\n");
            }

            /*
             * Also expose the exact FK field associated with many_to_one
             * relationships.
             */
            boolean hasFkRelationship = false;

            for (RelationshipSpec r : entity.getRelationships()) {
                if ("many_to_one".equals(r.getType())) {
                    hasFkRelationship = true;
                    break;
                }
            }

            if (hasFkRelationship) {

                sb.append("Entity '")
                        .append(entity.getName())
                        .append("' has these foreign key columns ")
                        .append("(use these EXACT names):\n");

                for (RelationshipSpec r : entity.getRelationships()) {
                    if ("many_to_one".equals(r.getType())) {
                        String fkField = NamingUtils.resolveForeignKeyField(entity, r);
                        if (fkField != null) {
                            sb.append("  - ")
                                    .append(fkField)
                                    .append(" (references ")
                                    .append(r.getTarget())
                                    .append(")\n");
                        }
                    }
                }

            }
            appendRelationshipToFkMapping(sb, entity);
            if (primaryEntities.contains(entity.getName())) {
                appendOwnershipScopeLine(sb, entity, entityMap);
            }
        }

        sb.append("\n");

        /*
         * Follow relationships so the LLM can understand chains such as:
         *
         * Task -> Project -> User
         */
        if (entity.getRelationships() != null) {

            for (RelationshipSpec r : entity.getRelationships()) {

                if (r.getTarget() == null
                        || r.getTarget().isEmpty()) {
                    continue;
                }

                describeEntityTree(
                        sb,
                        r.getTarget(),
                        entityMap,
                        entitiesSeen,
                        primaryEntities
                );
            }
        }
    }


    private boolean isTrulyRequired(FieldSpec f) {
        return !f.isNullable()
                && f.getDefaultValue() == null;
    }

    /**
     * Emit an explicit relationship -> FK column mapping for every
     * many_to_one relationship on this entity.
     *
     * The FK column name is resolved via the shared NamingUtils resolver,
     * so it is guaranteed to match what the model generator emits.
     *
     * This exists specifically to stop the LLM from inventing
     * '<relationshipField>_id' when the actual FK is named differently
     * (e.g. relationship 'owner' -> FK column 'user_id').
     */
    private void appendRelationshipToFkMapping(
            StringBuilder sb,
            EntitySpec entity) {

        if (entity.getRelationships() == null
                || entity.getRelationships().isEmpty()) {
            return;
        }

        boolean hasM2O = false;
        for (RelationshipSpec r : entity.getRelationships()) {
            if ("many_to_one".equals(r.getType())) {
                hasM2O = true;
                break;
            }
        }

        if (!hasM2O) {
            return;
        }

        sb.append("Entity '")
                .append(entity.getName())
                .append("' relationship-to-FK mapping ")
                .append("(do NOT invent '<relationship>_id' — use the FK column below):\n");

        for (RelationshipSpec r : entity.getRelationships()) {

            if (!"many_to_one".equals(r.getType())) {
                continue;
            }

            String fkField = NamingUtils.resolveForeignKeyField(entity, r);

            if (fkField == null) {
                continue;
            }

            String naiveGuess = r.getField() + "_id";

            sb.append("  - relationship '")
                    .append(r.getField())
                    .append("' → FK column '")
                    .append(fkField)
                    .append("' (references ")
                    .append(r.getTarget())
                    .append(").");

            // Only warn about the naive guess when it would actually be wrong.
            if (!naiveGuess.equals(fkField)) {
                sb.append(" Do NOT write '")
                        .append(naiveGuess)
                        .append("'.");
            }

            sb.append("\n");
        }
    }

    private EntitySpec matchEntityForSchema(
            String bareType,
            Map<String, EntitySpec> entityMap) {

        if (bareType == null) {
            return null;
        }

        if (entityMap.containsKey(bareType)) {
            return entityMap.get(bareType);
        }

        for (String suffix :
                new String[]{"Create", "Update", "Response", "Request"}) {

            if (bareType.endsWith(suffix)) {

                String base = bareType.substring(
                        0,
                        bareType.length() - suffix.length()
                );

                if (entityMap.containsKey(base)) {
                    return entityMap.get(base);
                }
            }
        }

        return null;
    }

    private String stripWrapper(String type) {

        if (type == null) {
            return null;
        }

        if (type.startsWith("List[")
                && type.endsWith("]")) {

            return type.substring(
                    5,
                    type.length() - 1
            );
        }

        if (type.startsWith("list[")
                && type.endsWith("]")) {

            return type.substring(
                    5,
                    type.length() - 1
            );
        }

        if (type.startsWith("Optional[")
                && type.endsWith("]")) {

            return type.substring(
                    9,
                    type.length() - 1
            );
        }

        return type;
    }
    /**
     * The auth entity is the entity with a `password` field.
     */
    private String findAuthEntityName(Map<String, EntitySpec> entityMap) {
        for (EntitySpec e : entityMap.values()) {
            if (e.getFields() == null) continue;
            for (FieldSpec f : e.getFields()) {
                if ("password".equals(f.getName())) {
                    return e.getName();
                }
            }
        }
        return null;
    }

    /**
     * The ownership FK on `entity` is the many_to_one FK that targets the
     * auth entity. Returns null if the entity has no such FK.
     */
    private String findOwnershipFk(EntitySpec entity, String authEntityName) {
        if (authEntityName == null
                || entity == null
                || entity.getRelationships() == null) {
            return null;
        }
        for (RelationshipSpec r : entity.getRelationships()) {
            if (!"many_to_one".equals(r.getType())) continue;
            if (!authEntityName.equals(r.getTarget())) continue;
            String fk = NamingUtils.resolveForeignKeyField(entity, r);
            if (fk != null) return fk;
        }
        return null;
    }
    /**
     * Emit a per-entity statement that names the ownership FK column (or
     * the relationship path to it) so the LLM never has to infer ownership
     * from the intent's phrasing.
     *
     * Direct case:
     *   Entity 'Loan' is scoped to the current user by column 'borrower_id'.
     *
     * Indirect case (no FK to User on this entity, but a parent has one):
     *   Entity 'Book' is scoped to the current user via relationship 'branch'
     *   → Branch (which is scoped by 'manager_id'). Filter with
     *   Book.branch.has(Branch.manager_id == current_user.id).
     *
     * Emits nothing when the entity has no path to the auth entity at all.
     */
    private void appendOwnershipScopeLine(
            StringBuilder sb,
            EntitySpec entity,
            Map<String, EntitySpec> entityMap) {

        String authEntity = findAuthEntityName(entityMap);
        if (authEntity == null) {
            return;
        }

        // Direct FK on this entity.
        String directFk = findOwnershipFk(entity, authEntity);
        if (directFk != null) {
            sb.append("Entity '").append(entity.getName())
                    .append("' is scoped to the current user by column '")
                    .append(directFk).append("'. ")
                    .append("Any query that fetches a ")
                    .append(entity.getName())
                    .append(" by its primary key must include ")
                    .append(entity.getName()).append(".").append(directFk)
                    .append(" == current_user.id in the filter.\n");
            return;
        }

        // One-hop path through a many_to_one relationship.
        if (entity.getRelationships() != null) {
            for (RelationshipSpec r : entity.getRelationships()) {
                if (!"many_to_one".equals(r.getType())) continue;

                EntitySpec target = entityMap.get(r.getTarget());
                if (target == null) continue;

                String targetFk = findOwnershipFk(target, authEntity);
                if (targetFk == null) continue;

                sb.append("Entity '").append(entity.getName())
                        .append("' is scoped to the current user via relationship '")
                        .append(r.getField()).append("' → ")
                        .append(target.getName())
                        .append(" (which is scoped by '")
                        .append(targetFk).append("'). ")
                        .append("Filter with ")
                        .append(entity.getName()).append(".").append(r.getField())
                        .append(".has(")
                        .append(target.getName()).append(".").append(targetFk)
                        .append(" == current_user.id).\n");
                return;
            }
        }
    }
    /**
     * Python literal for a field's default value, matching what the
     * FTL template emits. Used when describing read-only fields to the LLM.
     */
    private String pythonLiteralFor(FieldSpec f) {

        String type = f.getType();
        String dv = f.getDefaultValue();

        if (type == null) {
            return "None";
        }

        if ("bool".equals(type) || "Boolean".equals(type)) {
            if ("false".equalsIgnoreCase(dv)) return "False";
            if ("true".equalsIgnoreCase(dv))  return "True";
            return "False";
        }

        if ("int".equals(type) || "Integer".equals(type)
                || "float".equals(type) || "Float".equals(type)
                || "Double".equals(type)) {
            return dv != null ? dv : "0";
        }

        if ("str".equals(type) || "String".equals(type)) {
            return dv != null ? "\"" + dv + "\"" : "None";
        }

        if ("datetime".equals(type) || "Date".equals(type)) {
            return "None";
        }

        return dv != null ? dv : "None";
    }
}
