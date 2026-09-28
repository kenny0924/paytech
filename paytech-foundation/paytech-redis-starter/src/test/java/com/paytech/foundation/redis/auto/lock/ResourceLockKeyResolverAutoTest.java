package com.paytech.foundation.redis.auto.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.lock.ResourceLockKeyResolver;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataCompiler;
import com.paytech.foundation.redis.core.lock.ResolvedResourceLock;

class ResourceLockKeyResolverAutoTest {

    private final ResourceLockMetadataCompiler compiler = new ResourceLockMetadataCompiler();
    private final ResourceLockKeyResolver resolver = new ResourceLockKeyResolver();

    @Test
    void canonicalizesFixedStringIntegralAndUuidKeys() throws Exception {
        assertKeys("fixed", new Object[0], "gateway-configuration:catalog");
        assertKeys("stringValue", new Object[] {"account_1:A-b.c"}, "operations:account:account_1:A-b.c");
        assertKeys("byteValue", new Object[] {(byte)-2}, "operations:value:-2");
        assertKeys("shortValue", new Object[] {(short)3}, "operations:value:3");
        assertKeys("intValue", new Object[] {40}, "operations:value:40");
        assertKeys("longValue", new Object[] {500L}, "operations:value:500");
        assertKeys("uuidValue", new Object[] {UUID.fromString("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")},
            "operations:value:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    void resolvesRecordAndBeanProperties() throws Exception {
        assertKeys("recordValue", new Object[] {new ResourceCommand("record-1")}, "operations:value:record-1");
        assertKeys("beanValue", new Object[] {new BeanCommand("bean-1")}, "operations:value:bean-1");
    }

    @Test
    void resolvesPackagePrivateRecordPropertyAfterStartupValidation() throws Exception {
        assertKeys("hiddenRecordValue", new Object[] {new HiddenCommand("record-2")},
            "operations:value:record-2");
    }

    @Test
    void validatesEveryValueBeforeReturningAnyLock() throws Exception {
        assertThatThrownBy(() -> resolve("stringValue", new Object[] {""}))
            .isInstanceOf(InvalidParameterException.class);
        for (String invalid : List.of("-leading", " leading", "non ascii", "é", "a/b", "a".repeat(257))) {
            assertThatThrownBy(() -> resolve("stringValue", new Object[] {invalid}))
                .isInstanceOf(InvalidParameterException.class).hasMessageNotContaining(invalid);
        }
        assertThatThrownBy(() -> resolve("stringValue", new Object[] {null}))
            .isInstanceOf(InvalidParameterException.class).hasMessageNotContaining("null");
        assertThatThrownBy(() -> resolve("twoValues", new Object[] {"valid", "secret invalid"}))
            .isInstanceOf(InvalidParameterException.class).hasMessageNotContaining("secret invalid");
    }

    @Test
    void convertsAccessorFailureToSafeInvalidParameterCause() throws Exception {
        IllegalStateException cause = new IllegalStateException("secret-value");
        assertThatThrownBy(() -> resolve("throwingBean", new Object[] {new ThrowingCommand(cause)}))
            .isInstanceOf(InvalidParameterException.class).hasCause(cause).hasMessageNotContaining("secret-value");
    }

    @Test
    void sortsByFullAsciiLogicalKeyAndDeduplicatesRuntimeAliases() throws Exception {
        List<ResolvedResourceLock> locks = resolve("ordered", new Object[] {"same", "same", "z", "a"});

        assertThat(locks).extracting(ResolvedResourceLock::logicalKey).containsExactly("a-prefix:same",
            "m-prefix:a", "m-prefix:z");
        assertThat(locks.getFirst().toString()).doesNotContain("same");
    }

    private void assertKeys(String methodName, Object[] arguments, String... expected) throws Exception {
        assertThat(resolve(methodName, arguments)).extracting(ResolvedResourceLock::logicalKey).containsExactly(expected);
    }

    private List<ResolvedResourceLock> resolve(String methodName, Object[] arguments) throws Exception {
        Method method = java.util.Arrays.stream(Fixtures.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        return resolver.resolve(compiler.compile(Fixtures.class, method), arguments);
    }

    public record ResourceCommand(String id) {}

    record HiddenCommand(String id) {}

    public static final class BeanCommand {
        private final String id;

        BeanCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static final class ThrowingCommand {
        private final RuntimeException failure;

        ThrowingCommand(RuntimeException failure) {
            this.failure = failure;
        }

        public String getId() {
            throw failure;
        }
    }

    static class Fixtures {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void fixed() {}

        @ResourceLock(prefix = "operations:account", key = "#value")
        public void stringValue(String value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void byteValue(byte value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void shortValue(short value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void intValue(int value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void longValue(long value) {}

        @ResourceLock(prefix = "operations:value", key = "#value")
        public void uuidValue(UUID value) {}

        @ResourceLock(prefix = "operations:value", key = "#command.id")
        public void recordValue(ResourceCommand command) {}

        @ResourceLock(prefix = "operations:value", key = "#command.id")
        public void beanValue(BeanCommand command) {}

        @ResourceLock(prefix = "operations:value", key = "#command.id")
        public void hiddenRecordValue(HiddenCommand command) {}

        @ResourceLock(prefix = "operations:value", key = "#command.id")
        public void throwingBean(ThrowingCommand command) {}

        @ResourceLock(prefix = "a-prefix", key = "#first")
        @ResourceLock(prefix = "a-prefix", key = "#second")
        @ResourceLock(prefix = "m-prefix", key = "#third")
        @ResourceLock(prefix = "m-prefix", key = "#fourth")
        public void ordered(String first, String second, String third, String fourth) {}

        @ResourceLock(prefix = "a-prefix", key = "#first")
        @ResourceLock(prefix = "z-prefix", key = "#second")
        public void twoValues(String first, String second) {}
    }
}
