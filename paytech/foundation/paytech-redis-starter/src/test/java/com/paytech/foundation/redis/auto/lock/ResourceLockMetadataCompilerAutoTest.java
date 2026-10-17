package com.paytech.foundation.redis.auto.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataCompiler;
import com.paytech.foundation.redis.core.lock.ResourceLockMethodMetadata;
import com.paytech.foundation.redis.test.fixture.NoParameterMetadataCompiler;

class ResourceLockMetadataCompilerAutoTest {

    private final ResourceLockMetadataCompiler compiler = new ResourceLockMetadataCompiler();

    @Test
    void compilesFixedParameterRecordAndBeanGetterBindings() throws Exception {
        assertThat(compile("fixed").bindings().getFirst().expression()).isEmpty();
        assertThat(compile("scalar", String.class).bindings().getFirst().valueType()).isEqualTo(String.class);
        assertThat(compile("record", RefundCommand.class).bindings().getFirst().valueType()).isEqualTo(UUID.class);
        assertThat(compile("recordBeanGetter", CustomGetterRecord.class).bindings().getFirst().valueType())
            .isEqualTo(String.class);
        assertThat(compile("bean", RoleCommand.class).bindings().getFirst().valueType()).isEqualTo(long.class);
    }

    @Test
    void rejectsEveryForbiddenExpressionShape() throws Exception {
        List<String> expressions = List.of(" ", "#root", "#this", "@bean", "T(Type)", "new Thing()",
            "#value.trim()", "#value[0]", "#value.field.more", "#value?.field", "#value ?: 'x'",
            "#value + 'x'", "#value ? 'a' : 'b'", "#value.![x]", "#value.?[x]");

        for (String expression : expressions) {
            Method method = NoParameterMetadataCompiler.compileWithParameters(expression, "java.lang.String");
            assertThatThrownBy(() -> compiler.compile(method.getDeclaringClass(), method)).isInstanceOf(FailException.class)
                .hasMessageContaining("annotation[1]").hasMessageContaining("表达式");
        }
    }

    @Test
    void rejectsInvalidPrefixDuplicateBindingAndUnsupportedStaticTypes() throws Exception {
        assertInvalid("badPrefix", "prefix");
        assertInvalid("longPrefix", "prefix");
        assertInvalid("duplicate", "重复");
        assertInvalid("booleanValue", "类型");
        assertInvalid("doubleValue", "类型");
        assertInvalid("enumValue", "类型");
        assertInvalid("listValue", "类型");
        assertInvalid("privateFieldOnly", "属性");
        assertInvalid("unknownParameter", "参数");
    }

    @Test
    void acceptsOnlyTheApprovedScalarTypes() throws Exception {
        for (String methodName : List.of("byteValue", "byteObject", "shortValue", "shortObject", "intValue",
            "intObject", "longValue", "longObject", "uuidValue")) {
            assertThat(compileByName(methodName).bindings()).hasSize(1);
        }
    }

    @Test
    void rejectsClassesCompiledWithoutParameterMetadata() throws Exception {
        Method method = NoParameterMetadataCompiler.compileWithoutParameters("#accountId", "java.lang.String");
        assertThat(method.getParameters()[0].isNamePresent()).isFalse();

        assertThatThrownBy(() -> compiler.compile(method.getDeclaringClass(), method)).isInstanceOf(FailException.class)
            .hasMessageContaining("参数名元数据").hasMessageContaining("annotation[1]");
    }

    @Test
    void rejectsNonPublicStaticFinalAndDelayedMethods() throws Exception {
        assertInvalid("packagePrivate", "public");
        assertInvalid("staticMethod", "static");
        assertInvalid("finalMethod", "final");
        for (String method : List.of("delayed", "supplier", "callable", "runnable", "iterable", "iterator",
            "spliterator", "enumeration")) {
            assertInvalid(method, "同步");
        }
    }

    private ResourceLockMethodMetadata compile(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = Fixtures.class.getDeclaredMethod(methodName, parameterTypes);
        return compiler.compile(Fixtures.class, method);
    }

    private void assertInvalid(String methodName, String message) throws Exception {
        Method method = methodByName(methodName);
        assertThatThrownBy(() -> compiler.compile(Fixtures.class, method)).isInstanceOf(FailException.class)
            .hasMessageContaining(message).hasMessageContaining("annotation[");
    }

    private ResourceLockMethodMetadata compileByName(String methodName) {
        return compiler.compile(Fixtures.class, methodByName(methodName));
    }

    private Method methodByName(String methodName) {
        return java.util.Arrays.stream(Fixtures.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
    }

    enum UnsupportedCode { VALUE }

    record RefundCommand(UUID refundId) {}

    record CustomGetterRecord(String raw) {
        public String getResourceId() {
            return raw;
        }
    }

    static final class RoleCommand {
        private final long roleId = 12L;

        public long getRoleId() {
            return roleId;
        }
    }

    static class PrivateOnlyCommand {
        @SuppressWarnings("unused")
        private final String id = "hidden";
    }

    static class Fixtures {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void fixed() {}

        @ResourceLock(prefix = "operations:account", key = "#accountId")
        public void scalar(String accountId) {}

        @ResourceLock(prefix = "payment:refund", key = "#command.refundId")
        public void record(RefundCommand command) {}

        @ResourceLock(prefix = "payment:refund", key = "#command.resourceId")
        public void recordBeanGetter(CustomGetterRecord command) {}

        @ResourceLock(prefix = "operations:role", key = "#command.roleId")
        public void bean(RoleCommand command) {}

        @ResourceLock(prefix = "Operations:Account", key = "#value")
        public void badPrefix(String value) {}

        @ResourceLock(prefix = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", key = "#value")
        public void longPrefix(String value) {}

        @ResourceLock(prefix = "operations:account", key = "#value")
        @ResourceLock(prefix = "operations:account", key = "#value")
        public void duplicate(String value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void booleanValue(boolean value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void doubleValue(double value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void enumValue(UnsupportedCode value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void listValue(List<String> value) {}

        @ResourceLock(prefix = "operations:value", key = "#command.id")
        public void privateFieldOnly(PrivateOnlyCommand command) {}

        @ResourceLock(prefix = "operations:value", key = "#missing")
        public void unknownParameter(String value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void byteValue(byte value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void byteObject(Byte value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void shortValue(short value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void shortObject(Short value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void intValue(int value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void intObject(Integer value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void longValue(long value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void longObject(Long value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void uuidValue(UUID value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        void packagePrivate(String value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public static void staticMethod(String value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public final void finalMethod(String value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public CompletionStage<String> delayed(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Supplier<String> supplier(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Callable<String> callable(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Runnable runnable(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Iterable<String> iterable(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Iterator<String> iterator(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Spliterator<String> spliterator(String value) {
            return null;
        }

        @ResourceLock(prefix = "operations:value", key = "#value")
        public Enumeration<String> enumeration(String value) {
            return null;
        }
    }
}
