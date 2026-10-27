package com.paytech.compliance.auto.architecture;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.paytech.compliance.configuration.CompliancePersistenceConfiguration;
import com.paytech.compliance.configuration.ComplianceRuntimeConfiguration;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.test.clear.ComplianceDataCleaner;
import com.paytech.compliance.test.infrastructure.ComplianceDatabasePrecondition;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.messaging.catalog.YamlEventCatalog;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;

/**
 * 在默认 Maven 测试中保护 compliance 的模块依赖、装配、MQ 发布与测试库边界（与 risk-decision 的 RiskDecisionArchitectureTest 同口径，
 * design/10 §2.4、§6.2）。跨服务规则 R1–R12 由 {@link ArchitectureAutoTest} 执行。
 */
class ComplianceArchitectureTest {

    private static final String PREFIX = "paytech-compliance-";

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(Set.of("paytech-compliance-api", "paytech-compliance-core", "paytech-compliance-app",
            "paytech-compliance-test"), values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveOnlyApprovedDependencies() throws Exception {
        Assertions.assertEquals(Set.of("paytech-common-core", "spring-web", "jakarta.validation-api", "lombok"),
            values(readPom(PREFIX + "api"), "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("provided"),
            values(readPom(PREFIX + "api"), "/project/dependencies/dependency[artifactId='lombok']/scope"));
        Document core = readPom(PREFIX + "core");
        Assertions.assertEquals(Set.of("paytech-common-core", "paytech-compensation-event", "mybatis", "spring-context",
            "spring-tx", "spring-boot", "jackson-databind", "lombok"), values(core, "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("provided"),
            values(core, "/project/dependencies/dependency[artifactId='lombok']/scope"));
        Assertions.assertEquals(Set.of("paytech-compliance-api", "paytech-compliance-core", "paytech-mvc-web-starter",
            "paytech-internal-auth", "paytech-mybatis-support", "paytech-snowflake-id", "paytech-compensation-event",
            "paytech-messaging", "paytech-http-client-support", "paytech-channel-connector-api", "paytech-client-master-api"),
            values(readPom(PREFIX + "app"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId"));
    }

    @Test
    void test_persistenceDependenciesStayInOwningModules() throws Exception {
        Document app = readPom(PREFIX + "app");
        Assertions.assertTrue(values(app, "/project/dependencies/dependency/artifactId")
            .containsAll(Set.of("spring-boot-starter-jdbc", "mybatis-spring-boot-starter", "mysql-connector-j")));
        Assertions.assertEquals(Set.of("runtime"), values(app,
            "/project/dependencies/dependency[artifactId='mysql-connector-j']/scope"));
        Assertions.assertEquals(Set.of("test"), values(readPom(PREFIX + "test"),
            "/project/dependencies/dependency[artifactId='spring-jdbc']/scope"));
    }

    @Test
    void test_onlyApprovedDatabaseBansAreRemoved() throws Exception {
        Set<String> bans = values(readPom(""),
            "/project/build/plugins/plugin[artifactId='maven-enforcer-plugin']/executions/execution"
                + "[id='risk-bootstrap-scope']/configuration/rules/bannedDependencies/excludes/exclude");
        // 原 Risk 禁令原样复制（09 §1.4），追加其他服务 core / app 通配禁令（D09）。
        Assertions.assertEquals(Set.of("org.flywaydb:*", "org.liquibase:*", "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config",
            "org.springframework:spring-webflux", "org.springframework.cloud:spring-cloud-starter-gateway*",
            "org.springframework.cloud:spring-cloud-starter-openfeign", "org.apache.dubbo:*", "org.mockito:*",
            "org.testcontainers:*", "com.paytech:*-core", "com.paytech:*-app"), bans);
    }

    @Test
    void test_onlyAppIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "core", "test")) {
            Assertions.assertFalse(values(readPom(PREFIX + module),
                "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"), values(readPom(PREFIX + "app"),
            "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                + "/executions/execution/configuration/classifier"));
        Document test = readPom(PREFIX + "test");
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.install.skip"));
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.deploy.skip"));
    }

    /** 发布审核结果需要 RabbitMQ 与 paytech-messaging；名单筛查出站需要公共 HTTP 客户端；compliance 不使用 Redis，决策代码不在 classpath。 */
    @Test
    void test_runtimeHasOnlyApprovedInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class", "com/mysql/cj/jdbc/Driver.class",
            "com/paytech/foundation/id/IdGenerator.class", "com/alibaba/nacos/api/NacosFactory.class",
            "com/paytech/foundation/mybatis/typehandler/InstantTypeHandler.class",
            "org/springframework/transaction/support/TransactionTemplate.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class",
            "com/paytech/foundation/messaging/publish/RabbitDomainEventPublisher.class",
            "com/paytech/foundation/compensation/api/CompensationEventRegistrar.class",
            "com/paytech/foundation/httpclient/api/HttpServiceClientFactory.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("org/mockito/Mockito.class", "org/testcontainers/containers/MySQLContainer.class",
            "org/redisson/Redisson.class", "com/paytech/risk/decision/domain/runtime/DecisionEngine.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    @Test
    void test_persistenceConfigurationUsesExplicitMapperBoundary() throws Exception {
        MapperScan mapperScan = CompliancePersistenceConfiguration.class.getAnnotation(MapperScan.class);
        Assertions.assertNotNull(mapperScan);
        Assertions.assertEquals(Set.of("com.paytech.compliance.core.dal.mapper"), Set.of(mapperScan.basePackages()));
        Assertions.assertEquals("sqlSessionTemplate", mapperScan.sqlSessionTemplateRef());
        Method factory = CompliancePersistenceConfiguration.class.getDeclaredMethod("compliancePersistenceConfigurationCustomizer");
        factory.setAccessible(true);
        ConfigurationCustomizer customizer = (ConfigurationCustomizer)factory.invoke(new CompliancePersistenceConfiguration());
        Configuration mybatis = new Configuration();
        customizer.customize(mybatis);
        Assertions.assertFalse(mybatis.isUseGeneratedKeys());
        Assertions.assertInstanceOf(InstantTypeHandler.class, mybatis.getTypeHandlerRegistry().getTypeHandler(Instant.class));
    }

    /** compliance 有自己的 Snowflake worker，属性命名空间为 compliance.identifier.*（不再读取 decision.identifier.*）。 */
    @Test
    void test_runtimeConfigurationUsesComplianceIdentifierNamespace() throws Exception {
        ComplianceRuntimeConfiguration configuration = new ComplianceRuntimeConfiguration();
        Method sharedFactory = ComplianceRuntimeConfiguration.class.getDeclaredMethod("complianceIdGenerator", long.class,
            long.class);
        sharedFactory.setAccessible(true);
        Object shared = sharedFactory.invoke(configuration, 3L, 6L);
        Method portFactory = ComplianceRuntimeConfiguration.class.getDeclaredMethod("complianceIdentifierGenerator",
            IdGenerator.class);
        portFactory.setAccessible(true);
        ComplianceIdentifierGenerator generator = (ComplianceIdentifierGenerator)portFactory.invoke(configuration, shared);
        long first = generator.nextIdentifier();
        Assertions.assertNotEquals(first, generator.nextIdentifier());
        Assertions.assertEquals(3L, first >> 17 & 31L);
        Assertions.assertEquals(6L, first >> 12 & 31L);
        Properties properties = properties("application.yml");
        Assertions.assertEquals("${COMPLIANCE_SNOWFLAKE_REGION_ID}", properties.getProperty("compliance.identifier.region-id"));
        Assertions.assertEquals("${COMPLIANCE_SNOWFLAKE_WORKER_ID}", properties.getProperty("compliance.identifier.worker-id"));
        Assertions.assertNull(properties.getProperty("decision.identifier.region-id"));
    }

    @Test
    void test_applicationConfigurationIsStrictMetadataOnlyAndRecursiveForMappers() {
        Properties properties = properties("application.yml");
        Assertions.assertEquals("compliance-service", properties.getProperty("spring.application.name"));
        Assertions.assertEquals("18112", properties.getProperty("server.port"));
        Assertions.assertEquals("19112", properties.getProperty("management.server.port"));
        Assertions.assertEquals("${COMPLIANCE_DB_URL}", properties.getProperty("spring.datasource.url"));
        Assertions.assertEquals("classpath*:mapper/**/*.xml", properties.getProperty("mybatis.mapper-locations"));
        Assertions.assertEquals("true", properties.getProperty("spring.jackson.deserialization.fail-on-unknown-properties"));
        Set<String> metadataOnly = new HashSet<>();
        for (int index = 0; properties.getProperty("web.logging.metadata-only-path-prefixes[" + index + "]") != null; index++) {
            metadataOnly.add(properties.getProperty("web.logging.metadata-only-path-prefixes[" + index + "]"));
        }
        // 2026-10-27（S-16）：按 yml 实际读取，至少包含审核登记、运营审核与运营资格三个敏感前缀（4b661830 新增 /compliance-admin 后
        // 原精确断言未同步导致既有失败）。
        Assertions.assertTrue(metadataOnly.containsAll(Set.of("/client-onboarding-review", "/client-onboarding-admin",
            "/compliance-admin")), metadataOnly::toString);
        Assertions.assertEquals("false", properties.getProperty("onboarding.review.enabled"));
        Assertions.assertNull(properties.getProperty("onboarding.materials.enabled"));
        Assertions.assertNull(properties.getProperty("onboarding.materials.programs-json"));
        Assertions.assertEquals("${COMPLIANCE_SCREENING_CHANNEL_CODE:S_SCREENING_MOCK}",
            properties.getProperty("compliance.screening.channel-code"));
        Assertions.assertEquals("${COMPLIANCE_INTERNAL_AUTH_MODE:OFF}", properties.getProperty("internal-auth.mode"));
    }

    /** ClientReviewDecided v2 与 QualificationChanged 由 compliance 发布到 compliance.events；client-master 订阅，未路由按 FAIL 重试。 */
    @Test
    void test_domainEventPublicationIsRegistered() {
        Properties properties = properties("application.yml");
        Assertions.assertEquals("compliance", properties.getProperty("messaging.producer.service"));
        Assertions.assertEquals("FAIL", properties.getProperty("messaging.publisher.unroutable-policy"));
        Assertions.assertEquals("true", properties.getProperty("messaging.producer.sequencer.enabled"));
        var entry = YamlEventCatalog.fromClasspath().find("ClientReviewDecided").orElseThrow();
        Assertions.assertEquals("compliance", entry.producer());
        Assertions.assertEquals("DD_CASE", entry.aggregateType());
        Assertions.assertEquals("caseVersion", entry.aggregateVersionField());
        Assertions.assertEquals(List.of("client-master"), List.copyOf(entry.consumers()));
        Assertions.assertFalse(entry.strictOrder());
        Assertions.assertEquals(2, entry.schemaVersion());
        var qualification = YamlEventCatalog.fromClasspath().find("QualificationChanged").orElseThrow();
        Assertions.assertEquals("compliance", qualification.producer());
        Assertions.assertEquals("ROLE_QUALIFICATION", qualification.aggregateType());
        Assertions.assertEquals("qualificationVersion", qualification.aggregateVersionField());
        Assertions.assertEquals(List.of("client-master"), List.copyOf(qualification.consumers()));
        Assertions.assertEquals("compliance", YamlEventCatalog.fromClasspath().find("SettlementRestrictionChanged")
            .orElseThrow().producer());
    }

    /** mock Profile 的数据库目标固定为本机 Loopback 的 mor_compliance 与 compliance_app 账号，且从不执行 SQL 初始化。 */
    @Test
    void test_mockDatabaseTargetIsFixedToOwnedLoopbackSchema() {
        Properties properties = properties("application-mock.yml");
        String url = properties.getProperty("spring.datasource.url");
        String username = properties.getProperty("spring.datasource.username");
        Assertions.assertEquals(
            "jdbc:mysql://127.0.0.1:13306/mor_compliance?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", url);
        Assertions.assertEquals("compliance_app", username);
        Assertions.assertTrue(properties.getProperty("spring.datasource.password")
            .startsWith("${COMPLIANCE_TEST_DB_PASSWORD:${MOR_COMPLIANCE_DB_PASSWORD:"));
        Assertions.assertEquals("never", properties.getProperty("spring.sql.init.mode"));
        Assertions.assertDoesNotThrow(() -> ComplianceDatabasePrecondition.validateConnectionTarget(url, username));
        Assertions.assertThrows(IllegalStateException.class, () -> ComplianceDatabasePrecondition.validateConnectionTarget(
            "jdbc:mysql://127.0.0.1:13306/mor_risk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", username));
        Assertions.assertThrows(IllegalStateException.class, () -> ComplianceDatabasePrecondition.validateConnectionTarget(
            "jdbc:mysql://127.0.0.1:13306/mor_compliance?createDatabaseIfNotExist=true", username));
        Assertions.assertThrows(IllegalStateException.class,
            () -> ComplianceDatabasePrecondition.validateConnectionTarget(url, "risk_app"));
    }

    /** A7：全表清理器的表集合等于 init/ddl.sql 的 CREATE TABLE 集合，前置检查的表数与之一致；Mapper XML 只引用这些表。 */
    @Test
    void test_cleanerAndPreconditionMatchTestDdl() throws Exception {
        Path root = Path.of(System.getProperty("compliance.reactor.root"));
        Set<String> ddl = tables(Files.readString(root.resolve(PREFIX + "test/src/test/resources/init/ddl.sql")),
            Pattern.compile("(?m)^CREATE TABLE `?(t_[a-z0-9_]+)`?"));
        Assertions.assertEquals(ddl, new TreeSet<>(ComplianceDataCleaner.TABLES));
        Assertions.assertEquals(ddl.size(), ComplianceDatabasePrecondition.EXPECTED_TABLE_COUNT);
        Set<String> mapped = new TreeSet<>();
        try (var files = Files.walk(root.resolve(PREFIX + "core/src/main/resources"))) {
            for (Path xml : files.filter(path -> path.toString().endsWith(".xml")).toList()) {
                mapped.addAll(tables(Files.readString(xml), Pattern.compile("\\b(t_[a-z0-9_]+)\\b")));
            }
        }
        Assertions.assertTrue(ddl.containsAll(mapped), () -> "Mapper 引用了本库以外的表: " + mapped);
    }

    private static Set<String> tables(String text, Pattern pattern) {
        Set<String> tables = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private Document readPom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path root = Path.of(System.getProperty("compliance.reactor.root"));
        return factory.newDocumentBuilder().parse(root.resolve(module).resolve("pom.xml").toFile());
    }

    private Set<String> values(Document document, String expression) throws Exception {
        NodeList nodes = (NodeList)XPathFactory.newInstance().newXPath().evaluate(expression, document, XPathConstants.NODESET);
        Set<String> values = new HashSet<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            values.add(nodes.item(index).getTextContent().trim());
        }
        return values;
    }

    private Properties properties(String resource) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        Properties properties = yaml.getObject();
        Assertions.assertNotNull(properties, resource);
        return properties;
    }
}
