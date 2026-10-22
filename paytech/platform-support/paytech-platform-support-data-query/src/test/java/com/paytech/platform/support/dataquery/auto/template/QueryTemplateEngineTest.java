package com.paytech.platform.support.dataquery.auto.template;

import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.COMMERCE_SOURCE;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.JSON;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.ORDER_BODY;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.ORDER_PARAMETERS;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.WHOLE_INSTANCE_SOURCE;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.declarations;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.engine;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.json;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.dataquery.engine.QuerySource;
import com.paytech.platform.support.dataquery.engine.ReadOnlySqlGuard;
import com.paytech.platform.support.dataquery.template.JdbcValueBinder;
import com.paytech.platform.support.dataquery.template.ParameterBindingResult;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;
import com.paytech.platform.support.dataquery.template.QueryTemplateEngine;
import com.paytech.platform.support.dataquery.template.RenderedQuery;
import com.paytech.platform.support.dataquery.template.SystemParameters;
import com.paytech.platform.support.dataquery.template.TemplateCheckException;
import com.paytech.platform.support.dataquery.template.TemplateLimits;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;
import com.paytech.platform.support.dataquery.template.ValueBinder;

import tools.jackson.databind.JsonNode;

/**
 * 统一模板语法（需求第 7 节、技术方案第 3 节）：渲染、参数绑定、语法错误定位与保存时的展开校验。
 *
 * @see QueryTemplateEngine#parse(String)
 * @see QueryTemplateEngine#bindParameters(List, JsonNode, SystemParameters)
 * @see QueryTemplateEngine#render(com.paytech.platform.support.dataquery.template.QueryTemplate, Map, ValueBinder)
 * @see QueryTemplateEngine#validate(String, List, ValueBinder, com.paytech.platform.support.dataquery.template.RenderedQueryCheck)
 */
class QueryTemplateEngineTest {

    private static final SystemParameters NO_SYSTEM = new SystemParameters(null, null);

    private final QueryTemplateEngine engine = engine();

    private final JdbcValueBinder jdbc = new JdbcValueBinder();

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    /** 需求示例一：只传 statuses 与 sortBy，逐字比对渲染结果。 */
    @Test
    void test_jdbcExampleRendersExactly() {
        RenderedQuery rendered = renderJdbc(ORDER_BODY, ORDER_PARAMETERS, """
            {"statuses":["PAID","REFUNDED"],"sortBy":"NEWEST"}""");

        Assertions.assertEquals("""
            SELECT order_id, client_id, amount, status, created_at
            FROM t_order
            WHERE status IN (?, ?)
            ORDER BY created_at DESC""", rendered.getText());
        Assertions.assertEquals(List.of("PAID", "REFUNDED"), rendered.getBindings());
    }

    /** 示例一的全部条件：标量、集合、日期时间、对象集合与 each 分隔符，绑定顺序与占位符一致。 */
    @Test
    void test_jdbcExampleWithEveryCondition() {
        RenderedQuery rendered = renderJdbc(ORDER_BODY, ORDER_PARAMETERS, """
            {"clientId":"1001","statuses":["PAID"],"from":"2026-10-01T00:00:00","sortBy":"OLDEST",
             "conditions":[{"channel":"NI","minAmount":"10.50"},{"channel":"GEIDEA","minAmount":"20"}]}""");

        Assertions.assertEquals("""
            SELECT order_id, client_id, amount, status, created_at
            FROM t_order
            WHERE client_id = ? AND status IN (?) AND created_at >= ? AND ((channel = ? AND amount > ?) OR \
            (channel = ? AND amount > ?))
            ORDER BY created_at ASC""", rendered.getText());
        Assertions.assertEquals(List.of(1001L, "PAID", LocalDateTime.of(2026, 10, 1, 0, 0), "NI",
            new BigDecimal("10.50"), "GEIDEA", new BigDecimal("20")), rendered.getBindings());
    }

    /** join 全部为空时 prefix 也不输出；片段参数取默认值。 */
    @Test
    void test_joinWithoutSegmentsOmitsPrefix() {
        RenderedQuery rendered = renderJdbc(ORDER_BODY, ORDER_PARAMETERS, "{}");

        Assertions.assertEquals("""
            SELECT order_id, client_id, amount, status, created_at
            FROM t_order

            ORDER BY created_at DESC""", rendered.getText());
        Assertions.assertTrue(rendered.getBindings().isEmpty());
    }

