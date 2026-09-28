package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.delivery.domain.NotificationInput;

/** 普通参数和纯文本插值规则，不需要数据库或 Spring。 */
class NotificationInputTest {

    /** 参数按键覆盖，并且不会执行第二次插值。 */
    @Test void overridesAreLiteral() {
        var merged = NotificationInput.merge("{\"name\":\"Default\",\"notice\":\"Maintenance\"}", "{\"name\":\"${notice}\"}");
        var message = NotificationInput.render("Hello ${name}", "${notice}", merged);
        assertEquals("Hello ${notice}", message.getSubject());
        assertEquals("Maintenance", message.getBody());
    }

    /** 缺参数、危险标题或非法表达式均明确拒绝。 */
    @Test void malformedOrMissingPlaceholdersFail() {
        assertThrows(FailException.class, () -> NotificationInput.render("${missing}", "Body", Map.of()));
        assertThrows(FailException.class, () -> NotificationInput.render("${name}", "Body", Map.of("name", "A\r\nB")));
        assertThrows(FailException.class, () -> NotificationInput.render("${a.b}", "Body", Map.of()));
    }

    /** 认证秘密字段不能借普通参数落库。 */
    @Test void rejectsAuthenticationParameters() {
        assertThrows(FailException.class, () -> NotificationInput.parameters(Map.of("resetToken", "synthetic")));
        assertThrows(FailException.class, () -> NotificationInput.parameters(Map.of("name", "x".repeat(2049))));
    }

    /** 邮箱比较统一大小写，不擅自合并加号别名。 */
    @Test void canonicalizationPreservesMailboxAliases() {
        assertEquals("alice+one@example.com", NotificationInput.canonicalEmail("Alice+one@EXAMPLE.COM"));
        assertEquals("zh-CN", NotificationInput.locale("zh-cn"));
        assertThrows(FailException.class, () -> NotificationInput.locale("not_a_locale"));
    }
}
