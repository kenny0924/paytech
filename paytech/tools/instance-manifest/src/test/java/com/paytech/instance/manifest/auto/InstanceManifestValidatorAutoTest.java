package com.paytech.instance.manifest.auto;

import com.paytech.instance.manifest.InstanceManifestValidator;
import com.paytech.instance.manifest.InstanceManifestValidatorImpl;
import com.paytech.instance.manifest.ManifestViolationCode;
import com.paytech.instance.manifest.ServiceRuntime;
import com.paytech.instance.manifest.test.ManifestSamples;
import com.paytech.instance.manifest.test.ManifestViolationChecker;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 实例清单 Schema v1 与跨字段规则校验。
 *
 * @see com.paytech.instance.manifest.InstanceManifestValidator#validate(Path)
 * @author Claude
 */
class InstanceManifestValidatorAutoTest {

    private final InstanceManifestValidator validator = new InstanceManifestValidatorImpl();

    @Test
    void test_normalFlow() {
        var result = validator.validate(ManifestSamples.manifest("main.yaml"));

        ManifestViolationChecker.of(result).valid();
        Assertions.assertEquals("main", result.manifest().instanceId());
        Assertions.assertEquals("2.3.0", result.manifest().platformVersion());
        Assertions.assertEquals(List.of(new ServiceRuntime("ledger", 18105, 11, "mor_ledger"),
            new ServiceRuntime("account", 18114, 12, "mor_account")), result.manifest().services());
    }

    @Test
    void test_unknownService() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-unknown-service.yaml")))
            .exactly("services.accounts=UNKNOWN_SERVICE", "versions.schema.account=SCHEMA_VERSION_SERVICE_NOT_DEPLOYED");
    }

    @Test
    void test_duplicatePort() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-duplicate-port.yaml")))
            .exactly("services.account.port=DUPLICATE_PORT", "services.account.port=DUPLICATE_PORT",
                "services.api-gateway.port=DUPLICATE_PORT")
            .message(ManifestViolationCode.DUPLICATE_PORT, "业务端口 18105 与 ledger")
            .message(ManifestViolationCode.DUPLICATE_PORT, "管理端口 18105 与 ledger");
    }

    @Test
    void test_duplicateWorkerId() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-duplicate-worker-id.yaml")))
            .exactly("services.account.snowflakeWorkerId=DUPLICATE_WORKER_ID")
            .message(ManifestViolationCode.DUPLICATE_WORKER_ID, "已分配给 ledger");
    }

    @Test
    void test_modules() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-modules.yaml")))
            .exactly("modules.card.version=MODULE_VERSION_REQUIRED", "modules.loyalty=UNKNOWN_MODULE");
    }

    @Test
    void test_brandLegalParty() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-brand-legal-party.yaml")))
            .exactly("brands[0].legalPartyRef=BLANK_VALUE", "brands[1].legalPartyRef=REQUIRED",
                "brands[1].code=DUPLICATE_BRAND");
    }

    @Test
    void test_invalidParameter() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-enums.yaml")))
            .exactly("instance.operationMode=ENUM_MISMATCH", "instance.businessModes[1]=ENUM_MISMATCH",
                "instance.businessModes[2]=DUPLICATE_ITEM");
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-types-and-ranges.yaml")))
            .exactly("services.ledger.port=TYPE_MISMATCH", "services.ledger.snowflakeWorkerId=OUT_OF_RANGE",
                "services.ledger.db=DATABASE_MISMATCH", "versions.schema.ledger=OUT_OF_RANGE",
                "versions.schema.tax=SCHEMA_VERSION_SERVICE_NOT_DEPLOYED")
            .message(ManifestViolationCode.DATABASE_MISMATCH, "应为 mor_ledger");
    }

    @Test
    void test_noDataFound() {
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-missing-sections.yaml")))
            .exactly("services=REQUIRED", "versions=REQUIRED", "owner=UNKNOWN_PROPERTY");
        ManifestViolationChecker.of(validator.validate(ManifestSamples.manifest("invalid-duplicate-key.yaml")))
            .exactly("=UNREADABLE");
        ManifestViolationChecker.of(validator.validate(Path.of("does-not-exist.yaml")))
            .exactly("=UNREADABLE");
        ManifestViolationChecker.of(validator.validateContent("empty", ""))
            .exactly("=UNREADABLE");
    }

    @Test
    void test_edgeCase() {
        // 无数据库的服务不得声明 db；有数据库的服务必须声明且与服务清单一致。
        String manifest = """
            instance: {id: edge, operator: PayTech, operationMode: MANAGED, businessModes: [MOR]}
            modules: {}
            brands: [{code: edge, domains: [pay.example.com], legalPartyRef: PARTY-1}]
            channels: [NI]
            services:
              api-gateway: {port: 18080, snowflakeWorkerId: 0, db: mor_gateway}
              ledger: {port: 18105, snowflakeWorkerId: 31}
            versions: {platform: 2.3.0, schema: {}}
            """;

        ManifestViolationChecker.of(validator.validateContent("edge", manifest))
            .exactly("services.api-gateway.db=DATABASE_MISMATCH", "services.ledger.db=DATABASE_MISMATCH")
            .message(ManifestViolationCode.DATABASE_MISMATCH, "没有数据库");
    }
}
