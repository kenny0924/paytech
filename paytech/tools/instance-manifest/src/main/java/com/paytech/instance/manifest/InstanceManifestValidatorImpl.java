package com.paytech.instance.manifest;

import com.paytech.instance.manifest.catalog.ServiceCatalog;
import com.paytech.instance.manifest.catalog.ServiceDefinition;
import com.paytech.instance.manifest.schema.ManifestSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 先按 Schema v1 校验结构，再校验跨字段规则：端口与管理端口唯一、worker id 唯一、启用模块必须带版本、数据库名与服务清单一致、
 * versions.schema 只登记已部署服务、品牌 code 唯一。
 *
 * @author Claude
 */
public class InstanceManifestValidatorImpl implements InstanceManifestValidator {

    private static final int MANAGEMENT_PORT_OFFSET = 1000;

    private final ServiceCatalog serviceCatalog;

    private final ManifestSchema schema;

    private final YAMLMapper yamlMapper;

    /**
     * 使用打包的服务清单与 Schema。
     */
    public InstanceManifestValidatorImpl() {
        this(ServiceCatalog.loadDefault());
    }

    /**
     * 使用指定服务清单与打包的 Schema。
     *
     * @param serviceCatalog 服务清单
     */
    public InstanceManifestValidatorImpl(ServiceCatalog serviceCatalog) {
        this.serviceCatalog = serviceCatalog;
        this.schema = ManifestSchema.loadDefault(Map.of("services", serviceCatalog.names()));
        this.yamlMapper = YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    @Override
    public ManifestValidationResult validate(Path manifestFile) {
        String content;
        try {
            content = Files.readString(manifestFile);
        } catch (IOException exception) {
            return invalid(new ManifestViolation("", ManifestViolationCode.UNREADABLE,
                "清单文件无法读取: " + manifestFile + " (" + exception.getMessage() + ")"));
        }
        return validateContent(manifestFile.toString(), content);
    }

    @Override
    public ManifestValidationResult validateContent(String sourceName, String content) {
        JsonNode manifest;
        try {
            manifest = yamlMapper.readTree(content);
        } catch (JacksonException exception) {
            return invalid(new ManifestViolation("", ManifestViolationCode.UNREADABLE,
                "清单不是合法 YAML: " + sourceName + " (" + exception.getOriginalMessage() + ")"));
        }
        if (manifest == null || manifest.isMissingNode() || manifest.isNull()) {
            return invalid(new ManifestViolation("", ManifestViolationCode.UNREADABLE, "清单为空: " + sourceName));
        }
        List<ManifestViolation> violations = new ArrayList<>();
        schema.check(manifest, violations);
        if (manifest.isObject()) {
            checkServices(manifest.path("services"), violations);
            checkModules(manifest.path("modules"), violations);
            checkSchemaVersions(manifest.path("versions").path("schema"), manifest.path("services"), violations);
            checkBrands(manifest.path("brands"), violations);
        }
        if (!violations.isEmpty()) {
            return new ManifestValidationResult(List.copyOf(violations), null);
        }
        return new ManifestValidationResult(List.of(), toModel(manifest));
    }

    private void checkServices(JsonNode services, List<ManifestViolation> violations) {
        if (!services.isObject()) {
            return;
        }
        Map<Integer, String> occupiedPorts = new HashMap<>();
        Map<Integer, String> workerIds = new HashMap<>();
        for (Map.Entry<String, JsonNode> service : services.properties()) {
            String name = service.getKey();
            JsonNode runtime = service.getValue();
            if (serviceCatalog.find(name).isEmpty() || !runtime.isObject()) {
                continue;
            }
            JsonNode port = runtime.path("port");
            if (port.isIntegralNumber()) {
                reservePort(name, port.asInt(), "业务端口", occupiedPorts, violations);
                reservePort(name, port.asInt() + MANAGEMENT_PORT_OFFSET, "管理端口", occupiedPorts, violations);
            }
            JsonNode workerId = runtime.path("snowflakeWorkerId");
            if (workerId.isIntegralNumber()) {
                String previous = workerIds.putIfAbsent(workerId.asInt(), name);
                if (previous != null) {
                    violations.add(new ManifestViolation("services." + name + ".snowflakeWorkerId",
                        ManifestViolationCode.DUPLICATE_WORKER_ID,
                        "worker id " + workerId.asInt() + " 已分配给 " + previous));
                }
            }
            checkDatabase(name, runtime.path("db"), violations);
        }
    }

    private static void reservePort(String service, int port, String kind, Map<Integer, String> occupiedPorts,
        List<ManifestViolation> violations) {
        String previous = occupiedPorts.putIfAbsent(port, service);
        if (previous != null) {
            violations.add(new ManifestViolation("services." + service + ".port", ManifestViolationCode.DUPLICATE_PORT,
                kind + " " + port + " 与 " + previous + " 的端口冲突（管理端口 = 业务端口 + 1000）"));
        }
    }

    private void checkDatabase(String service, JsonNode database, List<ManifestViolation> violations) {
        String expected = serviceCatalog.find(service).map(ServiceDefinition::database).orElse(null);
        boolean declared = !database.isMissingNode() && !database.isNull();
        if (declared && !database.isString()) {
            return;
        }
        String actual = declared ? database.asString() : null;
        if (Objects.equals(expected, actual)) {
            return;
        }
        String message = expected == null ? service + " 没有数据库，不应声明 db"
            : service + " 的数据库应为 " + expected + "，实际 " + (actual == null ? "未声明" : actual);
        violations.add(new ManifestViolation("services." + service + ".db", ManifestViolationCode.DATABASE_MISMATCH,
            message));
    }

    private static void checkModules(JsonNode modules, List<ManifestViolation> violations) {
        if (!modules.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> module : modules.properties()) {
            JsonNode settings = module.getValue();
            boolean enabled = settings.path("enabled").isBoolean() && settings.path("enabled").asBoolean();
            JsonNode version = settings.path("version");
            if (enabled && (version.isMissingNode() || version.isNull())) {
                violations.add(new ManifestViolation("modules." + module.getKey() + ".version",
                    ManifestViolationCode.MODULE_VERSION_REQUIRED, "启用的模块 " + module.getKey() + " 必须声明版本"));
            }
        }
    }

    private static void checkSchemaVersions(JsonNode schemaVersions, JsonNode services,
        List<ManifestViolation> violations) {
        if (!schemaVersions.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : schemaVersions.properties()) {
            if (!services.has(entry.getKey())) {
                violations.add(new ManifestViolation("versions.schema." + entry.getKey(),
                    ManifestViolationCode.SCHEMA_VERSION_SERVICE_NOT_DEPLOYED,
                    entry.getKey() + " 未在 services 中部署，不能登记 Schema 版本"));
            }
        }
    }

    private static void checkBrands(JsonNode brands, List<ManifestViolation> violations) {
        if (!brands.isArray()) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < brands.size(); index++) {
            JsonNode code = brands.get(index).path("code");
            if (code.isString() && !codes.add(code.asString())) {
                violations.add(new ManifestViolation("brands[" + index + "].code", ManifestViolationCode.DUPLICATE_BRAND,
                    "品牌 code " + code.asString() + " 重复"));
            }
        }
    }

    private static InstanceManifest toModel(JsonNode manifest) {
        List<ServiceRuntime> services = new ArrayList<>();
        for (Map.Entry<String, JsonNode> service : manifest.path("services").properties()) {
            JsonNode runtime = service.getValue();
            JsonNode database = runtime.path("db");
            services.add(new ServiceRuntime(service.getKey(), runtime.path("port").asInt(),
                runtime.path("snowflakeWorkerId").asInt(), database.isString() ? database.asString() : null));
        }
        Map<String, Integer> schemaVersions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : manifest.path("versions").path("schema").properties()) {
            schemaVersions.put(entry.getKey(), entry.getValue().asInt());
        }
        return new InstanceManifest(manifest.path("instance").path("id").asString(),
            manifest.path("versions").path("platform").asString(), List.copyOf(services),
            Collections.unmodifiableMap(schemaVersions));
    }

    private static ManifestValidationResult invalid(ManifestViolation violation) {
        return new ManifestValidationResult(List.of(violation), null);
    }
}
