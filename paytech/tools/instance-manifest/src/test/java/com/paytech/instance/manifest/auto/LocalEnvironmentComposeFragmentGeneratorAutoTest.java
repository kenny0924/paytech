package com.paytech.instance.manifest.auto;

import com.paytech.instance.manifest.InstanceManifestValidatorImpl;
import com.paytech.instance.manifest.ManifestViolationCode;
import com.paytech.instance.manifest.catalog.ServiceCatalog;
import com.paytech.instance.manifest.compose.ComposeFragmentResult;
import com.paytech.instance.manifest.compose.LocalEnvironmentComposeFragmentGenerator;
import com.paytech.instance.manifest.compose.LocalEnvironmentComposeFragmentGeneratorImpl;
import com.paytech.instance.manifest.test.ManifestSamples;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 按实例清单生成 local-environment Compose 片段（快照比对）。
 *
 * @see com.paytech.instance.manifest.compose.LocalEnvironmentComposeFragmentGenerator#generate(java.nio.file.Path)
 * @author Claude
 */
class LocalEnvironmentComposeFragmentGeneratorAutoTest {

    private final ServiceCatalog catalog = ServiceCatalog.loadDefault();

    private final LocalEnvironmentComposeFragmentGenerator generator =
        new LocalEnvironmentComposeFragmentGeneratorImpl(catalog, new InstanceManifestValidatorImpl(catalog));

    @Test
    void test_normalFlow() {
        ComposeFragmentResult result = generator.generate(ManifestSamples.manifest("main.yaml"));

        Assertions.assertTrue(result.isGenerated(), () -> String.valueOf(result.violations()));
        Assertions.assertEquals(ManifestSamples.snapshot("main-compose-fragment.yaml"), result.fragment());
    }

    @Test
    void test_businessRuleViolation() {
        ComposeFragmentResult result = generator.generate(ManifestSamples.manifest("invalid-duplicate-port.yaml"));

        Assertions.assertFalse(result.isGenerated());
        Assertions.assertNull(result.fragment());
        Assertions.assertTrue(result.violations().stream()
            .allMatch(violation -> violation.code() == ManifestViolationCode.DUPLICATE_PORT));
    }

    @Test
    void test_edgeCase() {
        // 服务清单按 design/03 §1.2，去掉 2026-10-22 并入 Platform 的 workflow-approval 与 data-service：23 个逻辑服务（含预留的 partner-console-bff），端口不重复。
        Assertions.assertEquals(23, catalog.all().size());
        Assertions.assertEquals(23, catalog.all().stream().map(definition -> definition.port()).distinct().count());
        Assertions.assertEquals("client-master-service", catalog.find("client-master").orElseThrow().applicationName());
        Assertions.assertNull(catalog.find("api-gateway").orElseThrow().database());
    }
}
