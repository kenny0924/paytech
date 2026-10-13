package com.paytech.risk.auto;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.risk.configuration.PersistenceConfiguration;
import com.paytech.risk.configuration.RiskRuntimeConfiguration;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.domain.enums.CommandIdempotencyStatus;
import com.paytech.risk.domain.enums.CommandOperationType;
import com.paytech.risk.test.infrastructure.RiskDatabasePrecondition;
import java.lang.reflect.Method;
import java.nio.file.Path;
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
 * 在默认 Maven 测试中保护模块依赖和本轮启动范围。
 */
class RiskModuleBoundaryAutoTest {

    private static final Set<String> LIBRARIES = Set.of("api", "decision", "control", "compliance");

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(Set.of("risk-api", "risk-decision",
            "risk-control", "risk-compliance", "risk-web",
            "risk-test"), values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveOnlyApprovedDependencies() throws Exception {
        for (String module : LIBRARIES) {
            Document pom = readPom("risk-" + module);
            Set<String> dependencies = values(pom, "/project/dependencies/dependency/artifactId");
            if (module.equals("api")) {
                Assertions.assertEquals(Set.of("paytech-common-core", "spring-web", "jakarta.validation-api"),
                    dependencies);
            } else if (module.equals("decision")) {
                Assertions.assertEquals(Set.of("paytech-common-core", "paytech-json-canonicalization",
                    "paytech-mybatis-support", "paytech-redis-starter", "mybatis", "spring-context", "spring-tx", "lombok",
                    "jackson-databind"), dependencies);
                Assertions.assertEquals(Set.of("provided"),
                    values(pom, "/project/dependencies/dependency[artifactId='lombok']/scope"));
            } else if (module.equals("compliance")) {
                Assertions.assertEquals(Set.of("paytech-common-core", "mybatis", "spring-context", "spring-tx",
                    "spring-boot", "jackson-databind", "lombok"), dependencies);
                Assertions.assertEquals(Set.of("provided"),
                    values(pom, "/project/dependencies/dependency[artifactId='lombok']/scope"));
            } else if (module.equals("control")) {
                Assertions.assertEquals(Set.of("paytech-common-core", "spring-context", "lombok"), dependencies);
                Assertions.assertEquals(Set.of("provided"),
                    values(pom, "/project/dependencies/dependency[artifactId='lombok']/scope"));
            } else {
                Assertions.assertTrue(dependencies.isEmpty(), "当前业务 JAR 不应引入依赖: " + module);
            }
        }
        Set<String> web = values(readPom("risk-web"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("risk-api", "risk-decision",
            "risk-control", "risk-compliance", "paytech-mvc-web-starter", "paytech-snowflake-id", "paytech-redis-starter"), web);
    }

    @Test
    void test_persistenceDependenciesStayInOwningModules() throws Exception {
        Document web = readPom("risk-web");
        Assertions.assertTrue(values(web, "/project/dependencies/dependency/artifactId")
            .containsAll(Set.of("spring-boot-starter-jdbc", "mybatis-spring-boot-starter", "mysql-connector-j")));
        Assertions.assertEquals(Set.of("runtime"), values(web,
            "/project/dependencies/dependency[artifactId='mysql-connector-j']/scope"));

        Document test = readPom("risk-test");
        Assertions.assertEquals(Set.of("test"), values(test,
            "/project/dependencies/dependency[artifactId='spring-jdbc']/scope"));
    }

    @Test
    void test_onlyApprovedDatabaseBansAreRemoved() throws Exception {
        Set<String> bans = values(readPom(""),
            "/project/build/plugins/plugin[artifactId='maven-enforcer-plugin']/executions/execution"
                + "[id='risk-bootstrap-scope']/configuration/rules/bannedDependencies/excludes/exclude");
        Assertions.assertEquals(Set.of("org.flywaydb:*", "org.liquibase:*", "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config",
            "org.springframework.amqp:*", "com.rabbitmq:*", "com.xuxueli:*",
            "com.paytech:paytech-compensation-event", "com.paytech:paytech-xxl-job-starter",
            "org.springframework:spring-webflux", "org.springframework.cloud:spring-cloud-starter-gateway*",
            "org.springframework.cloud:spring-cloud-starter-openfeign", "org.apache.dubbo:*", "org.mockito:*",
            "org.testcontainers:*"), bans);
    }

    @Test
    void test_onlyWebIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "decision", "control", "compliance", "test")) {
            Assertions.assertFalse(values(readPom("risk-" + module),
                "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"), values(readPom("risk-web"),
            "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                + "/executions/execution/configuration/classifier"));
        Document test = readPom("risk-test");
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
            "org/redisson/Redisson.class", "org/springframework/transaction/support/TransactionTemplate.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("org/springframework/amqp/rabbit/core/RabbitTemplate.class",
            "org/mockito/Mockito.class", "org/testcontainers/containers/MySQLContainer.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    @Test
    void test_persistenceConfigurationUsesExplicitMapperAndTypeHandlerBoundaries() throws Exception {
        MapperScan mapperScan = PersistenceConfiguration.class.getAnnotation(MapperScan.class);
        Assertions.assertNotNull(mapperScan);
        Assertions.assertEquals(Set.of("com.paytech.risk.decision.core.dal.mapper",
            "com.paytech.risk.core.dal.mapper", "com.paytech.risk.compliance.core.dal.mapper"), Set.of(mapperScan.basePackages()));
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
        Object operationTypeHandler = mybatis.getTypeHandlerRegistry()
            .getTypeHandler(CommandOperationType.class);
        Object idempotencyStatusHandler = mybatis.getTypeHandlerRegistry()
            .getTypeHandler(CommandIdempotencyStatus.class);
        Assertions.assertInstanceOf(CodeEnumTypeHandler.class, operationTypeHandler);
        Assertions.assertInstanceOf(CodeEnumTypeHandler.class, idempotencyStatusHandler);
        Assertions.assertNotSame(operationTypeHandler, idempotencyStatusHandler,
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
        Method generatorFactory = RiskRuntimeConfiguration.class
            .getDeclaredMethod("decisionIdentifierGenerator", long.class, long.class);
        generatorFactory.setAccessible(true);
        DecisionIdentifierGenerator generator = (DecisionIdentifierGenerator)generatorFactory
            .invoke(new RiskRuntimeConfiguration(), 3L, 5L);

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
        Assertions.assertEquals(Set.of("/internal/due-diligence/material-requirements", "/decision-rule-sets", "/decision-releases", "/decision-dictionaries"), Set.of(
            properties.getProperty("web.logging.metadata-only-path-prefixes[0]"),
            properties.getProperty("web.logging.metadata-only-path-prefixes[1]"),
            properties.getProperty("web.logging.metadata-only-path-prefixes[2]"), properties.getProperty("web.logging.metadata-only-path-prefixes[3]")));
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

    @Test
    void test_autoDatabaseTargetIsFixedToOwnedLoopbackSchema() {
        Properties properties = properties("application-auto.yml");
        String url = properties.getProperty("spring.datasource.url");
        String username = properties.getProperty("spring.datasource.username");
        Assertions.assertEquals(
            "jdbc:mysql://127.0.0.1:13306/mor_risk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            url);
        Assertions.assertEquals("risk_app", username);
        Assertions.assertEquals("${RISK_TEST_DB_PASSWORD:${MOR_RISK_DB_PASSWORD:}}",
            properties.getProperty("spring.datasource.password"));
        Assertions.assertEquals("never", properties.getProperty("spring.sql.init.mode"));
        Assertions.assertEquals("0", properties.getProperty("decision.identifier.region-id"));
        Assertions.assertEquals("0", properties.getProperty("decision.identifier.worker-id"));
        Assertions.assertDoesNotThrow(() -> RiskDatabasePrecondition.validateConnectionTarget(url, username));
        Properties connectionProperties = RiskDatabasePrecondition.connectionProperties(username, "test-only");
        Assertions.assertEquals(username, connectionProperties.getProperty("user"));
        Assertions.assertEquals("test-only", connectionProperties.getProperty("password"));
        Assertions.assertEquals("2000", connectionProperties.getProperty("connectTimeout"));
        Assertions.assertEquals("2000", connectionProperties.getProperty("socketTimeout"));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://localhost:13306/mor_risk", username));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://127.0.0.1:13306/mor_platform", username));
        Assertions.assertThrows(IllegalStateException.class,
            () -> RiskDatabasePrecondition.validateConnectionTarget(
                "jdbc:mysql://127.0.0.1:13306/mor_risk?createDatabaseIfNotExist=true", username));
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