    /** 需求示例二：同一语法用 JSON 绑定器输出 ES 查询，join 处理逗号。 */
    @Test
    void test_elasticsearchExampleJoinsJsonLiterals() {
        String body = """
            { "query": { "bool": { "filter": [
              {{#join ","}}
                {{#if clientId}}{ "term": { "client_id": #{clientId} } }{{/if}}
                {{#if statuses}}{ "terms": { "status": #{statuses} } }{{/if}}
              {{/join}}
            ] } } }""";
        String parameters = """
            [{"name":"clientId","type":"INTEGER"},
             {"name":"statuses","type":"LIST","elementType":"ENUM","allowedValues":["PAID","REFUNDED"]}]""";

        RenderedQuery both = render(body, parameters, """
            {"clientId":"42","statuses":["PAID","REFUNDED"]}""", new JsonLiteralBinder());
        RenderedQuery statusOnly = render(body, parameters, """
            {"statuses":["PAID"]}""", new JsonLiteralBinder());

        Assertions.assertEquals("""
            { "query": { "bool": { "filter": [
              { "term": { "client_id": 42 } },{ "terms": { "status": ["PAID","REFUNDED"] } }
            ] } } }""", both.getText());
        Assertions.assertEquals(2, json(both.getText()).at("/query/bool/filter").size());
        Assertions.assertEquals(1, json(statusOnly.getText()).at("/query/bool/filter").size());
        Assertions.assertTrue(both.getBindings().isEmpty());
    }

    /** if / elseif / else 按顺序取第一个成立的分支，null 落入 else。 */
    @Test
    void test_ifElseIfElseBranches() {
        String body = "x = {{#if mode == 'A'}}1{{#elseif mode == 'B'}}2{{#else}}3{{/if}}";
        String parameters = """
            [{"name":"mode","type":"ENUM","allowedValues":["A","B","C"]}]""";

        Assertions.assertEquals("x = 1", render(body, parameters, "{\"mode\":\"A\"}", jdbc).getText());
        Assertions.assertEquals("x = 2", render(body, parameters, "{\"mode\":\"B\"}", jdbc).getText());
        Assertions.assertEquals("x = 3", render(body, parameters, "{\"mode\":\"C\"}", jdbc).getText());
        Assertions.assertEquals("x = 3", render(body, parameters, "{}", jdbc).getText());
    }

    /** each 的下标变量与分隔符；下标按 64 位整数绑定。 */
    @Test
    void test_eachWithIndexAndSeparator() {
        RenderedQuery rendered = render("VALUES {{#each ids as id, i separator=\", \"}}(#{i}, #{id}){{/each}}", """
            [{"name":"ids","type":"LIST","elementType":"INTEGER"}]""", "{\"ids\":[5,\"6\"]}", jdbc);

        Assertions.assertEquals("VALUES (?, ?), (?, ?)", rendered.getText());
        Assertions.assertEquals(List.of(0L, 5L, 1L, 6L), rendered.getBindings());
    }

    /** join 内的 part 与 if：空段丢弃，前后缀包住非空段；全部为空时什么都不输出。 */
    @Test
    void test_joinPartsWithPrefixAndSuffix() {
        String body = "{{#join \" OR \" prefix=\"(\" suffix=\")\"}}{{#part}} a = 1 {{/part}}{{#part}}  {{/part}}"
            + "{{#if flag}}b = #{flag}{{/if}}{{/join}}";
        String parameters = "[{\"name\":\"flag\",\"type\":\"BOOLEAN\"}]";

        RenderedQuery withFlag = render(body, parameters, "{\"flag\":true}", jdbc);
        RenderedQuery empty = render("{{#join \",\"}}{{#part}} {{/part}}{{/join}}", "[]", "{}", jdbc);

        Assertions.assertEquals("(a = 1 OR b = ?)", withFlag.getText());
        Assertions.assertEquals(List.of(Boolean.TRUE), withFlag.getBindings());
        Assertions.assertEquals("(a = 1)", render(body, parameters, "{\"flag\":false}", jdbc).getText());
        Assertions.assertEquals("", empty.getText());
    }

    /** 注释不输出；\#{、\@{、\{{ 原样输出。 */
    @Test
    void test_commentsAndEscapes() {
        RenderedQuery rendered = render("SELECT 1 {{!-- 说明 #{x} {{#if}} --}}AS \\#{a} \\@{b} \\{{c}}", "[]", "{}",
            jdbc);

        Assertions.assertEquals("SELECT 1 AS #{a} @{b} {{c}}", rendered.getText());
        Assertions.assertTrue(rendered.getBindings().isEmpty());
    }

