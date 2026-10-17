package com.paytech.arch.auto;

import com.paytech.arch.catalog.ServiceCatalog;
import com.paytech.arch.catalog.ServiceLayer;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.pom.PomReader;
import com.paytech.arch.test.Fixtures;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 模块坐标归属判定与 POM 树读取。
 *
 * @see com.paytech.arch.model.ModuleIdentity#classify(String, String)
 * @see com.paytech.arch.pom.PomReader#readReactor(java.nio.file.Path)
 * @author Claude
 */
class ModuleModelAutoTest {

    @Test
    void test_normalFlow() {
        assertIdentity("paytech-ledger-api", "ledger", ModuleRole.API);
        assertIdentity("paytech-ledger-core", "ledger", ModuleRole.CORE);
        assertIdentity("paytech-acquiring-transaction-core", "acquiring", ModuleRole.CORE);
        assertIdentity("paytech-platform-support-access-control", "platform-support", ModuleRole.CORE);
        assertIdentity("paytech-risk-decision-app", "risk-decision", ModuleRole.APP);
        assertIdentity("paytech-client-master-test", "client-master", ModuleRole.TEST);
        assertIdentity("paytech-client-console-bff-app", "client-console-bff", ModuleRole.APP);
        assertIdentity("paytech-data-service-api", "data-service", ModuleRole.API);
        assertIdentity("paytech-vault-client", "vault", ModuleRole.CLIENT);
        assertIdentity("paytech-ledger", "ledger", ModuleRole.SERVICE_AGGREGATOR);
    }

    @Test
    void test_edgeCase() {
        Assertions.assertEquals(ModuleRole.FOUNDATION, ModuleIdentity.classify("com.paytech", "paytech-common-core").role());
        Assertions.assertEquals(ModuleRole.FOUNDATION, ModuleIdentity.classify("com.paytech", "paytech-messaging").role());
        Assertions.assertEquals(ModuleRole.BUILD, ModuleIdentity.classify("com.paytech", "paytech-parent").role());
        Assertions.assertEquals(ModuleRole.TOOL,
            ModuleIdentity.classify("com.paytech", "paytech-arch-rules-reactor-test").role());
        // 搬迁前的旧模块与第三方制品不归属任何服务。
        Assertions.assertEquals(ModuleRole.UNCLASSIFIED,
            ModuleIdentity.classify("com.paytech", "paytech-commerce-client-api").role());
        Assertions.assertEquals(ModuleRole.UNCLASSIFIED,
            ModuleIdentity.classify("org.springframework", "spring-web").role());
        Assertions.assertEquals(ServiceLayer.PROCESS, ServiceCatalog.layerOf("fx"));
        Assertions.assertEquals(ServiceLayer.DOMAIN, ServiceCatalog.layerOf("data-service"));
        Assertions.assertEquals(25, ServiceCatalog.serviceNames().size());
    }

    @Test
    void test_reactorRead() {
        List<MavenModule> modules = new PomReader().readReactor(Fixtures.path("graph/inherited"));

        Assertions.assertEquals(List.of("paytech", "paytech-ledger", "paytech-ledger-core"),
            modules.stream().map(MavenModule::artifactId).toList());
        MavenModule core = modules.get(2);
        Assertions.assertEquals("com.paytech", core.groupId());
        Assertions.assertEquals(List.of("com.paytech:paytech-payment-core"),
            core.productionDependencies().stream().map(dependency -> dependency.coordinate()).toList());
    }

    @Test
    void test_invalidParameter() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new PomReader().readModule(Fixtures.path("graph").resolve("missing-module")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ServiceCatalog.layerOf("fx-rate"));
    }

    private static void assertIdentity(String artifactId, String service, ModuleRole role) {
        ModuleIdentity identity = ModuleIdentity.classify("com.paytech", artifactId);
        Assertions.assertEquals(service, identity.service(), artifactId);
        Assertions.assertEquals(role, identity.role(), artifactId);
    }
}
