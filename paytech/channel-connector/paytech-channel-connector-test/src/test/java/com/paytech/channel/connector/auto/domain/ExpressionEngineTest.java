package com.paytech.channel.connector.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.googlecode.aviator.exception.ExpressionRuntimeException;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionEngine;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionInvalidException;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionProfile;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationScriptValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.ValueBindingValidator;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;

/**
 * Aviator 白名单剖面：锁定只读表达式与公共脚本开放的语法和函数，任何越权写法都在编译时拒绝；
 * 保存时校验把编译失败报告为 EXPRESSION_INVALID。直接调用公开的引擎与校验器，不经过 HTTP。
 *
 * @see ExpressionEngine#compile(ExpressionProfile, String)
 */
class ExpressionEngineTest {

    private static final String EXPRESSION_INVALID = "EXPRESSION_INVALID";

    private final ExpressionEngine engine = new ExpressionEngine();

    /** 只读剖面拒绝赋值、语句、new、静态调用、模块、内部变量、正则与白名单外函数。 */
    @Test
    void test_readOnlyRejectsUnsafeSyntax() {
        for (String source : List.of("input.a = 1", "let x = 1", "if (true) { 1 }", "for i in range(0, 2) { }",
            "while (true) { }", "lambda(x) -> x end", "fn f() { 1 }", "new java.util.Date()", "Math.abs(1)",
            "java.lang.System.exit(0)", "use java.util.*", "require('x')", "load('x')", "eval('1')", "println(1)",
            "rand()", "sysdate()", "input.a =~ /a+/", "input.a.toString()", "seq.put(input, 'k', 1)", "(1")) {
            assertThrows(ExpressionInvalidException.class, () -> engine.compile(ExpressionProfile.READ_ONLY, source),
                source);
        }
    }

    /** 只读剖面允许比较、三元、字符串与数学函数；浮点字面量按十进制解析，不丢精度。 */
    @Test
    void test_readOnlyEvaluates() {
        var env = new HashMap<String, Object>(Map.of("input", Map.of("amount", "10.50", "name", "abc"),
            "steps", Map.of("createOrder", Map.of("executeStatus", "SUCCESS"))));

        assertEquals(true, engine.compile(ExpressionProfile.READ_ONLY,
            "steps.createOrder.executeStatus == 'SUCCESS' && string.length(input.name) == 3").execute(env));
        assertEquals("ok", engine.compile(ExpressionProfile.READ_ONLY,
            "string.startsWith(input.name, 'a') ? 'ok' : 'no' ## 注释里的 eval( 不算调用").execute(env));
        assertEquals(new java.math.BigDecimal("0.3"),
            engine.compile(ExpressionProfile.READ_ONLY, "0.1 + 0.2").execute(env));
    }

    /** 脚本剖面允许赋值、let、if 与有界 for，仍拒绝 while、函数定义、new 与静态调用。 */
    @Test
    void test_scriptProfile() {
        var env = new HashMap<String, Object>(Map.of("http", new HashMap<>(Map.of("requestHeaders", new HashMap<>()))));
        engine.compile(ExpressionProfile.SCRIPT, """
            let prefix = 'Bearer';
            if (prefix != nil) { http.requestHeaders.Authorization = prefix + ' x'; }
            for i in range(0, 3) { http.requestHeaders.Count = i; }
            """).execute(env);

        assertEquals(Map.of("Authorization", "Bearer x", "Count", 2L), ((Map<?, ?>) env.get("http")).get("requestHeaders"));
        for (String source : List.of("while (true) { }", "fn f() { 1 }", "new java.util.Date()",
            "java.lang.System.exit(0)", "eval('1')", "unknownFn(1)")) {
            assertThrows(ExpressionInvalidException.class, () -> engine.compile(ExpressionProfile.SCRIPT, source),
                source);
        }
    }

    /** for 循环超过上限在求值时失败，不会无界执行。 */
    @Test
    void test_loopLimit() {
        var script = engine.compile(ExpressionProfile.SCRIPT,
            "for i in range(0, " + (ExpressionEngine.MAX_LOOP_COUNT + 1) + ") { }");

        assertThrows(ExpressionRuntimeException.class, () -> script.execute(new HashMap<>()));
    }

    /** 以函数值间接调用能绕过静态检查，但白名单外的函数已从实例移除，求值时失败。 */
    @Test
    void test_removedFunctionsUnavailable() {
        var expression = engine.compile(ExpressionProfile.READ_ONLY, "(eval)('1')");

        assertThrows(RuntimeException.class, () -> expression.execute(new HashMap<>()));
    }

    /** 白名单内置函数可直接使用；集合判断与类型转换给出确定结果。 */
    @Test
    void test_whitelistedBuiltins() {
        var env = new HashMap<String, Object>(Map.of("input", Map.of("name", "abc", "count", "7")));

        assertEquals(true, engine.compile(ExpressionProfile.READ_ONLY,
            "is_def(input) && !is_empty(input.name) && long(input.count) == 7 && string.contains(seq.get(input, 'name'), 'b')")
            .execute(env));
    }

    /** 保存时：取值表达式编译失败或引用未知变量报告 EXPRESSION_INVALID；脚本源码同样在 sourceCode 报告。 */
    @Test
    void test_saveTimeIssues() {
        var bindings = new ValueBindingValidator(engine);
        var scripts = new IntegrationScriptValidator(engine);

        assertEquals(List.of(EXPRESSION_INVALID + "@value.expression"),
            bindingIssues(bindings, "{\"kind\":\"EXPRESSION\",\"expression\":\"input.a = 1\"}"));
        assertEquals(List.of(EXPRESSION_INVALID + "@value.expression"),
            bindingIssues(bindings, "{\"kind\":\"EXPRESSION\",\"expression\":\"secret + 1\"}"));
        assertEquals(List.of(), bindingIssues(bindings, "{\"kind\":\"EXPRESSION\",\"expression\":\"input.a + 1\"}"));
        assertEquals(List.of(EXPRESSION_INVALID + "@sourceCode"),
            codes(scripts.validate(List.of(new ParameterDeclaration("key", "STRING", true)), "while (true) { }")));
        assertEquals(List.of(), codes(scripts.validate(List.of(), ExternalApiSamples.signSource())));
    }

    /** 取值绑定的校验问题。 */
    private static List<String> bindingIssues(ValueBindingValidator bindings, String binding) {
        var issues = new java.util.ArrayList<ValidationIssue>();
        bindings.validate(ExternalApiSamples.object(binding), "value", false, path -> null, issues);
        return codes(issues);
    }

    /** 问题的“编码@位置”，排序。 */
    private static List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().map(issue -> issue.getCode() + "@" + issue.getLocation()).toList();
    }
}
