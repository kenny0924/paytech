package com.paytech.reconciliation.auto;

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
 * 在默认 Maven 测试中保护 reconciliation-service 的模块集合、依赖清单与已批准的基础设施范围（Snowflake 与 MyBatis）
 * <li>由 FinanceModuleBoundaryTest 按服务拆分（design/09 §2.7）；跨服务依赖图规则由 ArchitectureAutoTest（R1–R12）检查。</li>
 * <li>本服务第 11 步前不得引入 MQ：RabbitTemplate 不在 classpath。</li>
 *
 * @see com.paytech.reconciliation.ReconciliationApplication
 */
class ReconciliationModuleBoundaryTest {

    @Test
    void test_reactorContainsOnlyApprovedModules() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-reconciliation-api", "paytech-reconciliation-core", "paytech-reconciliation-app", "paytech-reconciliation-test"),
            values(readPom(""), "/project/modules/module"));
    }

    @Test
    void test_businessLibrariesHaveApprovedDependencies() throws Exception {
        Assertions.assertEquals(
            Set.of("paytech-reconciliation-api", "paytech-common-core", "paytech-mybatis-support",
                "spring-context", "mybatis", "lombok"),
            values(readPom("paytech-reconciliation-core"), "/project/dependencies/dependency/artifactId"));
        Assertions.assertEquals(Set.of("lombok"),
            values(readPom("paytech-reconciliation-api"), "/project/dependencies/dependency/artifactId"));
        Set<String> app = values(readPom("paytech-reconciliation-app"),
            "/project/dependencies/dependency[groupId='com.paytech']/artifactId");
        Assertions.assertEquals(Set.of("paytech-reconciliation-api", "paytech-reconciliation-core", "paytech-snowflake-id",
                "paytech-mybatis-support", "paytech-mvc-web-starter"), app);
    }

    @Test
    void test_onlyAppIsExecutableAndTestIsNotPublished() throws Exception {
        for (String module : Set.of("api", "core", "test")) {
            Assertions.assertFalse(values(readPom("paytech-reconciliation-" + module),
                "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        }
        Assertions.assertEquals(Set.of("exec"),
            values(readPom("paytech-reconciliation-app"),
                "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                    + "/executions/execution/configuration/classifier"));
        Document test = readPom("paytech-reconciliation-test");
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.install.skip"));
        Assertions.assertEquals(Set.of("true"), values(test, "/project/properties/maven.deploy.skip"));
    }

    @Test
    void test_runtimeContainsOnlyApprovedInfrastructure() {
        for (String resource : Set.of("org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class",
            "com/mysql/cj/jdbc/Driver.class")) {
            Assertions.assertNotNull(getClass().getClassLoader().getResource(resource), resource);
        }
        for (String resource : Set.of("com/alibaba/nacos/api/NacosFactory.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class",
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
        Path root = Path.of(System.getProperty("reconciliation.reactor.root"));
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
