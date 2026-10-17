package com.paytech.foundation.redis.auto.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.api.ResourceLocks;

class ResourceLockAnnotationContractAutoTest {

    @Test
    void exposesTheMinimalRepeatableMethodContract() throws Exception {
        assertThat(ResourceLock.class.getDeclaredMethods()).extracting(Method::getName)
            .containsExactlyInAnyOrder("prefix", "key");
        assertThat(ResourceLock.class.getDeclaredMethod("prefix").getDefaultValue()).isNull();
        assertThat(ResourceLock.class.getDeclaredMethod("key").getDefaultValue()).isEqualTo("");
        assertThat(ResourceLocks.class.getDeclaredMethods()).extracting(Method::getName).containsExactly("value");
        assertThat(ResourceLocks.class.getDeclaredMethod("value").getReturnType()).isEqualTo(ResourceLock[].class);

        assertMeta(ResourceLock.class, ResourceLocks.class);
        assertMeta(ResourceLocks.class, null);
    }

    private void assertMeta(Class<?> type, Class<?> repeatableContainer) {
        assertThat(type.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(type.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(type.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD);
        if (repeatableContainer == null) {
            assertThat(type.isAnnotationPresent(Repeatable.class)).isFalse();
        } else {
            assertThat(type.getAnnotation(Repeatable.class).value()).isEqualTo(repeatableContainer);
        }
    }
}
