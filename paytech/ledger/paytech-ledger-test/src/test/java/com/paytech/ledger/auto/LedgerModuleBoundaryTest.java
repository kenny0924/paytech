package com.paytech.ledger.auto;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * 在默认 Maven 测试中保护 ledger-service 的模块集合、依赖清单与已批准的基础设施范围（Redis 资源锁、Snowflake、MyBatis、paytech-messaging
 * 消费端与内部 HTTP 客户端）
 * <li>由 FinanceModuleBoundaryTest 按服务拆分（design/09 §2.7）；跨服务依赖图规则由 ArchitectureAutoTest（R1–R12）检查。</li>
 * <li>ledger 消费 account 的 AccountPosted（2026-10-19 起替代 BillingFactRecorded）：RabbitTemplate 必须在 classpath；account-api 提供
 * 载荷与过账进度 / 批次合同，billing-api 提供事实详情与发布进度合同；不注册补偿 Handler，补偿组件仍不得引入。</li>
 *
 * @see com.paytech.ledger.LedgerApplication
 */
class LedgerModuleBoundaryTest {

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-ledger-api", "paytech-ledger-core", "paytech-ledger-app", "paytech-ledger-test"),
            values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveApprovedDependencies() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-ledger-api", "paytech-billing-api", "paytech-account-api",
                "paytech-platform-support-api", "paytech-mybatis-support", "paytech-redis-starter",
                "paytech-snowflake-id", "paytech-json-canonicalization", "jackson-core", "paytech-messaging-api",
                "jackson-databind", "jakarta.validation-api", "spring-context", "spring-tx", "mybatis", "lombok"),
            values(readPom("paytech-ledger-core"), "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("spring-web", "jakarta.validation-api", "paytech-common-core", "lombok"),
            values(readPom("paytech-ledger-api"), "/project/dependencies/dependency/artifactId"));
        Set<String> app = values(readPom("paytech-ledger-app"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("paytech-ledger-api", "paytech-ledger-core", "paytech-redis-starter", "paytech-snowflake-id",
                "paytech-mybatis-support", "paytech-mvc-web-starter", "paytech-messaging", "paytech-account-api",
                "paytech-billing-api", "paytech-http-client-support"), app);
    }

    @Test
    void test_onlyAppIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "core", "test")) {
            Assertions.assertFalse(values(readPom("paytech-ledger-" + module),
                "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"),
            values(readPom("paytech-ledger-app"),
                "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                    + "/executions/execution/configuration/classifier"));
        Document test = readPom("paytech-ledger-test");
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.install.skip"));
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.deploy.skip"));
    }

    @Test
    void test_runtimeContainsOnlyApprovedInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class",
            "com/mysql/cj/jdbc/Driver.class",
            "org/redisson/Redisson.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("com/alibaba/nacos/api/NacosFactory.class",
            "com/paytech/foundation/compensation/api/CompensationEventRegistrar.class",
            "org/mockito/Mockito.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    private Document readPom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path root = Path.of(System.getProperty("ledger.reactor.root"));
        return factory.newDocumentBuilder().parse(root.resolve(module).resolve("pom.xml").toFile());
    }

    private Set<String> values(Document document, String expression) throws Exception {
        NodeList nodes =
            (NodeList)XPathFactory.newInstance().newXPath().evaluate(expression, document, XPathConstants.NODESET);
        Set<String> values = new HashSet<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            values.add(nodes.item(index).getTextContent().trim());
        }
        return values;
    }
}
