package com.paytech.arch.rule;

import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.Source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * R12：补偿事件 Handler 只能注册在 {@code -core}，且 {@code eventType} 必须在本服务的事件目录文件中登记（design/08）。
 *
 * <p>
 * 事件目录为服务目录下的 {@value #CATALOG_FILE}，格式 {@code eventTypes: [..]}。{@code eventType()} 从类文件字节码读取（JDK
 * ClassFile API），因此必须返回字符串字面量或编译期常量；返回枚举 {@code getCode()} 等运行期值时报告为无法核对。
 * {@code -test} 模块中的测试 Handler 不在检查范围内。
 *
 * @author Claude
 */
public final class CompensationHandlerRule implements ArchitectureRule {

    /**
     * Foundation 补偿 Handler 接口。
     */
    public static final String DEFAULT_HANDLER_INTERFACE = "com.paytech.foundation.compensation.api.CompensationEventHandler";

    /**
     * 服务目录下的事件目录文件名。
     */
    public static final String CATALOG_FILE = "compensation-event-catalog.yaml";

    private final String handlerInterface;

    /**
     * 使用 Foundation 补偿 Handler 接口。
     */
    public CompensationHandlerRule() {
        this(DEFAULT_HANDLER_INTERFACE);
    }

    /**
     * 使用指定的 Handler 接口全名。
     *
     * @param handlerInterface Handler 接口全名
     */
    public CompensationHandlerRule(String handlerInterface) {
        this.handlerInterface = handlerInterface;
    }

    @Override
    public String id() {
        return "R12";
    }

    @Override
    public String description() {
        return "补偿 Handler 只能在 -core，eventType 必须在服务事件目录中登记";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<JavaClass> handlers = new ArrayList<>();
        for (JavaClass javaClass : scope.classes()) {
            Optional<ModuleIdentity> owner = scope.ownership().ownerOf(javaClass);
            boolean target = owner.filter(identity -> scope.serviceName().equals(identity.service())
                && identity.role() != ModuleRole.TEST).isPresent();
            if (target && isConcreteHandler(javaClass)) {
                handlers.add(javaClass);
            }
        }
        if (handlers.isEmpty()) {
            return List.of();
        }
        Path catalogFile = scope.serviceDirectory().resolve(CATALOG_FILE);
        List<RuleViolation> violations = new ArrayList<>();
        Set<String> catalog = readCatalog(catalogFile, violations);
        for (JavaClass handler : handlers) {
            ModuleRole role = scope.ownership().ownerOf(handler).map(ModuleIdentity::role).orElseThrow();
            if (role != ModuleRole.CORE) {
                violations.add(new RuleViolation(id(), handler.getName(),
                    "补偿 Handler 只能注册在 -core，当前位于 " + role + " 模块"));
            }
            violations.addAll(checkEventType(handler, catalog, catalogFile));
        }
        return violations;
    }

    private boolean isConcreteHandler(JavaClass javaClass) {
        return !javaClass.isInterface() && !javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
            && javaClass.getAllRawInterfaces().stream().anyMatch(contract -> contract.getName().equals(handlerInterface));
    }

    private List<RuleViolation> checkEventType(JavaClass handler, Set<String> catalog, Path catalogFile) {
        Optional<String> eventType = constantEventType(handler);
        if (eventType.isEmpty()) {
            return List.of(new RuleViolation(id(), handler.getName(),
                "eventType() 必须返回字符串字面量或编译期常量，才能与事件目录核对"));
        }
        if (catalog.contains(eventType.get())) {
            return List.of();
        }
        return List.of(new RuleViolation(id(), handler.getName(),
            "eventType " + eventType.get() + " 未在事件目录 " + catalogFile + " 中登记"));
    }

    private Set<String> readCatalog(Path catalogFile, List<RuleViolation> violations) {
        if (!Files.isRegularFile(catalogFile)) {
            violations.add(new RuleViolation(id(), catalogFile.toString(), "服务存在补偿 Handler，但缺少事件目录文件"));
            return Set.of();
        }
        try {
            JsonNode root = YAMLMapper.builder().build().readTree(Files.readString(catalogFile));
            Set<String> eventTypes = new LinkedHashSet<>();
            root.path("eventTypes").values().forEach(node -> eventTypes.add(node.asString()));
            return eventTypes;
        } catch (IOException exception) {
            throw new UncheckedIOException("事件目录读取失败: " + catalogFile, exception);
        } catch (JacksonException exception) {
            violations.add(new RuleViolation(id(), catalogFile.toString(), "事件目录不是合法 YAML: "
                + exception.getOriginalMessage()));
            return Set.of();
        }
    }

    private static Optional<String> constantEventType(JavaClass handler) {
        for (JavaClass current = handler; current != null; current = current.getRawSuperclass().orElse(null)) {
            Optional<MethodModel> method = eventTypeMethod(current);
            if (method.isPresent()) {
                return method.get().code().flatMap(CompensationHandlerRule::onlyStringConstant);
            }
        }
        return Optional.empty();
    }

    private static Optional<MethodModel> eventTypeMethod(JavaClass javaClass) {
        Optional<Source> source = javaClass.getSource();
        if (source.isEmpty()) {
            return Optional.empty();
        }
        ClassModel model = ClassFile.of().parse(readBytes(source.get()));
        return model.methods().stream()
            .filter(method -> method.methodName().equalsString("eventType")
                && method.methodType().equalsString("()Ljava/lang/String;")
                && (method.flags().flagsMask() & ClassFile.ACC_BRIDGE) == 0)
            .findFirst();
    }

    private static Optional<String> onlyStringConstant(Iterable<CodeElement> code) {
        List<String> constants = new ArrayList<>();
        int invocations = 0;
        for (CodeElement element : code) {
            if (element instanceof ConstantInstruction constant && constant.constantValue() instanceof String value) {
                constants.add(value);
            } else if (element instanceof java.lang.classfile.instruction.InvokeInstruction
                || element instanceof java.lang.classfile.instruction.FieldInstruction) {
                invocations++;
            }
        }
        return constants.size() == 1 && invocations == 0 ? Optional.of(constants.getFirst()) : Optional.empty();
    }

    private static byte[] readBytes(Source source) {
        try (InputStream input = source.getUri().toURL().openStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("类文件读取失败: " + source.getUri(), exception);
        }
    }
}
