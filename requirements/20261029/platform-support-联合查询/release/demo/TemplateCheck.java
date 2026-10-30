import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.engine.QuerySource;
import com.paytech.platform.support.dataquery.engine.ReadOnlySqlGuard;
import com.paytech.platform.support.dataquery.template.JdbcValueBinder;
import com.paytech.platform.support.dataquery.template.ParameterBindingResult;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;
import com.paytech.platform.support.dataquery.template.QueryTemplateEngine;
import com.paytech.platform.support.dataquery.template.RenderedQuery;
import com.paytech.platform.support.dataquery.template.SystemParameters;
import com.paytech.platform.support.dataquery.template.TemplateLimits;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 联合查询演示模板离线校验（阶段 6；只用候选 Platform JAR 的生产 data-query 引擎，不连任何库）。
 * 输入 validate_templates.py 生成的 JSON：[{templateCode, database, body, parameters, sample}]；对每个模板执行保存时的
 * QueryTemplateEngine.validate（声明、语法、引用、展开校验 + ReadOnlySqlGuard 只读校验），再用 sample 绑定参数、渲染，
 * 把 JDBC 绑定值按 SQL 字面量内联输出，供 validate_templates.py 在本机 v2e2e / mor-test 只读会话里实际执行。
 * 输出每行一个 JSON：{templateCode, problems[], errors, sql}。限制值与生产相同：正文 64 KiB、列表 1000、展开 256。
 */
public class TemplateCheck {
    public static void main(String[] args) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode items = mapper.readTree(Files.readString(Path.of(args[0])));
        QueryTemplateEngine engine = new QueryTemplateEngine(new TemplateLimits(64 * 1024, 1000, 256));
        ReadOnlySqlGuard guard = new ReadOnlySqlGuard();
        JdbcValueBinder binder = new JdbcValueBinder();
        for (JsonNode item : items) {
            String code = item.get("templateCode").asString();
            String body = item.get("body").asString();
            QuerySource source = new QuerySource(DataSourceEngineType.MYSQL, item.get("database").asString());
            List<QueryParameterDeclaration> declarations = new ParameterDeclarationReader().read(item.get("parameters"));
            TemplateValidationResult validation = engine.validate(body, declarations, binder, guard.renderedQueryCheck(source));
            var out = mapper.createObjectNode().put("templateCode", code);
            var problems = out.putArray("problems");
            for (TemplateProblem problem : validation.getProblems()) {
                problems.add(problem.getLevel() + " " + problem.describe());
            }
            out.put("errors", validation.hasErrors());
            if (!validation.hasErrors()) {
                ParameterBindingResult bound = engine.bindParameters(declarations, item.get("sample"), new SystemParameters(null, null));
                RenderedQuery rendered = engine.render(engine.parse(body), bound.requireValues(), binder);
                guard.requireReadOnly(rendered.getText(), source);
                out.put("sql", inline(rendered.getText(), rendered.getBindings()));
            }
            System.out.println(mapper.writeValueAsString(out));
        }
    }

    /** 按出现顺序把 ? 换成字面量；正文里的 ? 只来自绑定（演示正文不含字符串里的问号）。 */
    static String inline(String text, List<Object> bindings) {
        StringBuilder sql = new StringBuilder();
        int next = 0;
        for (char c : text.toCharArray()) {
            if (c == '?' && next < bindings.size()) {
                sql.append(literal(bindings.get(next++)));
            } else {
                sql.append(c);
            }
        }
        if (next != bindings.size()) {
            throw new IllegalStateException("binding count mismatch");
        }
        return sql.toString();
    }

    static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Long || value instanceof Integer || value instanceof BigInteger || value instanceof BigDecimal) {
            return value.toString();
        }
        return "'" + value.toString().replace("\\", "\\\\").replace("'", "''") + "'";
    }
}
