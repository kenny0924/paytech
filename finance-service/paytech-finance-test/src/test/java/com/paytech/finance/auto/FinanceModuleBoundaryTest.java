package com.paytech.finance.auto;

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
 * 在默认 Maven 测试中保护模块依赖和已批准的基础设施范围（本期新增 Redis 资源锁、Snowflake 与 RFC 8785）。
 *
 * @see com.paytech.finance.FinanceApplication
 */
class FinanceModuleBoundaryTest {

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-finance-api", "paytech-finance-accounting", "paytech-finance-treasury",
                "paytech-finance-tax", "paytech-finance-web", "paytech-finance-test"),
            values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveNoCrossModuleDependencies() throws Exception {
        for (String module : Set.of("treasury", "tax")) {
            Document pom = readPom("paytech-finance-" + module);
            Assertions.assertTrue(values(pom, "/project/dependencies/dependency/artifactId").isEmpty(),
                "启动阶段业务 JAR 不应引入依赖: " + module);
        }
        Assertions.assertEquals(
            Set.of("paytech-mybatis-support", "paytech-redis-starter", "paytech-snowflake-id",
                "paytech-json-canonicalization", "jackson-core", "jakarta.validation-api", "spring-context",
                "spring-tx", "mybatis", "lombok"),
            values(readPom("paytech-finance-accounting"), "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("spring-web", "jakarta.validation-api", "paytech-common-core", "lombok"),
            values(readPom("paytech-finance-api"), "/project/dependencies/dependency/artifactId"));
        Set<String> web = values(readPom("paytech-finance-web"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("paytech-finance-api", "paytech-finance-accounting", "paytech-finance-treasury",
            "paytech-finance-tax", "paytech-snowflake-id", "paytech-mybatis-support", "paytech-mvc-web-starter"), web);
    }

    @Test
    void test_onlyWebIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "accounting", "treasury", "tax", "test")) {
            Assertions
                .assertFalse(values(readPom("paytech-finance-" + module), "/project/build/plugins/plugin/artifactId")
                    .contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"),
            values(readPom("paytech-finance-web"),
                "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                    + "/executions/execution/configuration/classifier"));
        Document test = readPom("paytech-finance-test");
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.install.skip"));
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.deploy.skip"));
    }

    @Test
    void test_runtimeContainsOnlyApprovedAccountingInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class", "com/mysql/cj/jdbc/Driver.class",
            "org/redisson/Redisson.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("com/alibaba/nacos/api/NacosFactory.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class", "org/mockito/Mockito.class")) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    private Document readPom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Path root = Path.of(System.getProperty("finance.reactor.root"));
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
