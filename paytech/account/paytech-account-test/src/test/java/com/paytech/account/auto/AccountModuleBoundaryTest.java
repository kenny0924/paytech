package com.paytech.account.auto;

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
 * 在默认 Maven 测试中保护 account-service 的模块集合、依赖清单与已批准的基础设施范围（Snowflake、MyBatis、paytech-messaging 消费与发布端、
 * paytech-compensation-event）；2026-10-19 起 core 不依赖 ledger-api，app 不再有出站 HTTP 客户端
 * <li>由 FinanceModuleBoundaryTest 按服务拆分（design/09 §2.7）；跨服务依赖图规则由 ArchitectureAutoTest（R1–R12）检查。</li>
 * <li>account 消费 EconomicFactRecorded 并发布 AccountPosted：RabbitTemplate 必须在 classpath；不使用资源锁，Redisson 仍不得引入。</li>
 *
 * @see com.paytech.account.AccountApplication
 */
class AccountModuleBoundaryTest {

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-account-api", "paytech-account-core", "paytech-account-app", "paytech-account-test"),
            values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveApprovedDependencies() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-account-api", "paytech-billing-api", "paytech-compensation-event", "paytech-messaging-api",
                "spring-boot", "jackson-databind", "paytech-mybatis-support", "paytech-snowflake-id", "jakarta.validation-api",
                "spring-context", "spring-tx", "slf4j-api", "mybatis", "lombok"),
            values(readPom("paytech-account-core"), "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("spring-web", "jakarta.validation-api", "paytech-common-core", "lombok"),
            values(readPom("paytech-account-api"), "/project/dependencies/dependency/artifactId"));
        Set<String> app = values(readPom("paytech-account-app"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("paytech-account-api", "paytech-account-core", "paytech-snowflake-id",
                "paytech-mybatis-support", "paytech-mvc-web-starter", "paytech-messaging", "paytech-compensation-event"), app);
    }

    @Test
    void test_onlyAppIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "core", "test")) {
            Assertions.assertFalse(values(readPom("paytech-account-" + module),
                "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"),
            values(readPom("paytech-account-app"),
                "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                    + "/executions/execution/configuration/classifier"));
        Document test = readPom("paytech-account-test");
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.install.skip"));
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.deploy.skip"));
    }

    @Test
    void test_runtimeContainsOnlyApprovedInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class",
            "com/mysql/cj/jdbc/Driver.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class",
            "com/xxl/job/core/handler/annotation/XxlJob.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("com/alibaba/nacos/api/NacosFactory.class",
            "org/mockito/Mockito.class",
            "org/redisson/Redisson.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    private Document readPom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path root = Path.of(System.getProperty("account.reactor.root"));
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
