package com.paytech.billing.auto;

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
 * 在默认 Maven 测试中保护 billing-service 的模块集合、依赖清单与已批准的基础设施范围（Redis 资源锁、Snowflake 与 MyBatis）
 * <li>由 FinanceModuleBoundaryTest 按服务拆分（design/09 §2.7）；跨服务依赖图规则由 ArchitectureAutoTest（R1–R12）检查。</li>
 * <li>BillingFactRecorded 由补偿 Handler 经 paytech-messaging 发布到 RabbitMQ；币种快照经 paytech-http-client-support 读取 platform-support；
 * 自动 PAYMENT 事实登记消费 acquiring 事件并读取 commercial、channel-connector、reconciliation、ledger 四个领域 / 支撑层 api（不依赖流程层 api，D08）；
 * Nacos 与 Mockito 不得进入 classpath。</li>
 *
 * @see com.paytech.billing.BillingApplication
 */
class BillingModuleBoundaryTest {

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-billing-api", "paytech-billing-core", "paytech-billing-app", "paytech-billing-test"),
            values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveApprovedDependencies() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-billing-api", "paytech-platform-support-api", "paytech-commercial-api",
                "paytech-channel-connector-api", "paytech-reconciliation-api", "paytech-ledger-api", "paytech-mybatis-support",
                "paytech-redis-starter", "paytech-snowflake-id", "paytech-json-canonicalization",
                "paytech-compensation-event", "paytech-messaging-api", "jackson-core", "jackson-databind",
                "jakarta.validation-api", "spring-boot", "spring-context", "spring-tx", "mybatis", "lombok"),
            values(readPom("paytech-billing-core"), "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("spring-web", "jakarta.validation-api", "paytech-common-core", "lombok"),
            values(readPom("paytech-billing-api"), "/project/dependencies/dependency/artifactId"));
        Set<String> app = values(readPom("paytech-billing-app"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("paytech-billing-api", "paytech-billing-core", "paytech-redis-starter", "paytech-snowflake-id",
                "paytech-mybatis-support", "paytech-mvc-web-starter", "paytech-compensation-event", "paytech-messaging",
                "paytech-http-client-support"), app);
    }

    @Test
    void test_onlyAppIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "core", "test")) {
            Assertions.assertFalse(values(readPom("paytech-billing-" + module),
                "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"),
            values(readPom("paytech-billing-app"),
                "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                    + "/executions/execution/configuration/classifier"));
        Document test = readPom("paytech-billing-test");
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.install.skip"));
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.deploy.skip"));
    }

    @Test
    void test_runtimeContainsOnlyApprovedInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class",
            "com/mysql/cj/jdbc/Driver.class",
            "org/redisson/Redisson.class",
            // BillingFactRecorded 由补偿 Handler 经 paytech-messaging 发布到 RabbitMQ。
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("com/alibaba/nacos/api/NacosFactory.class",
            "org/mockito/Mockito.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    private Document readPom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path root = Path.of(System.getProperty("billing.reactor.root"));
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
