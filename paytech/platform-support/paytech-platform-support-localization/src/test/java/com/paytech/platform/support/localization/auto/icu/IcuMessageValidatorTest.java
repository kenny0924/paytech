package com.paytech.platform.support.localization.auto.icu;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.paytech.platform.support.localization.domain.icu.IcuMessageValidator;
import com.paytech.platform.support.localization.domain.icu.IcuProblem;

/**
 * ICU 校验矩阵（技术方案 3.4、需求 B5）：语法可解析、参数名集合与基准一致（# 不算参数）、选择类参数必须有 other，问题带字符位置。
 *
 * @see IcuMessageValidator#validateSource(String)
 * @see IcuMessageValidator#validateTranslation(String, String)
 * @see IcuMessageValidator#parameterNames(String)
 */
class IcuMessageValidatorTest {

    /** 合法文案：简单参数、plural（含 #、=0、offset）、select、selectordinal、嵌套、number/date/time 与撇号转义。 */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "Send invitation",
        "Hello {name}",
        "{count, plural, one {# member} other {# members}}",
        "{count, plural, =0 {No members} one {One member} other {# members}}",
        "{count, plural, offset:1 =0 {nobody} other {# others}}",
        "{gender, select, female {She} male {He} other {They}} joined",
        "{place, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}",
        "{count, plural, one {{name} has # file} other {{name} has # files}}",
        "Total {amount, number} on {day, date, short} at {at, time}",
        "It''s {name}''s turn"})
    void test_validSource(String source) {
        assertEquals(List.of(), IcuMessageValidator.validateSource(source), source);
    }

    /** 语法错误带中文原因与字符位置；ICU4J 未给位置的未闭合花括号定位到最外层左花括号。 */
    @Test
    void test_syntaxErrors() {
        IcuProblem unmatched = single(IcuMessageValidator.validateSource("Hello {name"));
        IcuProblem badArgument = single(IcuMessageValidator.validateSource("Hi {a b}"));
        IcuProblem badSelect = single(IcuMessageValidator.validateSource("{a, select, x {X} other {Y}"));
        assertAll(
            () -> assertEquals(6, unmatched.position()),
            () -> assertEquals("第 7 个字符：花括号不匹配，缺少右花括号", unmatched.message()),
            () -> assertEquals(4, badArgument.position()),
            () -> assertTrue(badArgument.message().startsWith("第 5 个字符：参数语法错误"), badArgument.message()),
            () -> assertTrue(badSelect.position() >= 0, "应有位置"));
    }

    /** plural、select、selectordinal 缺少 other 均拒绝，并定位到该参数开头。 */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{count, plural, one {# member}}|0|plural 参数缺少 other 分支",
        "Hi {gender, select, male {He} female {She}}|3|select 参数缺少 other 分支",
        "x {n, selectordinal, one {#st}}|2|selectordinal 参数缺少 other 分支"})
    void test_missingOther(String source, int position, String reason) {
        IcuProblem problem = single(IcuMessageValidator.validateSource(source));
        assertAll(() -> assertEquals(position, problem.position()), () -> assertEquals(reason, problem.reason()));
    }

    /** 前端运行时不支持的 choice 与未知简单类型拒绝。 */
    @Test
    void test_unsupportedArgumentTypes() {
        IcuProblem unknown = single(IcuMessageValidator.validateSource("Pay {amount, currency}"));
        IcuProblem choice = single(IcuMessageValidator.validateSource("{n, choice, 0#none|1#one}"));
        assertAll(
            () -> assertEquals("第 5 个字符：不支持的参数类型 currency，只允许 number、date、time", unknown.message()),
            () -> assertEquals(0, choice.position()),
            () -> assertTrue(choice.reason().contains("choice")));
    }

    /** 翻译参数名集合与基准一致才通过；# 不算参数，参数顺序可以不同。 */
    @Test
    void test_translationParameters() {
        String source = "{name} invited {count, plural, one {# member} other {# members}}";
        assertAll(
            () -> assertEquals(List.of(), IcuMessageValidator.validateTranslation(source,
                "{count, plural, other {# 位成员}}由 {name} 邀请")),
            () -> assertEquals(List.of(), IcuMessageValidator.validateTranslation("{count} items", "共 {count} 项")),
            () -> assertEquals(List.of(), IcuMessageValidator.validateTranslation("Saved", "已保存")));
    }

    /** 缺少参数无位置，多出参数定位到首次出现；翻译自身语法错误优先返回。 */
    @Test
    void test_translationMismatch() {
        List<IcuProblem> missing = IcuMessageValidator.validateTranslation("Hello {name}", "你好");
        List<IcuProblem> extra = IcuMessageValidator.validateTranslation("Hello", "你好 {name}");
        List<IcuProblem> broken = IcuMessageValidator.validateTranslation("Hello {name}", "你好 {name");
        assertAll(
            () -> assertEquals(List.of(new IcuProblem(-1, "缺少参数 {name}，英文基准包含该参数")), missing),
            () -> assertEquals(List.of(new IcuProblem(3, "多出参数 {name}，英文基准没有该参数")), extra),
            () -> assertEquals("第 4 个字符：花括号不匹配，缺少右花括号", single(broken).message()));
    }

    /** 参数名按首次出现排列并去重；无法解析时为空。 */
    @Test
    void test_parameterNames() {
        assertAll(
            () -> assertEquals(List.of("name", "count"), IcuMessageValidator.parameterNames(
                "{name}: {count, plural, one {# by {name}} other {# by {name}}}")),
            () -> assertEquals(List.of(), IcuMessageValidator.parameterNames("{broken")),
            () -> assertEquals(List.of(), IcuMessageValidator.parameterNames("No parameters")));
    }

    /** 恰好一条问题。 */
    static IcuProblem single(List<IcuProblem> problems) {
        assertEquals(1, problems.size(), () -> "问题数不为 1：" + problems);
        return problems.getFirst();
    }
}
