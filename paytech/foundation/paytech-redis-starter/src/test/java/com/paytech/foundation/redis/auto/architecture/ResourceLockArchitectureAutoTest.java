package com.paytech.foundation.redis.auto.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

import com.paytech.foundation.redis.api.ResourceLock;

class ResourceLockArchitectureAutoTest {

    private static final String API_PACKAGE = "com.paytech.foundation.redis.api.";

    @Test
    void exposesOnlyTheApprovedResourceLockAnnotationContract() throws Exception {
        Class<?> resourceLock = Class.forName(API_PACKAGE + "ResourceLock");
        Class<?> resourceLocks = Class.forName(API_PACKAGE + "ResourceLocks");

        assertThat(resourceLock.isAnnotation()).isTrue();
        assertThat(resourceLocks.isAnnotation()).isTrue();
        assertThat(resourceLock.getDeclaredMethods()).extracting(Method::getName).containsExactlyInAnyOrder("prefix", "key");
        assertThat(resourceLock.getDeclaredMethod("prefix").getDefaultValue()).isNull();
        assertThat(resourceLock.getDeclaredMethod("key").getDefaultValue()).isEqualTo("");
        assertThat(resourceLocks.getDeclaredMethods()).extracting(Method::getName).containsExactly("value");
        assertThat(resourceLocks.getDeclaredMethod("value").getReturnType()).isEqualTo(resourceLock.arrayType());
        assertAnnotationMeta(resourceLock, resourceLocks);
        assertAnnotationMeta(resourceLocks, null);
    }

    @Test
    void keepsForbiddenFrameworksAndOperationsOutOfProductionSources() throws IOException {
        Path mainSources = moduleRoot().resolve("src/main/java");
        List<String> forbidden = List.of("com.paytech.platform", "audit", "repository", "mapper", "RKeys",
            "forceUnlock(", "flushdb", "flushall",
            "import org.springframework.data.redis.connection.RedisConnection;");

        try (var paths = Files.walk(mainSources)) {
            List<Path> offenders = paths.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> containsAny(path, forbidden)).sorted(Comparator.naturalOrder()).toList();
            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void doesNotDependOnTestcontainers() throws IOException {
        Path foundationRoot = moduleRoot().getParent();
        String testcontainers = "org.test" + "containers";
        List<String> forbidden = List.of(testcontainers, "com.github.docker-java", "it.ozimov:embedded-redis",
            "Generic" + "Container", "Docker" + "ImageName");
        Path thisSource = moduleRoot().resolve("src/test/java/com/paytech/foundation/redis/auto/architecture/"
            + "ResourceLockArchitectureAutoTest.java");

        try (var paths = Files.walk(foundationRoot)) {
            List<Path> offenders = paths.filter(path -> path.getFileName().toString().equals("pom.xml")
                || path.toString().endsWith(".java")).filter(path -> !path.equals(thisSource))
                .filter(path -> containsAny(path, forbidden)).sorted().toList();
            assertThat(offenders).isEmpty();
        }
    }

    @Test
    void consumerArchitectureGateRejectsSameClassCallsThatBypassTheProxy() {
        assertThatThrownBy(() -> assertNoResourceLockSelfInvocation(SelfInvokingFixture.class))
            .isInstanceOf(AssertionError.class).hasMessageContaining("bypass").hasMessageContaining("locked");
    }

    private void assertNoResourceLockSelfInvocation(Class<?> type) throws IOException {
        Set<String> lockedMethods = java.util.Arrays.stream(type.getDeclaredMethods())
            .filter(method -> method.getDeclaredAnnotationsByType(ResourceLock.class).length > 0)
            .map(method -> method.getName() + Type.getMethodDescriptor(method)).collect(Collectors.toSet());
        String internalName = Type.getInternalName(type);
        String resourceName = "/" + internalName + ".class";
        try (InputStream bytecode = type.getResourceAsStream(resourceName)) {
            if (bytecode == null) {
                throw new AssertionError("无法读取架构测试classfile: " + type.getName());
            }
            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String callerName, String callerDescriptor,
                    String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String calleeName,
                            String calleeDescriptor, boolean isInterface) {
                            String callee = calleeName + calleeDescriptor;
                            if (owner.equals(internalName) && lockedMethods.contains(callee)
                                && !(callerName + callerDescriptor).equals(callee)) {
                                throw new AssertionError("ResourceLock代理绕过: " + type.getName() + "."
                                    + callerName + " -> " + calleeName);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private void assertAnnotationMeta(Class<?> annotationType, Class<?> repeatableContainer) {
        assertThat(annotationType.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(annotationType.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotationType.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD);
        if (repeatableContainer == null) {
            assertThat(annotationType.isAnnotationPresent(Repeatable.class)).isFalse();
        } else {
            assertThat(annotationType.getAnnotation(Repeatable.class).value()).isEqualTo(repeatableContainer);
        }
    }

    private boolean containsAny(Path path, List<String> forbidden) {
        try {
            String source = Files.readString(path);
            return forbidden.stream().anyMatch(source::contains);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path moduleRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    static class SelfInvokingFixture {
        public void bypass() {
            this.locked();
        }

        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }
}