    /** 表达式：非空判断、!、&&、||、括号，数字按数值比较，日期之间比较。 */
    @Test
    void test_expressionOperators() {
        String body = "{{#if a > 10 && b <= 2.5}}A{{/if}}{{#if !s || s == 'x'}}B{{/if}}{{#if flag}}X{{/if}}"
            + "{{#if !flag}}C{{/if}}{{#if list}}L{{/if}}{{#if !list}}E{{/if}}{{#if from <= to}}D{{/if}}"
            + "{{#if (a == 11 || a == 12) && !(b != 2.50)}}F{{/if}}{{#if s != null}}N{{/if}}";
        String parameters = """
            [{"name":"a","type":"INTEGER"},{"name":"b","type":"DECIMAL"},{"name":"s","type":"STRING"},
             {"name":"flag","type":"BOOLEAN"},{"name":"list","type":"LIST","elementType":"STRING"},
             {"name":"from","type":"DATE"},{"name":"to","type":"DATE"}]""";

        RenderedQuery rendered = render(body, parameters, """
            {"a":11,"b":"2.5","s":"","flag":false,"list":[],"from":"2026-01-01","to":"2026-02-01"}""", jdbc);

        Assertions.assertEquals("ABCEDFN", rendered.getText());
    }

    /** 比较两边类型不兼容：保存时指出位置，绕过保存直接渲染也会拒绝，不做隐式转换。 */
    @Test
    void test_incompatibleComparison() {
        String body = "SELECT 1 FROM t {{#if clientId == 'x'}}WHERE 1 = 1{{/if}}";
        String parameters = "[{\"name\":\"clientId\",\"type\":\"INTEGER\"}]";

        TemplateValidationResult result = validate(body, parameters, COMMERCE_SOURCE);
        TemplateCheckException runtime = Assertions.assertThrows(TemplateCheckException.class,
            () -> render(body, parameters, "{\"clientId\":1}", jdbc));

        assertProblems(result, TemplateProblemLevel.ERROR, "1:32 比较两边类型不兼容：数字 == 字符串");
        Assertions.assertEquals(32, runtime.getProblem().getColumn());
    }

    /** 注入尝试作为值时只进入绑定值，正文结构不变，并且仍通过只读校验。 */
    @Test
    void test_injectionValueOnlyEntersBindings() {
        RenderedQuery rendered = render("SELECT id FROM t_order WHERE name = #{name}",
            "[{\"name\":\"name\",\"type\":\"STRING\"}]", "{\"name\":\"' OR 1=1 --\"}", jdbc);

        Assertions.assertEquals("SELECT id FROM t_order WHERE name = ?", rendered.getText());
        Assertions.assertEquals(List.of("' OR 1=1 --"), rendered.getBindings());
        Assertions.assertTrue(guard.inspect(rendered.getText(), COMMERCE_SOURCE).passed());
    }

    /** 空集合直接写入 #{} 时渲染失败并指出位置，提示用 {{#if}} 包住。 */
    @Test
    void test_emptyCollectionInValueFails() {
        TemplateCheckException exception = Assertions.assertThrows(TemplateCheckException.class,
            () -> render("SELECT * FROM t WHERE status IN (#{statuses})",
                "[{\"name\":\"statuses\",\"type\":\"LIST\",\"elementType\":\"STRING\"}]", "{\"statuses\":[]}", jdbc));

        Assertions.assertEquals(1, exception.getProblem().getLine());
        Assertions.assertEquals(34, exception.getProblem().getColumn());
        Assertions.assertTrue(exception.getMessage().contains("集合为空"), exception.getMessage());
    }

