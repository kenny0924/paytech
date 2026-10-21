package com.paytech.channel.connector.auto.architecture;

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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.RuleViolation;

/**
 * channel-connector 公开合同与渠道表所有权的源码门禁（原 Payment ChannelModuleBoundaryTest，09 §2.9）。
 * <li>锚点为本服务聚合 POM（{@code Path.of("").resolve("..")}），Payment 模块按同级目录 {@code ../payment/paytech-payment-*} 列举。</li>
 * <li>渠道表归属由 arch-rules R4（table-ownership.yaml）判定，本类同时执行 R4 并保留对 Payment 源码的文本扫描。</li>
 * <li>2026-10-20 统一对外渠道出口第一段：新增 protocol 模块（依赖方向 core → protocol），core 只能使用 protocol 公开的
 * Processor、Service 接口、领域对象与枚举，不访问其实现、Repository、Mapper 与 DataObject。</li>
 */
class ChannelConnectorArchitectureTest {
    private static final List<String> OWN_MODULES = List.of("paytech-channel-connector-api",
        "paytech-channel-connector-protocol", "paytech-channel-connector-core", "paytech-channel-connector-app");

    /** protocol 模块基础包。 */
    private static final String PROTOCOL = "com.paytech.channel.connector.protocol";

    private static final List<String> PAYMENT_MODULES = List.of("paytech-payment-api", "paytech-payment-core",
        "paytech-payment-app");

    /** Payment 只能经 channel-connector-api 访问渠道能力；channel-connector 不依赖 Payment；api 不引用本服务实现。 */
    @Test
    void limitsChannelAccessToPublishedTypes() throws IOException {
        var channelReferences = Pattern.compile("com\\.paytech\\.channel\\.connector\\.([a-z]+)\\b");
        var paymentReferences = Pattern.compile("com\\.paytech\\.payment\\.");
        for (var module : modules(paymentRoot(), PAYMENT_MODULES)) {
            for (var file : sources(module)) {
                var matches = channelReferences.matcher(Files.readString(file));
                while (matches.find()) {
                    assertEquals("api", matches.group(1), file + " 引用了 channel-connector 非公开类型");
                }
            }
        }
        for (var module : modules(root(), OWN_MODULES)) {
            for (var file : sources(module)) {
                String text = Files.readString(file);
                assertFalse(paymentReferences.matcher(text).find(), file + " 引用了 Payment");
                if (module.getFileName().toString().equals("paytech-channel-connector-api")) {
                    var matches = channelReferences.matcher(text);
                    while (matches.find()) {
                        assertEquals("api", matches.group(1), file + " 合同引用了本服务实现");
                    }
                }
            }
        }
    }

    /**
     * 渠道 12 表、渠道成本 4 表、变更记录表与渠道协议 10 表只属于 channel-connector：R4 对两个服务零违规，Payment 源码与 Mapper
     * 不出现这些表名。
     */
    @Test
    void keepsChannelTablesInsideOwnerModule() throws IOException {
        var tables = Pattern.compile("\\bt_(?:payment_channel|channel_account|channel_mid|mid_capability(?:_detail)?"
            + "|routing_policy(?:_version|_mid)?|channel_interaction|channel_callback(?:_item)?|channel_event_exception"
            + "|channel_cost_(?:plan(?:_ver)?|scope|component)|channel_config_change"
            + "|standard_api|api_field|integration|integration_api|integration_script|integration_standard_api"
            + "|integration_verify_(?:account|case|run|run_step))\\b",
            Pattern.CASE_INSENSITIVE);
        for (var module : modules(paymentRoot(), PAYMENT_MODULES)) {
            for (var file : sources(module)) {
                assertFalse(tables.matcher(Files.readString(file)).find(), file + " 越权访问渠道表");
            }
        }
        assertEquals(List.of(), r4("channel-connector", modules(root(), OWN_MODULES)));
        assertEquals(List.of(), r4("payment", modules(paymentRoot(), PAYMENT_MODULES)));
    }

    /**
     * core 只经 protocol 公开的 Processor、Service 接口、领域对象与枚举协作：不依赖 protocol 的实现类（{@code *Impl} 或 impl 包）、
     * Repository、Mapper 与 DataObject；以 core 编译产物导入，依赖方向 core → protocol 由 POM 依赖图另行核对。
     */
    @Test
    void coreUsesOnlyPublishedProtocolTypes() throws IOException {
        Path classes = root().resolve("paytech-channel-connector-core/target/classes");
        assertTrue(Files.isDirectory(classes), "core 尚未编译，规则无类可检: 先在 paytech/ 执行 ./mvnw test-compile");
        JavaClasses core = new ClassFileImporter().importPath(classes);
        DescribedPredicate<JavaClass> internal = JavaClass.Predicates.resideInAnyPackage(PROTOCOL + "..impl..",
            PROTOCOL + ".domainservice.repository..", PROTOCOL + ".core.dal.mapper..",
            PROTOCOL + ".core.dal.dataobject..")
            .or(JavaClass.Predicates.resideInAPackage(PROTOCOL + "..")
                .and(JavaClass.Predicates.simpleNameEndingWith("Impl")));
        ArchRule rule = ArchRuleDefinition.noClasses().should().dependOnClassesThat(internal)
            .because("core 只能使用 protocol 公开的 Processor、Service 接口、领域对象与枚举");

        rule.check(core);
    }

