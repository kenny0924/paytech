package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.delivery.domain.NotificationInput;

/**
 * 普通参数和纯文本插值规则，不需要数据库或 Spring。
 *
 * @see NotificationInput#canonicalEmail(String)
 * @see NotificationInput#locale(String)
 * @see NotificationInput#parameters(Map)
 * @see NotificationInput#merge(String, String)
 * @see NotificationInput#render(String, String, Map)
 */
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

    /** 地址长度、local-part 长度、点号位置与缺少域名均拒绝。 */
    @ParameterizedTest
    @MethodSource("invalidEmails")
    void test_invalidEmailRejected(String email) {
        assertThrows(InvalidParameterException.class, () -> NotificationInput.canonicalEmail(email));
    }

    /** 超长地址、超长 local-part 及点号边界。 */
    static Stream<String> invalidEmails() {
        return Stream.of("a".repeat(65) + "@example.com", "a@" + "b".repeat(63) + "." + "c".repeat(63) + "."
            + "d".repeat(63) + "." + "e".repeat(63) + ".com", ".alice@example.com", "al..ice@example.com",
            "alice.@example.com", "alice", "alice@example");
    }

    /** und 与超过 35 字符的语言标签拒绝，不隐式回退。 */
    @ParameterizedTest
    @ValueSource(strings = {"und", "en-x-aaaaaaaa-bbbbbbbb-cccccccc-dddddddd"})
    void test_unsupportedLocaleRejected(String locale) {
        assertThrows(InvalidParameterException.class, () -> NotificationInput.locale(locale));
    }

    /** 空参数不持久化；合并时缺省或覆盖为空都按一层 Map 处理。 */
    @Test
    void test_emptyParametersAndMerge() {
        assertNull(NotificationInput.parameters(null));
        assertNull(NotificationInput.parameters(Map.of()));
        assertTrue(NotificationInput.merge(null, null).isEmpty());
        assertEquals(Map.of("name", "A"), NotificationInput.merge("{\"name\":\"A\"}", null));
        assertEquals(Map.of("name", "B"), NotificationInput.merge(null, "{\"name\":\"B\"}"));
    }

    /** 键数量、键格式、null 值和 16 KiB 总量上限都拒绝。 */
    @ParameterizedTest
    @MethodSource("invalidParameters")
    void test_invalidParametersRejected(Map<String, String> values) {
        assertThrows(InvalidParameterException.class, () -> NotificationInput.parameters(values));
    }

    /** 超过 64 个键、以数字开头的键、null 值及超出总量。 */
    static Stream<Map<String, String>> invalidParameters() {
        var tooMany = new HashMap<String, String>();
        IntStream.range(0, 65).forEach(i -> tooMany.put("k" + i, "v"));
        var nullValue = new HashMap<String, String>();
        nullValue.put("name", null);
        var tooLarge = new HashMap<String, String>();
        IntStream.range(0, 9).forEach(i -> tooLarge.put("k" + i, "x".repeat(2048)));
        return Stream.of(tooMany, Map.of("1name", "v"), nullValue, tooLarge);
    }

    /** 空标题/正文、超长或带换行的标题、超长正文与模板，以及未闭合占位符均拒绝。 */
    @ParameterizedTest
    @MethodSource("invalidTemplates")
    void test_invalidTemplateRejected(String subject, String body, Map<String, String> parameters) {
        assertThrows(BizCheckFailException.class, () -> NotificationInput.render(subject, body, parameters));
    }

    /** 每组只破坏一个约束。 */
    static Stream<Arguments> invalidTemplates() {
        var none = Map.<String, String>of();
        return Stream.of(Arguments.of(null, "Body", none), Arguments.of(" ", "Body", none),
            Arguments.of("Subject", null, none), Arguments.of("Subject", " ", none),
            Arguments.of("x".repeat(256), "Body", none), Arguments.of("${name}", "Body", Map.of("name", "A\nB")),
            Arguments.of("Subject", "${name}", Map.of("name", "x".repeat(65536))),
            Arguments.of("Subject", "x".repeat(65536), none), Arguments.of("${a.b} ${name}", "Body", Map.of("name", "A")));
    }
}