    /** 语法错误带行号、列号与原因。 */
    @Test
    void test_syntaxErrorsCarryPosition() {
        assertSyntaxError("SELECT 1\n{{#if a}}x", 2, 1, "{{#if}} 缺少结束标签 {{/if}}");
        assertSyntaxError("{{#if a}}x{{/each}}", 1, 11, "标签不成对");
        assertSyntaxError("SELECT {{#foo}}", 1, 8, "未知标签：{{#foo}}");
        assertSyntaxError("x {{#else}}", 1, 3, "{{#else}} 必须写在 {{#if}} 内");
        assertSyntaxError("{{#if a}}1{{#else}}2{{#elseif b}}3{{/if}}", 1, 21, "不能出现在 {{#else}} 之后");
        assertSyntaxError("{{#join \",\"}}\n  bare text\n{{/join}}", 2, 3, "{{#join}} 内只能直接包含");
        assertSyntaxError("{{#join \",\"}}#{a}{{/join}}", 1, 14, "{{#join}} 内只能直接包含");
        assertSyntaxError("{{#part}}x{{/part}}", 1, 1, "{{#part}} 只能直接写在 {{#join}} 内");
        assertSyntaxError("{{#if a ==}}x{{/if}}", 1, 11, "{{#if}} 缺少条件");
        assertSyntaxError("{{#if a = 1}}x{{/if}}", 1, 9, "{{#if}} 多余的内容：=");
        assertSyntaxError("{{#if fn(a)}}x{{/if}}", 1, 9, "{{#if}} 多余的内容：(");
        assertSyntaxError("{{#each items}}x{{/each}}", 1, 14, "{{#each}} 缺少 as");
        assertSyntaxError("SELECT #{a", 1, 8, "#{ 缺少结尾 }");
        assertSyntaxError("SELECT #{a b}", 1, 8, "#{} 内的路径不合法：a b");
        assertSyntaxError("{{!-- x", 1, 1, "注释 {{!-- 缺少结尾 --}}");
        assertSyntaxError("{{#if 'x}}", 1, 1, "标签缺少结尾 }}");
    }

    /** 参数错误带字段路径：未声明、系统参数、类型、枚举、对象字段、片段取值。 */
    @Test
    void test_parameterErrorsCarryFieldPath() {
        ParameterBindingResult result = engine.bindParameters(declarations(ORDER_PARAMETERS), json("""
            {"clientId":"abc","statuses":["PAID","LOST"],"from":"2026-10-01T00:00:00+04:00","sortBy":"RANDOM",
             "conditions":[{"channel":"NI","minAmount":"1"},{"channel":"X","minAmount":5,"bogus":1}],
             "unknown":1,"__operator":{"accountId":"1"}}"""), NO_SYSTEM);

        Assertions.assertEquals(List.of(
            "params.unknown 未声明的参数",
            "params.__operator 系统参数只能由服务端注入",
            "params.clientId 必须是 64 位整数（字符串或整数）",
            "params.statuses[1] 取值不在允许范围内",
            "params.conditions[1].bogus 未声明的字段",
            "params.conditions[1].minAmount 必须是十进制字符串",
            "params.from 必须是不带时区的 ISO-8601 日期时间，如 2026-10-22T08:30:00",
            "params.sortBy 取值不在可选范围内"), errors(result));
        Assertions.assertThrows(InvalidParameterException.class, result::requireValues);
    }

    /** 必填、长度、声明与系统的集合上限。 */
    @Test
    void test_parameterLimits() {
        String parameters = """
            [{"name":"ids","type":"LIST","elementType":"INTEGER","maxSize":2,"required":true},
             {"name":"name","type":"STRING","required":true,"maxLength":3},
             {"name":"amount","type":"DECIMAL","min":"1","max":"100"},
             {"name":"tags","type":"LIST","elementType":"STRING"}]""";
        QueryTemplateEngine smallLists = new QueryTemplateEngine(new TemplateLimits(64 * 1024, 3, 256));

        ParameterBindingResult missing = engine.bindParameters(declarations(parameters), json("{}"), NO_SYSTEM);
        ParameterBindingResult exceeded = smallLists.bindParameters(declarations(parameters), json("""
            {"ids":[1,2,3],"name":"abcd","amount":"100.01","tags":["a","b","c","d"]}"""), NO_SYSTEM);

        Assertions.assertEquals(List.of("params.ids 必填", "params.name 必填"), errors(missing));
        Assertions.assertEquals(List.of("params.ids 元素数不能超过 2", "params.name 长度不能超过 3",
            "params.amount 不能大于 100", "params.tags 元素数不能超过 3"), errors(exceeded));
    }

