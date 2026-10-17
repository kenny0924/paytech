package com.paytech.instance.manifest.schema;

import com.paytech.instance.manifest.ManifestViolation;
import com.paytech.instance.manifest.ManifestViolationCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 按 {@value #RESOURCE} 中的节点定义校验清单结构；关键字说明见该文件头部注释。
 *
 * @author Claude
 */
public final class ManifestSchema {

    /**
     * Schema 资源。
     */
    public static final String RESOURCE = "/schema/instance-manifest-v1.yaml";

    private final JsonNode definitions;

    private final JsonNode root;

    private final Map<String, Set<String>> keyCatalogs;

    private ManifestSchema(JsonNode schema, Map<String, Set<String>> keyCatalogs) {
        this.definitions = schema.path("definitions");
        this.root = schema.path("root");
        this.keyCatalogs = keyCatalogs;
    }

    /**
     * 读取默认 Schema。
     *
     * @param keyCatalogs map 节点 {@code keys} 引用的键集合，如 {@code services -> 服务名}
     * @return Schema
     */
    public static ManifestSchema loadDefault(Map<String, Set<String>> keyCatalogs) {
        try (InputStream input = ManifestSchema.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("类路径缺少实例清单 Schema: " + RESOURCE);
            }
            JsonNode schema = YAMLMapper.builder().build()
                .readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return new ManifestSchema(schema, keyCatalogs);
        } catch (IOException exception) {
            throw new UncheckedIOException("实例清单 Schema 读取失败: " + RESOURCE, exception);
        }
    }

    /**
     * Schema 版本。
     *
     * @return 版本号
     */
    public int version() {
        return 1;
    }

    /**
     * 校验清单根节点。
     *
     * @param manifest 清单根节点
     * @param violations 失败收集
     */
    public void check(JsonNode manifest, List<ManifestViolation> violations) {
        check(manifest, root, "", violations);
    }

    private void check(JsonNode value, JsonNode node, String path, List<ManifestViolation> violations) {
        JsonNode schema = resolve(node);
        String type = schema.path("type").asString();
        switch (type) {
            case "object" -> checkObject(value, schema, path, violations);
            case "map" -> checkMap(value, schema, path, violations);
            case "array" -> checkArray(value, schema, path, violations);
            case "string" -> checkString(value, schema, path, violations);
            case "integer" -> checkInteger(value, schema, path, violations);
            case "boolean" -> {
                if (!value.isBoolean()) {
                    violations.add(typeMismatch(path, "boolean", value));
                }
            }
            default -> throw new IllegalStateException("Schema 节点类型不受支持: " + type + " @ " + path);
        }
    }

    private JsonNode resolve(JsonNode node) {
        JsonNode reference = node.path("$ref");
        if (reference.isMissingNode()) {
            return node;
        }
        JsonNode target = definitions.path(reference.asString());
        if (target.isMissingNode()) {
            throw new IllegalStateException("Schema 引用不存在: " + reference.asString());
        }
        return target;
    }

    private void checkObject(JsonNode value, JsonNode schema, String path, List<ManifestViolation> violations) {
        if (!value.isObject()) {
            violations.add(typeMismatch(path, "object", value));
            return;
        }
        JsonNode properties = schema.path("properties");
        for (JsonNode required : schema.path("required").values()) {
            JsonNode field = value.path(required.asString());
            if (field.isMissingNode() || field.isNull()) {
                violations.add(new ManifestViolation(child(path, required.asString()), ManifestViolationCode.REQUIRED,
                    "缺少必填字段 " + required.asString()));
            }
        }
        ManifestViolationCode unknownCode = unknownCode(schema);
        for (Map.Entry<String, JsonNode> field : value.properties()) {
            String fieldPath = child(path, field.getKey());
            JsonNode fieldSchema = properties.path(field.getKey());
            if (fieldSchema.isMissingNode()) {
                violations.add(new ManifestViolation(fieldPath, unknownCode, "Schema 未声明字段 " + field.getKey()
                    + "，允许: " + names(properties)));
            } else if (!field.getValue().isNull()) {
                check(field.getValue(), fieldSchema, fieldPath, violations);
            }
        }
    }

    private void checkMap(JsonNode value, JsonNode schema, String path, List<ManifestViolation> violations) {
        if (!value.isObject()) {
            violations.add(typeMismatch(path, "map", value));
            return;
        }
        int minItems = schema.path("minItems").asInt(0);
        if (value.size() < minItems) {
            violations.add(new ManifestViolation(path, ManifestViolationCode.TOO_FEW_ITEMS,
                "至少需要 " + minItems + " 项，实际 " + value.size()));
        }
        Set<String> allowedKeys = keyCatalogs.get(schema.path("keys").asString());
        if (allowedKeys == null) {
            throw new IllegalStateException("Schema map 节点引用了未知键集合: " + schema.path("keys").asString());
        }
        for (Map.Entry<String, JsonNode> entry : value.properties()) {
            String entryPath = child(path, entry.getKey());
            if (!allowedKeys.contains(entry.getKey())) {
                violations.add(new ManifestViolation(entryPath, unknownCode(schema),
                    entry.getKey() + " 不在服务清单 design/03 §1.2 中"));
                continue;
            }
            check(entry.getValue(), schema.path("values"), entryPath, violations);
        }
    }

    private void checkArray(JsonNode value, JsonNode schema, String path, List<ManifestViolation> violations) {
        if (!value.isArray()) {
            violations.add(typeMismatch(path, "array", value));
            return;
        }
        int minItems = schema.path("minItems").asInt(0);
        if (value.size() < minItems) {
            violations.add(new ManifestViolation(path, ManifestViolationCode.TOO_FEW_ITEMS,
                "至少需要 " + minItems + " 项，实际 " + value.size()));
        }
        Set<JsonNode> seen = new HashSet<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode item = value.get(index);
            String itemPath = path + "[" + index + "]";
            if (schema.path("uniqueItems").asBoolean(false) && !seen.add(item)) {
                violations.add(new ManifestViolation(itemPath, ManifestViolationCode.DUPLICATE_ITEM,
                    "重复元素 " + item));
            }
            check(item, schema.path("items"), itemPath, violations);
        }
    }

    private void checkString(JsonNode value, JsonNode schema, String path, List<ManifestViolation> violations) {
        if (!value.isString()) {
            violations.add(typeMismatch(path, "string", value));
            return;
        }
        String text = value.asString();
        if (schema.path("notBlank").asBoolean(false) && text.isBlank()) {
            violations.add(new ManifestViolation(path, ManifestViolationCode.BLANK_VALUE, "不能为空"));
            return;
        }
        JsonNode allowed = schema.path("enum");
        if (!allowed.isMissingNode() && allowed.values().stream().noneMatch(option -> option.asString().equals(text))) {
            violations.add(new ManifestViolation(path, ManifestViolationCode.ENUM_MISMATCH,
                text + " 不在允许值 " + allowed.values().stream().map(JsonNode::asString).toList() + " 中"));
        }
        JsonNode pattern = schema.path("pattern");
        if (!pattern.isMissingNode() && !Pattern.compile(pattern.asString()).matcher(text).matches()) {
            violations.add(new ManifestViolation(path, ManifestViolationCode.PATTERN_MISMATCH,
                text + " 不符合格式 " + pattern.asString()));
        }
    }

    private void checkInteger(JsonNode value, JsonNode schema, String path, List<ManifestViolation> violations) {
        if (!value.isIntegralNumber()) {
            violations.add(typeMismatch(path, "integer", value));
            return;
        }
        long number = value.asLong();
        JsonNode minimum = schema.path("minimum");
        JsonNode maximum = schema.path("maximum");
        boolean belowMinimum = !minimum.isMissingNode() && number < minimum.asLong();
        boolean aboveMaximum = !maximum.isMissingNode() && number > maximum.asLong();
        if (belowMinimum || aboveMaximum) {
            violations.add(new ManifestViolation(path, ManifestViolationCode.OUT_OF_RANGE, number + " 超出范围 ["
                + (minimum.isMissingNode() ? "" : minimum.asLong()) + ", "
                + (maximum.isMissingNode() ? "" : maximum.asLong()) + "]"));
        }
    }

    private static ManifestViolationCode unknownCode(JsonNode schema) {
        JsonNode code = schema.path("unknownCode");
        return code.isMissingNode() ? ManifestViolationCode.UNKNOWN_PROPERTY : ManifestViolationCode.valueOf(code.asString());
    }

    private static ManifestViolation typeMismatch(String path, String expected, JsonNode actual) {
        return new ManifestViolation(path, ManifestViolationCode.TYPE_MISMATCH,
            "期望 " + expected + "，实际 " + actual.getNodeType().name().toLowerCase(java.util.Locale.ROOT) + " " + actual);
    }

    private static List<String> names(JsonNode properties) {
        return properties.properties().stream().map(Map.Entry::getKey).toList();
    }

    private static String child(String path, String name) {
        return path.isEmpty() ? name : path + "." + name;
    }
}
