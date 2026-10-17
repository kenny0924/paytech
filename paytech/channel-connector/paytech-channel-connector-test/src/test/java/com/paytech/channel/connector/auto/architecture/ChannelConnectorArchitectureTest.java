package com.paytech.payment.auto.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Channel 公开合同及五表所有权的源码门禁，Maven Enforcer 同时检查真实传递依赖。 */
class ChannelModuleBoundaryTest {
    private static final Set<String> PUBLIC_TYPES = Set.of(
        "domain.MidCandidateView", "domain.CapabilityView", "domain.ChannelExecutionConfigView",
        "domain.ChannelAccountConfiguration", "core.enums.ChannelOperation", "core.enums.ExecutionPurpose",
        "core.enums.CapabilityValueType", "service.processor.QueryMidCandidatesProcessor",
        "service.processor.GetChannelExecutionConfigProcessor",
        "service.processor.request.QueryMidCandidatesRequest", "service.processor.request.MidCriteria",
        "service.processor.request.GetChannelExecutionConfigRequest",
        // 经 Platform 执行渠道操作（requirements/20261014/payment-PayerMax卡直付对接）。
        "domain.ChannelOperationResult", "core.enums.ChannelOperationOutcome",
        "service.processor.ExecuteChannelOperationProcessor", "service.processor.request.ExecuteChannelOperationRequest");

    /** Owner 不依赖其他 Payment 模块，消费者不得绕过窄公开合同访问实现或持久化。 */
    @Test
    void limitsChannelAccessToPublishedTypes() throws IOException {
        var references = Pattern.compile("com\\.paytech\\.payment\\.([a-z]+)\\.([A-Za-z0-9_.*]+)");
        for (var module : modules()) {
            for (var file : sources(module)) {
                if (!file.toString().endsWith(".java")) {
                    continue;
                }
                var matches = references.matcher(Files.readString(file));
                while (matches.find()) {
                    String owner = matches.group(1);
                    if (module.getFileName().toString().equals("paytech-payment-channel")) {
                        assertEquals("channel", owner, file + " 引用了其他 Payment 模块");
                    } else if (owner.equals("channel")) {
                        String type = matches.group(2);
                        boolean bootstrapScan = file.equals(root().resolve(
                            "paytech-payment-web/src/main/java/com/paytech/payment/configuration/PaymentConfiguration.java"))
                            && type.equals("core.dal.mapper");
                        assertTrue(bootstrapScan || PUBLIC_TYPES.contains(type), file + " 引用了 Channel 非公开类型 " + type);
                    }
                }
            }
        }
    }

    /** Java/XML 中的五表 SQL 只能位于 Channel；测试夹具另有隔离库合同。 */
    @Test
    void keepsChannelTablesInsideOwnerModule() throws IOException {
        var tables = Pattern.compile("\\bt_(?:payment_channel|channel_account|channel_mid|mid_capability(?:_detail)?)\\b",
            Pattern.CASE_INSENSITIVE);
        for (var module : modules()) {
            if (!module.getFileName().toString().equals("paytech-payment-channel")) {
                for (var file : sources(module)) {
                    assertFalse(tables.matcher(Files.readString(file)).find(), file + " 越权访问 Channel 表");
                }
            }
        }
    }

    /** 核对实际 POM 依赖图，新增模块协作不能形成直接或间接环。 */
    @Test
    void paymentModuleDependenciesRemainAcyclic() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Map<String, Set<String>> graph = new HashMap<>();
        for (var module : modules()) {
            var document = factory.newDocumentBuilder().parse(module.resolve("pom.xml").toFile());
            var dependencies = document.getElementsByTagName("dependency");
            Set<String> targets = new HashSet<>();
            for (int i = 0; i < dependencies.getLength(); i++) {
                var dependency = (Element) dependencies.item(i);
                String artifact = dependency.getElementsByTagName("artifactId").item(0).getTextContent();
                if (artifact.startsWith("paytech-payment-")) {
                    targets.add(artifact);
                }
            }
            graph.put(module.getFileName().toString(), targets);
        }
        for (String module : graph.keySet()) {
            assertAcyclic(module, graph, new HashSet<>(), new HashSet<>());
        }
    }

    /** 当前路径向上寻找当前被测仓库，不依赖开发机绝对路径。 */
    private Path root() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("paytech-payment-channel/pom.xml"))) {
            current = current.getParent();
        }
        assertNotNull(current, "找不到 Payment 多模块仓库");
        return current;
    }

    /** 测试模块允许直接安排 Owner 数据，不属于生产依赖扫描。 */
    private List<Path> modules() throws IOException {
        try (var children = Files.list(root())) {
            return children.filter(path -> path.getFileName().toString().startsWith("paytech-payment-"))
                .filter(path -> !path.getFileName().toString().equals("paytech-payment-test"))
                .filter(path -> Files.isRegularFile(path.resolve("pom.xml"))).sorted().toList();
        }
    }

    /** 扫描生产 Java 和 Mapper XML，资源不存在的空模块不虚构代码。 */
    private List<Path> sources(Path module) throws IOException {
        var source = module.resolve("src/main");
        if (!Files.isDirectory(source)) {
            return List.of();
        }
        try (var files = Files.walk(source)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".xml")).toList();
        }
    }

    /** 深度优先检查当前调用链，完成集合防止共享依赖被误判为环。 */
    private void assertAcyclic(String module, Map<String, Set<String>> graph, Set<String> visiting, Set<String> done) {
        if (done.contains(module)) {
            return;
        }
        assertTrue(visiting.add(module), "Payment 模块依赖成环：" + module);
        for (String dependency : graph.getOrDefault(module, Set.of())) {
            assertAcyclic(dependency, graph, visiting, done);
        }
        visiting.remove(module);
        done.add(module);
    }
}