    /** 系统参数由服务端注入：操作人与游标。 */
    @Test
    void test_systemParametersInjected() {
        String body = "SELECT * FROM t WHERE owner_id = #{__operator.accountId}"
            + "{{#if __cursor}} AND id < #{__cursor.id}{{/if}} ORDER BY id DESC";

        RenderedQuery firstPage = renderWith(body, new SystemParameters(77L, null));
        RenderedQuery nextPage = renderWith(body, new SystemParameters(77L, Map.of("id", 500L)));

        Assertions.assertEquals("SELECT * FROM t WHERE owner_id = ? ORDER BY id DESC", firstPage.getText());
        Assertions.assertEquals(List.of(77L), firstPage.getBindings());
        Assertions.assertEquals("SELECT * FROM t WHERE owner_id = ? AND id < ? ORDER BY id DESC", nextPage.getText());
        Assertions.assertEquals(List.of(77L, 500L), nextPage.getBindings());
    }

    /** 参数声明的保存校验：片段原文、ENUM 取值、LIST 元素类型、默认值、保留名与上下限。 */
    @Test
    void test_declarationProblems() {
        TemplateValidationResult result = validate("SELECT 1", """
            [{"name":"sortBy","type":"FRAGMENT","choices":{"BAD":"id; DROP TABLE t","EMPTY":" "}},
             {"name":"status","type":"ENUM"},
             {"name":"nested","type":"LIST","elementType":"LIST"},
             {"name":"limitRows","type":"INTEGER","default":"abc"},
             {"name":"__secret","type":"STRING"},
             {"name":"amount","type":"DECIMAL","min":"10","max":"1"},
             {"name":"note","type":"BOOLEAN","maxLength":3}]""", COMMERCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR,
            "parameters[0].choices.BAD 片段原文必须是纯文本，不能包含 ;",
            "parameters[0].choices.EMPTY 取值与片段原文都不能为空",
            "parameters[1].allowedValues ENUM 必须声明取值范围",
            "parameters[2].elementType 必须声明，且不能是 LIST 或 FRAGMENT",
            "parameters[3].default 必须是 64 位整数（字符串或整数）",
            "parameters[4].name __ 开头的名称保留给系统参数",
            "parameters[5].min 不能大于 max",
            "parameters[6].maxLength 只适用于 STRING");
    }

    /** 声明 JSON 的结构错误直接拒绝，错误带 JSON 路径。 */
    @Test
    void test_declarationStructureRejected() {
        ParameterDeclarationReader reader = new ParameterDeclarationReader();

        InvalidParameterException type = Assertions.assertThrows(InvalidParameterException.class,
            () -> reader.read(json("[{\"name\":\"x\",\"type\":\"WHATEVER\"}]")));
        InvalidParameterException property = Assertions.assertThrows(InvalidParameterException.class,
            () -> reader.read(json("[{\"name\":\"x\",\"type\":\"STRING\",\"pattern\":\".*\"}]")));

        Assertions.assertEquals("parameters[0].type 取值不合法：WHATEVER", type.getMessage());
        Assertions.assertEquals("parameters[0] 含有不支持的属性：pattern", property.getMessage());
    }

    /** 引用检查：未声明参数、对象集合与片段写入 #{}、遍历非集合；未使用参数给出警告。 */
    @Test
    void test_referenceProblems() {
        TemplateValidationResult result = validate("""
            SELECT * FROM t
            WHERE a = #{missing}
              AND b IN (#{conditions})
              AND c = #{sortBy}
            {{#each clientId as x}}#{x}{{/each}}""", ORDER_PARAMETERS, COMMERCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR,
            "2:11 引用了未声明的参数：missing",
            "3:13 #{conditions}：对象集合不能直接写入 #{}，请用 {{#each}} 逐个取字段",
            "4:11 #{sortBy}：片段参数请用 @{sortBy} 输出",
            "5:1 {{#each}} 只能遍历集合参数：clientId");
        assertProblems(result, TemplateProblemLevel.WARNING,
            "parameters[1] 参数 statuses 已声明但未在正文中使用",
            "parameters[3] 参数 from 已声明但未在正文中使用");
    }

    /** 需求示例一在指定默认库的数据源上通过全部保存校验，没有任何问题。 */
    @Test
    void test_jdbcExampleValidatesClean() {
        TemplateValidationResult result = validate(ORDER_BODY, ORDER_PARAMETERS, COMMERCE_SOURCE);

        Assertions.assertTrue(result.getProblems().isEmpty(), () -> describe(result.getProblems()));
    }