    /** 核对实际 POM 依赖图：两服务模块之间无环，channel-connector 不依赖任何 Payment 模块，protocol 不依赖 core 与 app。 */
    @Test
    void paymentModuleDependenciesRemainAcyclic() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Map<String, Set<String>> graph = new HashMap<>();
        var all = new java.util.ArrayList<>(modules(root(), OWN_MODULES));
        all.addAll(modules(paymentRoot(), PAYMENT_MODULES));
        for (var module : all) {
            var document = factory.newDocumentBuilder().parse(module.resolve("pom.xml").toFile());
            var dependencies = document.getElementsByTagName("dependency");
            Set<String> targets = new HashSet<>();
            for (int i = 0; i < dependencies.getLength(); i++) {
                var dependency = (Element)dependencies.item(i);
                String artifact = dependency.getElementsByTagName("artifactId").item(0).getTextContent();
                if (artifact.startsWith("paytech-payment-") || artifact.startsWith("paytech-channel-connector-")) {
                    targets.add(artifact);
                }
            }
            graph.put(module.getFileName().toString(), targets);
        }
        for (String module : OWN_MODULES) {
            assertTrue(graph.get(module).stream().noneMatch(target -> target.startsWith("paytech-payment-")),
                module + " 依赖了 Payment 模块");
        }
        assertTrue(graph.get("paytech-channel-connector-core").contains("paytech-channel-connector-protocol"),
            "core 应依赖 protocol");
        assertTrue(graph.get("paytech-channel-connector-protocol").stream()
            .noneMatch(target -> target.equals("paytech-channel-connector-core")
                || target.equals("paytech-channel-connector-app")), "protocol 不得依赖 core 或 app");
        for (String module : graph.keySet()) {
            assertAcyclic(module, graph, new HashSet<>(), new HashSet<>());
        }
    }

    /** 本服务聚合 POM 所在目录：测试从本模块目录运行，不依赖开发机绝对路径。 */
    private Path root() throws IOException {
        Path root = Path.of("").resolve("..").toAbsolutePath().normalize();
        assertTrue(Files.readString(root.resolve("pom.xml")).contains("<artifactId>paytech-channel-connector</artifactId>"),
            "锚点不是 channel-connector 聚合 POM：" + root);
        return root;
    }

    /** 同级 Payment 服务目录。 */
    private Path paymentRoot() throws IOException {
        Path root = root().resolveSibling("payment");
        assertTrue(Files.isRegularFile(root.resolve("pom.xml")), "找不到 Payment 服务目录：" + root);
        return root;
    }

    /** 按名称列举模块，缺失即失败，避免改名后静默少扫。 */
    private static List<Path> modules(Path root, List<String> names) {
        return names.stream().map(root::resolve).peek(module -> assertTrue(Files.isRegularFile(module.resolve("pom.xml")),
            "模块不存在：" + module)).toList();
    }

    /** 扫描生产 Java 和 Mapper XML，资源不存在的空模块不虚构代码。 */
    private static List<Path> sources(Path module) throws IOException {
        var source = module.resolve("src/main");
        if (!Files.isDirectory(source)) {
            return List.of();
        }
        try (var files = Files.walk(source)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".xml")).toList();
        }
    }

    /** 只取 R4 的违规。 */
    private static List<RuleViolation> r4(String service, List<Path> moduleRoots) {
        return ArchitectureRules.checkService(service, moduleRoots).stream()
            .filter(violation -> "R4".equals(violation.ruleId())).toList();
    }

    /** 深度优先检查当前调用链，完成集合防止共享依赖被误判为环。 */
    private void assertAcyclic(String module, Map<String, Set<String>> graph, Set<String> visiting, Set<String> done) {
        if (done.contains(module)) {
            return;
        }
        assertTrue(visiting.add(module), "模块依赖成环：" + module);
        for (String dependency : graph.getOrDefault(module, Set.of())) {
            assertAcyclic(dependency, graph, visiting, done);
        }
        visiting.remove(module);
        done.add(module);
    }
}
