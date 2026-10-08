package com.prashant.codegen.generator;

import com.prashant.codegen.model.EntitySpec;
import com.prashant.codegen.model.FieldSpec;
import com.prashant.codegen.model.RelationshipSpec;
import com.prashant.codegen.util.NamingUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class EntityModelGenerator {

    public String generate(
            EntitySpec entity,
            List<EntitySpec> allEntities,
            Configuration cfg)
            throws IOException, TemplateException {

        Map<String, EntitySpec> entityMap = allEntities.stream()
                .collect(Collectors.toMap(
                        EntitySpec::getName,
                        e -> e
                ));

        // ============================================================
        // 1. Enrich relationships with partner field
        // ============================================================

        List<Map<String, Object>> enrichedRelationships =
                new ArrayList<>();

        for (RelationshipSpec rel : entity.getRelationships()) {

            Map<String, Object> enrichedRel =
                    new HashMap<>();

            enrichedRel.put("type", rel.getType());
            enrichedRel.put("target", rel.getTarget());
            enrichedRel.put("field", rel.getField());

            String partnerField =
                    findPartnerField(
                            entity,
                            rel,
                            entityMap
                    );

// Leave as null when there is no inverse. The template will omit
// back_populates entirely rather than emit back_populates="unknown",
// which would raise AttributeError at configure time.
            enrichedRel.put("partnerField", partnerField);

// ------------------------------------------------------------
// Ambiguous-FK detection.
//
// When this entity has more than one many_to_one (or one_to_one)
// relationship targeting the same entity, SQLAlchemy cannot guess
// which FK column belongs to which relationship. Each such
// relationship must be given an explicit foreign_keys=[...] list.
//
// Example: Ticket has both reporter -> User and assignee -> User,
// with reporter_id and assignee_id. Both need foreign_keys.
// ------------------------------------------------------------
            List<String> foreignKeys = null;

            if ("many_to_one".equals(rel.getType())
                    || "one_to_one".equals(rel.getType())) {

                int sameTargetM2OCount = 0;

                for (RelationshipSpec other : entity.getRelationships()) {

                    if (other == rel) {
                        continue;
                    }

                    if (!"many_to_one".equals(other.getType())
                            && !"one_to_one".equals(other.getType())) {
                        continue;
                    }

                    if (rel.getTarget().equals(other.getTarget())) {
                        sameTargetM2OCount++;
                    }
                }

                if (sameTargetM2OCount > 0) {

                    String fkField =
                            NamingUtils.resolveForeignKeyField(entity, rel);

                    if (fkField != null) {
                        foreignKeys = List.of(fkField);
                    } else {
                        System.err.println(
                                "WARN: Ambiguous target '" + rel.getTarget()
                                        + "' on " + entity.getName()
                                        + "." + rel.getField()
                                        + " but no FK column could be resolved. "
                                        + "SQLAlchemy will fail at configure time."
                        );
                    }
                }
            }

            enrichedRel.put("foreignKeys", foreignKeys);

// Cascade is only correct when the child's FK back to us is NOT nullable.
// Nullable FK means "child survives parent with FK set to NULL", which is
// what shipping_address_id on Order means.
            String cascade = null;
            if ("one_to_many".equals(rel.getType())) {
                EntitySpec child = entityMap.get(rel.getTarget());
                if (child != null) {
                    String childFk = null;
                    if (child.getRelationships() != null) {
                        for (RelationshipSpec childRel : child.getRelationships()) {
                            if (!"many_to_one".equals(childRel.getType())) continue;
                            if (!entity.getName().equals(childRel.getTarget())) continue;
                            childFk = NamingUtils.resolveForeignKeyField(child, childRel);
                            if (childFk != null) break;
                        }
                    }
                    if (childFk != null && child.getFields() != null) {
                        boolean nullable = false;
                        for (FieldSpec cf : child.getFields()) {
                            if (cf.getName().equals(childFk)) {
                                nullable = cf.isNullable();
                                break;
                            }
                        }
                        if (!nullable) {
                            cascade = "all, delete-orphan";
                        }
                    }
                }
            }
            enrichedRel.put("cascade", cascade);

            enrichedRelationships.add(enrichedRel);
        }

        // ============================================================
        // 2. Build foreign key map
        //
        // field name -> ForeignKey("target_table.id")
        // ============================================================

        Map<String, String> fieldForeignKeyMap =
                new HashMap<>();

        for (RelationshipSpec rel :
                entity.getRelationships()) {

            if (!"many_to_one".equals(rel.getType())
                    && !"one_to_one".equals(rel.getType())) {
                continue;
            }

            String fkFieldName =
                    resolveForeignKeyFieldName(
                            entity,
                            rel
                    );

            if (fkFieldName == null) {
                System.err.println(
                        "WARN: Could not find FK field for relationship " +
                                entity.getName() +
                                "." +
                                rel.getField() +
                                " -> " +
                                rel.getTarget()
                );

                continue;
            }

            // Use the shared naming utility instead of target + "s".
            // User -> users, Category -> categories, Box -> boxes.
            String targetTable =
                    NamingUtils.toTableName(rel.getTarget());

            String fkString =
                    "ForeignKey(\"" +
                            targetTable +
                            ".id\")";

            fieldForeignKeyMap.put(
                    fkFieldName,
                    fkString
            );
        }

        // ============================================================
        // 3. Build data model
        // ============================================================

        Map<String, Object> dataModel =
                new HashMap<>();

        dataModel.put(
                "entity",
                entity
        );

        dataModel.put(
                "tableName",
                NamingUtils.toTableName(entity.getName())
        );

        dataModel.put(
                "enrichedRelationships",
                enrichedRelationships
        );

        dataModel.put(
                "fieldForeignKeyMap",
                fieldForeignKeyMap
        );

        // ============================================================
        // 4. Render template
        // ============================================================

        Template template =
                cfg.getTemplate(
                        "entity_model.py.ftl"
                );

        StringWriter writer =
                new StringWriter();

        template.process(
                dataModel,
                writer
        );

        return writer.toString();
    }

    private String resolveForeignKeyFieldName(EntitySpec entity, RelationshipSpec rel) {
        return NamingUtils.resolveForeignKeyField(entity, rel);
    }

    // ================================================================
    // Find inverse/partner relationship
    // ================================================================

    private String findPartnerField(
            EntitySpec currentEntity,
            RelationshipSpec currentRel,
            Map<String, EntitySpec> entityMap) {

        EntitySpec targetEntity =
                entityMap.get(
                        currentRel.getTarget()
                );

        if (targetEntity == null) {

            System.err.println(
                    "WARN: Target entity '" +
                            currentRel.getTarget() +
                            "' not found for relationship on " +
                            currentEntity.getName()
            );

            return null;
        }

        String currentType =
                currentRel.getType();

        String expectedInverseType =
                getInverseType(currentType);

        for (RelationshipSpec targetRel :
                targetEntity.getRelationships()) {

            if (!targetRel.getTarget()
                    .equals(currentEntity.getName())) {
                continue;
            }

            if (expectedInverseType
                    .equals(targetRel.getType())) {

                return targetRel.getField();
            }
        }

        System.err.println(
                "WARN: No matching inverse relationship found on " +
                        targetEntity.getName() +
                        " for " +
                        currentEntity.getName() +
                        "." +
                        currentRel.getField()
        );

        return null;
    }

    // ================================================================
    // Relationship inverse mapping
    // ================================================================

    private String getInverseType(String type) {

        switch (type) {

            case "one_to_many":
                return "many_to_one";

            case "many_to_one":
                return "one_to_many";

            case "one_to_one":
                return "one_to_one";

            case "many_to_many":
                return "many_to_many";

            default:
                throw new IllegalArgumentException(
                        "Unknown relationship type: " +
                                type
                );
        }
    }
}