    /** 整实例数据源：未限定库名的表在展开校验中报错，并定位到模板行列。 */
    @Test
    void test_wholeInstanceRequiresQualifiedTables() {
        TemplateValidationResult result = validate(ORDER_BODY, ORDER_PARAMETERS, WHOLE_INSTANCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR, "2:6 表 t_order 未限定库名：数据源未指定默认库，请写成 库.表");
    }

    /** 只在某个分支出现的加锁子句也能在保存时发现，定位到分支内的文本。 */
    @Test
    void test_expansionFindsLockInBranch() {
        TemplateValidationResult result = validate("""
            SELECT id FROM t_order
            WHERE id > 0
            {{#if lock}}FOR UPDATE{{/if}}""", "[{\"name\":\"lock\",\"type\":\"BOOLEAN\"}]", COMMERCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR, "3:13 不允许 FOR UPDATE / FOR SHARE");
    }

    /** 只在某个分支出现的第二条语句在保存时被拒绝。 */
    @Test
    void test_expansionFindsSecondStatementInBranch() {
        TemplateValidationResult result = validate("SELECT id FROM t_order{{#if purge}}; DELETE FROM t_order{{/if}}",
            "[{\"name\":\"purge\",\"type\":\"BOOLEAN\",\"required\":true}]", COMMERCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR, "1:1 只允许一条语句");
    }

    /** 样例取值走不到的分支会被强制走一次，分支内的黑名单函数被发现。 */
    @Test
    void test_expansionForcesUnreachedBranch() {
        TemplateValidationResult result = validate("""
            SELECT id FROM t_order
            {{#if amount > 100}}WHERE SLEEP(1) = 0{{/if}}""",
            "[{\"name\":\"amount\",\"type\":\"DECIMAL\",\"required\":true}]", COMMERCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR, "2:27 不允许调用函数 SLEEP");
    }

    /** 集合取空集合的组合会发现未用 {{#if}} 包住的 #{集合}。 */
    @Test
    void test_expansionFindsUnguardedEmptyCollection() {
        TemplateValidationResult result = validate("SELECT id FROM t_order WHERE status IN (#{statuses})", """
            [{"name":"statuses","type":"LIST","elementType":"ENUM","allowedValues":["PAID"]}]""", COMMERCE_SOURCE);

        assertProblems(result, TemplateProblemLevel.ERROR,
            "1:41 #{statuses}：集合为空，不能直接写入 #{}，请用 {{#if}} 包住或改用 {{#each}}");
    }

    /** 组合数超过上限时改用覆盖集并给出提示，分支内的问题仍被发现。 */
    @Test
    void test_expansionUsesCoveringSetWhenTooMany() {
        QueryTemplateEngine smallBudget = new QueryTemplateEngine(new TemplateLimits(64 * 1024, 1000, 4));
        String parameters = ORDER_PARAMETERS.replace("\n]", ",\n  {\"name\":\"lock\",\"type\":\"BOOLEAN\"}\n]");
        String body = ORDER_BODY + "\n{{#if lock}}FOR UPDATE{{/if}}";

        TemplateValidationResult result = smallBudget.validate(body, declarations(parameters), jdbc,
            guard.renderedQueryCheck(COMMERCE_SOURCE));

        assertProblems(result, TemplateProblemLevel.INFO, "组合较多（共 96 种），已按覆盖集校验 3 种");
        assertProblems(result, TemplateProblemLevel.ERROR, "10:13 不允许 FOR UPDATE / FOR SHARE");
    }

    /** 正文长度由调用方传入的上限决定。 */
    @Test
    void test_bodyLengthLimit() {
        QueryTemplateEngine tiny = new QueryTemplateEngine(new TemplateLimits(8, 1000, 256));

        TemplateValidationResult result = tiny.validate("SELECT 1 FROM t", List.of(), jdbc,
            guard.renderedQueryCheck(COMMERCE_SOURCE));

        assertProblems(result, TemplateProblemLevel.ERROR, "正文不能超过 8 字节");
    }

    /**
     * 解析、绑定并用指定绑定器渲染。
     *
     * @param body 正文
     * @param parameters 参数声明 JSON
     * @param params 调用方参数 JSON
     * @param binder 值绑定器
     * @return 渲染结果
     */
    private RenderedQuery render(String body, String parameters, String params, ValueBinder binder) {
        Map<String, Object> values = engine.bindParameters(declarations(parameters), json(params), NO_SYSTEM)
            .requireValues();
        return engine.render(engine.parse(body), values, binder);
    }

