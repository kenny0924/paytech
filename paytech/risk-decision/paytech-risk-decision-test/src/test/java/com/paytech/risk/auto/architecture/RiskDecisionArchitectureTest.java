package com.paytech.risk.auto.architecture;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.risk.configuration.PersistenceConfiguration;
import com.paytech.risk.configuration.RiskRuntimeConfiguration;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.test.clear.RiskDecisionDataCleaner;
import com.paytech.risk.test.infrastructure.RiskDecisionDatabasePrecondition;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 在默认 Maven 测试中保护 risk-decision 的模块依赖、装配与测试库边界（由原 RiskModuleBoundaryAutoTest 改写，design/10 §2.4）。
 * <li>模块集合与依赖的跨服务规则由 {@link ArchitectureAutoTest}（R1–R12）执行；本类保留 POM 明细、Mapper / TypeHandler 装配、
 * Enforcer 清单与 mock 数据库目标断言。</li>
 */
class RiskDecisionArchitectureTest {

    private static final String PREFIX = "paytech-risk-decision-";

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(Set.of("paytech-risk-decision-api", "paytech-risk-decision-core",
            "paytech-risk-decision-app", "paytech-risk-decision-test"), values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveOnlyApprovedDependencies() throws Exception {
        // 承保合同（REQ-03）的多字段 Body 用 Lombok 生成访问方法，与其他服务 *-api 一致；仅编译期 provided。
        Document api = readPom(PREFIX + "api");
        Assertions.assertEquals(Set.of("paytech-common-core", "spring-web", "jakarta.validation-api", "lombok"),
            values(api, "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("provided"),
            values(api, "/project/dependencies/dependency[artifactId='lombok']/scope"));
        Document core = readPom(PREFIX + "core");
        // risk-control 并入后：名单持久化与名单维护锁、影子回放补偿任务、决策哈希规范化。
        Assertions.assertEquals(Set.of("paytech-common-core", "paytech-json-canonicalization",
            "paytech-compensation-event", "paytech-mybatis-support", "paytech-redis-starter", "mybatis",
            "spring-boot", "jakarta.validation-api", "spring-context", "spring-tx", "lombok",
            "jackson-databind"), values(core, "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("provided"),
            values(core, "/project/dependencies/dependency[artifactId='lombok']/scope"));
        Set<String> app = values(readPom(PREFIX + "app"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("paytech-risk-decision-api", "paytech-risk-decision-core",
            "paytech-mvc-web-starter", "paytech-internal-auth", "paytech-snowflake-id", "paytech-redis-starter",
            "paytech-compensation-event"), app);
    }

    @Test
    void test_persistenceDependenciesStayInOwningModules() throws Exception {
        Document web = readPom(PREFIX + "app");
        Assertions.assertTrue(values(web, "/project/dependencies/dependency/artifactId")
            .containsAll(Set.of("spring-boot-starter-jdbc", "mybatis-spring-boot-starter", "mysql-connector-j")));
        Assertions.assertEquals(Set.of("runtime"), values(web,
            "/project/dependencies/dependency[artifactId='mysql-connector-j']/scope"));

        Document test = readPom(PREFIX + "test");
        Assertions.assertEquals(Set.of("test"), values(test,
            "/project/dependencies/dependency[artifactId='spring-jdbc']/scope"));
    }

    @Test
    void test_onlyApprovedDatabaseBansAreRemoved() throws Exception {
        Set<String> bans = values(readPom(""),
            "/project/build/plugins/plugin[artifactId='maven-enforcer-plugin']/executions/execution"
                + "[id='risk-bootstrap-scope']/configuration/rules/bannedDependencies/excludes/exclude");
        // 原 Risk 禁令原样保留（09 §1.4），追加其他服务 core / app 通配禁令（D09）；放行自身 core / app 与 paytech-common-core。
        Assertions.assertEquals(Set.of("org.flywaydb:*", "org.liquibase:*", "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config",
            "org.springframework:spring-webflux", "org.springframework.cloud:spring-cloud-starter-gateway*",
            "org.springframework.cloud:spring-cloud-starter-openfeign", "org.apache.dubbo:*", "org.mockito:*",
            "org.testcontainers:*", "com.paytech:*-core", "com.paytech:*-app"), bans);
    }

    @Test
    void test_onlyWebIsExecutableAndTestIsNotPublished() throws Exception {
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

    @Test
    void test_runtimeHasOnlyApprovedInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class", "com/mysql/cj/jdbc/Driver.class",
            "com/paytech/foundation/id/IdGenerator.class", "com/alibaba/nacos/api/NacosFactory.class",
            "com/paytech/foundation/jsoncanonicalization/api/Rfc8785JsonCanonicalizer.class",
            "com/paytech/foundation/mybatis/typehandler/InstantTypeHandler.class",
            "org/redisson/Redisson.class", "org/springframework/transaction/support/TransactionTemplate.class",
            "com/paytech/foundation/compensation/api/CompensationEventRegistrar.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        // 审核结果发布随 compliance 拆走：risk-decision 不发布也不消费 RabbitMQ（A10），合规代码不在 classpath。
        for (String resource : Set.of("org/mockito/Mockito.class", "org/testcontainers/containers/MySQLContainer.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class",
            "com/paytech/compliance/domainservice/service/ClientReviewService.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    @Test
    void test_persistenceConfigurationUsesExplicitMapperAndTypeHandlerBoundaries() throws Exception {
        MapperScan mapperScan = PersistenceConfiguration.class.getAnnotation(MapperScan.class);
        Assertions.assertNotNull(mapperScan);
        Assertions.assertEquals(Set.of("com.paytech.risk.decision.core.dal.mapper",
            "com.paytech.risk.control.core.dal.mapper"), Set.of(mapperScan.basePackages()));
        Assertions.assertEquals("sqlSessionTemplate", mapperScan.sqlSessionTemplateRef());

        PersistenceConfiguration persistence = new PersistenceConfiguration();
        Method customizerFactory = PersistenceConfiguration.class
            .getDeclaredMethod("persistenceConfigurationCustomizer");
        customizerFactory.setAccessible(true);
        ConfigurationCustomizer customizer = (ConfigurationCustomizer)customizerFactory.invoke(persistence);
        Configuration mybatis = new Configuration();
        customizer.customize(mybatis);

        Assertions.assertFalse(mybatis.isUseGeneratedKeys());
        Assertions.assertInstanceOf(InstantTypeHandler.class,
            mybatis.getTypeHandlerRegistry().getTypeHandler(Instant.class));
        Object valueTypeHandler = mybatis.getTypeHandlerRegistry()
            .getTypeHandler(DecisionValueType.class);
        Object dictionaryStatusHandler = mybatis.getTypeHandlerRegistry()
            .getTypeHandler(DictionaryStatus.class);
        Assertions.assertInstanceOf(CodeEnumTypeHandler.class, valueTypeHandler);
        Assertions.assertInstanceOf(CodeEnumTypeHandler.class, dictionaryStatusHandler);
        Assertions.assertNotSame(valueTypeHandler, dictionaryStatusHandler,
            "每个闭合枚举必须绑定自己的 CodeEnumTypeHandler 实例");

        Method enumRegistration = PersistenceConfiguration.class.getDeclaredMethod("registerCodeEnumHandler",
            TypeHandlerRegistry.class, Class.class);
        enumRegistration.setAccessible(true);
        enumRegistration.invoke(null, mybatis.getTypeHandlerRegistry(), PersistedTestStatus.class);
        Assertions.assertInstanceOf(CodeEnumTypeHandler.class,
            mybatis.getTypeHandlerRegistry().getTypeHandler(PersistedTestStatus.class));
        Assertions.assertFalse(mybatis.getTypeHandlerRegistry().getTypeHandler(CodeEnum.class)
            instanceof CodeEnumTypeHandler, "CodeEnum 不得成为全局默认 TypeHandler");
    }

    @Test
    void test_runtimeConfigurationAdaptsFoundationSnowflakeBehindDecisionPort() throws Exception {
        RiskRuntimeConfiguration configuration = new RiskRuntimeConfiguration();
        Method sharedFactory = RiskRuntimeConfiguration.class.getDeclaredMethod("riskIdGenerator", long.class, long.class);
        sharedFactory.setAccessible(true);
        Object shared = sharedFactory.invoke(configuration, 3L, 5L);
        Method generatorFactory = RiskRuntimeConfiguration.class
            .getDeclaredMethod("decisionIdentifierGenerator", com.paytech.foundation.id.IdGenerator.class);
        generatorFactory.setAccessible(true);
        DecisionIdentifierGenerator generator = (DecisionIdentifierGenerator)generatorFactory.invoke(configuration, shared);

        long first = generator.nextIdentifier();
        long second = generator.nextIdentifier();
        Assertions.assertTrue(first > 0L);
        Assertions.assertNotEquals(first, second);
        Assertions.assertEquals(3L, first >> 17 & 31L);
        Assertions.assertEquals(5L, first >> 12 & 31L);
    }

    @Test
    void test_applicationConfigurationIsStrictMetadataOnlyAndRecursiveForMappers() {
        Properties properties = properties("application.yml");
        Assertions.assertEquals("${RISK_DB_URL}", properties.getProperty("spring.datasource.url"));
        Assertions.assertEquals("${RISK_DB_USERNAME}", properties.getProperty("spring.datasource.username"));
        Assertions.assertEquals("${RISK_DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
        Assertions.assertEquals("classpath*:mapper/**/*.xml", properties.getProperty("mybatis.mapper-locations"));
        Assertions.assertEquals("true",
            properties.getProperty("spring.jackson.deserialization.fail-on-unknown-properties"));
        Assertions.assertEquals("false",
            properties.getProperty("spring.jackson.deserialization.fail-on-missing-creator-properties"));
        Set<String> metadataOnly = new HashSet<>();
        for (int index = 0; properties.getProperty("web.logging.metadata-only-path-prefixes[" + index + "]") != null; index++) {
            metadataOnly.add(properties.getProperty("web.logging.metadata-only-path-prefixes[" + index + "]"));
        }
        Assertions.assertEquals(Set.of("/decision-dictionaries", "/decision-rule-sets", "/decision-releases", "/decision-flows",
            "/decision-variables", "/risk-lists", "/risk-decisions", "/risk-underwriting",
            "/risk-limit-tiers"), metadataOnly);
        Assertions.assertEquals("${RISK_DECISION_ENVIRONMENT}", properties.getProperty("decision.runtime.environment"));
        Assertions.assertEquals("${RISK_SNOWFLAKE_REGION_ID}",
            properties.getProperty("decision.identifier.region-id"));
        Assertions.assertEquals("${RISK_SNOWFLAKE_WORKER_ID}",
            properties.getProperty("decision.identifier.worker-id"));
    }

    @Test
    void test_applicationJacksonConfigurationBindsToBootMapper() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            new ConfigDataApplicationContextInitializer().initialize(context);
            context.register(JacksonAutoConfiguration.class);
            context.refresh();
            JsonMapper mapper = context.getBean(JsonMapper.class);

            StrictPayload accepted = Assertions.assertDoesNotThrow(() -> mapper.readValue(
                "{\"required\":\"configured\",\"nullable\":null}", StrictPayload.class));
            Assertions.assertEquals("configured", accepted.required());
            Assertions.assertNull(accepted.nullable());
            Assertions.assertThrows(UnrecognizedPropertyException.class, () -> mapper.readValue(
                "{\"required\":\"configured\",\"nullable\":null,\"unknown\":true}", StrictPayload.class));
            Assertions.assertThrows(MismatchedInputException.class, () -> mapper.readValue(
                "{\"required\":\"configured\"}", StrictPayload.class));
        }
    }

    /** mock Profile 的数据库目标固定为本机 Loopback 的 mor_risk_decision 与 risk_decision_app 账号，且从不执行 SQL 初始化。 */
    @Test
    void test_mockDatabaseTargetIsFixedToOwnedLoopbackSchema() {
        Properties properties = properties("application-mock.yml");
        String url = properties.getProperty("spring.datasource.url");
        String username = properties.getProperty("spring.datasource.username");
        Assertions.assertEquals(
            "jdbc:mysql://127.0.0.1:13306/mor_risk_decision?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            url);
        Assertions.assertEquals("risk_decision_app", username);
        // 环境变量优先，缺省为仓库 local-environment 的 Loopback 本地密码；断言只核对覆盖顺序，不复述密码值。
        Assertions.assertTrue(properties.getProperty("spring.datasource.password")
            .startsWith("${RISK_DECISION_TEST_DB_PASSWORD:${MOR_RISK_DECISION_DB_PASSWORD:"));
        Assertions.assertEquals("never", properties.getProperty("spring.sql.init.mode"));
        Assertions.assertEquals("0", properties.getProperty("decision.identifier.region-id"));
        Assertions.assertEquals("0", properties.getProperty("decision.identifier.worker-id"));
        Assertions.assertDoesNotThrow(() -> RiskDecisionDatabasePrecondition.validateConnectionTarget(url, username));
        Properties connectionProperties = RiskDecisionDatabasePrecondition.connectionProperties(username, "test-only");
        Assertions.assertEquals(username, connectionProperties.getProperty("user"));
        Assertions.assertEquals("test-only", connectionProperties.getProperty("password"));
        Assertions.assertEquals("2000", connectionProperties.getProperty("connectTimeout"));
        Assertions.assertEquals("2000", connectionProperties.getProperty("socketTimeout"));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDecisionDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://localhost:13306/mor_risk_decision", username));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDecisionDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://127.0.0.1:13306/mor_platform", username));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDecisionDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://127.0.0.1:13306/mor_risk_decision?createDatabaseIfNotExist=true", username));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDecisionDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://127.0.0.1:13306/mor_risk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                username), "拆分前的共享库 mor_risk 不再是本服务的测试库");
    }

    /** A7：全表清理器触碰的表集合等于 init/ddl.sql 的 CREATE TABLE 集合，前置检查的表数与之一致；Mapper XML 只引用这些表。 */
    @Test
    void test_cleanerAndPreconditionMatchTestDdl() throws Exception {
        Path module = Path.of(System.getProperty("risk.reactor.root")).resolve(PREFIX + "test");
        Set<String> ddl = tables(Files.readString(module.resolve("src/test/resources/init/ddl.sql")),
            Pattern.compile("(?m)^CREATE TABLE `?(t_[a-z0-9_]+)`?"));
        Assertions.assertEquals(ddl, new TreeSet<>(RiskDecisionDataCleaner.tables()));
        Assertions.assertEquals(ddl.size(), RiskDecisionDatabasePrecondition.EXPECTED_TABLE_COUNT);
        Set<String> mapped = new TreeSet<>();
        try (var files = Files.walk(Path.of(System.getProperty("risk.reactor.root")).resolve(PREFIX + "core/src/main/resources"))) {
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
        Path root = Path.of(System.getProperty("risk.reactor.root"));
        return factory.newDocumentBuilder().parse(root.resolve(module).resolve("pom.xml").toFile());
    }

    private Set<String> values(Document document, String expression) throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate(expression, document,
            XPathConstants.NODESET);
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

    private enum PersistedTestStatus implements CodeEnum {
        ACTIVE;

        @Override
        public String getCode() {
            return name();
        }
    }

    private record StrictPayload(
        @com.fasterxml.jackson.annotation.JsonProperty(isRequired = com.fasterxml.jackson.annotation.OptBoolean.TRUE) String required,
        @com.fasterxml.jackson.annotation.JsonProperty(isRequired = com.fasterxml.jackson.annotation.OptBoolean.TRUE) String nullable) {
    }
}