    /**
     * 用 JDBC 绑定器渲染。
     *
     * @param body 正文
     * @param parameters 参数声明 JSON
     * @param params 调用方参数 JSON
     * @return 渲染结果
     */
    private RenderedQuery renderJdbc(String body, String parameters, String params) {
        return render(body, parameters, params, jdbc);
    }

    /**
     * 无声明参数、带系统参数渲染。
     *
     * @param body 正文
     * @param system 系统参数
     * @return 渲染结果
     */
    private RenderedQuery renderWith(String body, SystemParameters system) {
        Map<String, Object> values = engine.bindParameters(List.of(), json("{}"), system).requireValues();
        return engine.render(engine.parse(body), values, jdbc);
    }

    /**
     * 以 JDBC 绑定器与只读校验做保存校验。
     *
     * @param body 正文
     * @param parameters 参数声明 JSON
     * @param source 数据源
     * @return 校验结果
     */
    private TemplateValidationResult validate(String body, String parameters, QuerySource source) {
        return engine.validate(body, declarations(parameters), jdbc, guard.renderedQueryCheck(source));
    }

    /**
     * 断言解析失败的位置与原因。
     *
     * @param body 正文
     * @param line 期望行号
     * @param column 期望列号
     * @param message 原因应包含的文本
     */
    private void assertSyntaxError(String body, int line, int column, String message) {
        TemplateCheckException exception = Assertions.assertThrows(TemplateCheckException.class,
            () -> engine.parse(body), body);
        TemplateProblem problem = exception.getProblem();
        Assertions.assertEquals(line + ":" + column, problem.getLine() + ":" + problem.getColumn(), body);
        Assertions.assertTrue(problem.getMessage().contains(message), body + " → " + problem.getMessage());
    }

    /**
     * 断言某级别的问题恰好为给定列表（按顺序）。
     *
     * @param result 校验结果
     * @param level 级别
     * @param expected 期望问题，形如“行:列 原因”或“字段 原因”
     */
    private static void assertProblems(TemplateValidationResult result, TemplateProblemLevel level,
        String... expected) {
        List<TemplateProblem> actual = result.getProblems().stream().filter(problem -> problem.getLevel() == level)
            .toList();
        Assertions.assertEquals(Arrays.asList(expected), actual.stream().map(QueryTemplateEngineTest::key).toList(),
            () -> describe(result.getProblems()));
    }

    /**
     * 问题的比对形式。
     *
     * @param problem 问题
     * @return “行:列 原因”“字段 原因”或原因
     */
    private static String key(TemplateProblem problem) {
        if (problem.getLine() != null) {
            return problem.getLine() + ":" + problem.getColumn() + " " + problem.getMessage();
        }
        return problem.getField() == null ? problem.getMessage() : problem.getField() + " " + problem.getMessage();
    }

    /**
     * 全部问题的可读描述，用于失败信息。
     *
     * @param problems 问题
     * @return 描述
     */
    private static String describe(List<TemplateProblem> problems) {
        return problems.stream().map(problem -> problem.getLevel().getCode() + " " + key(problem)).toList().toString();
    }

    /**
     * 字段错误的比对形式。
     *
     * @param result 绑定结果
     * @return “字段 原因”列表
     */
    private static List<String> errors(ParameterBindingResult result) {
        return result.getErrors().stream().map(error -> error.getField() + " " + error.getMessage()).toList();
    }

    /** 测试内的 JSON 字面量绑定器：模拟以后的 ES 引擎，值输出为转义后的 JSON，不追加绑定值。 */
    private static final class JsonLiteralBinder implements ValueBinder {

        /**
         * 输出 JSON 字面量。
         *
         * @param value 值
         * @param bindings 绑定值列表，不使用
         * @return JSON 字面量
         */
        @Override
        public String bind(Object value, List<Object> bindings) {
            if (value instanceof Collection<?> collection) {
                StringJoiner joiner = new StringJoiner(",", "[", "]");
                collection.forEach(element -> joiner.add(literal(element)));
                return joiner.toString();
            }
            return literal(value);
        }

        /**
         * 单个值的 JSON 字面量。
         *
         * @param value 值
         * @return JSON 字面量
         */
        private static String literal(Object value) {
            return value instanceof Number || value instanceof Boolean || value == null ? String.valueOf(value)
                : JSON.writeValueAsString(String.valueOf(value));
        }
    }
}
