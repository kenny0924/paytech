package com.paytech.risk.decision.domainservice.service.impl;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.risk.decision.core.dal.dataobject.VariableReferenceRow;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Condition;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Expression;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Group;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Predicate;
import com.paytech.risk.decision.domain.flow.DecisionFlowJsonCodec;
import com.paytech.risk.decision.domain.variable.VariableDefinitionValidator;
import com.paytech.risk.decision.domain.variable.VariableReferences.*;
import com.paytech.risk.decision.domainservice.repository.RiskVariableReferenceRepository;
import com.paytech.risk.decision.domainservice.service.RiskVariableReferenceService;
import java.util.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 不使用影子表；索引引用和条件整树扫描共同覆盖历史版本。 */
@Service
@AllArgsConstructor
public class RiskVariableReferenceServiceImpl implements RiskVariableReferenceService {
    private static final List<String> PHASES = List.of("OPERAND", "OPERAND_CURRENCY", "ACTION_PARAMETER", "VARIABLE_DEPENDENCY", "FLOW_EDGE");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    private final DecisionFlowJsonCodec codec;
    private final RiskVariableReferenceRepository repository;

    /** 上界固定在首次读取；只按实际扫描位置推进，不将空命中页误报为末页。 */
    @Override public Page page(String code, String scope, String cursor, int size) {
        VariableDefinitionValidator.code(code);
        if (!Set.of("ALL", "CURRENT", "HISTORY").contains(scope) || size < 1 || size > 100) { throw invalid(); }
        Cursor position = cursor == null ? initial(code, scope) : decode(cursor, code, scope);
        var references = new ArrayList<Reference>();
        int scanned = 0;
        while (position.phase < PHASES.size() && scanned < 1000 && references.size() < size) {
            String phase = PHASES.get(position.phase);
            int limit = Math.min(100, 1000 - scanned);
            var rows = repository.scan(phase, code, scope, position.lastId, position.bounds.get(phase), limit);
            for (VariableReferenceRow row : rows) {
                if (scanned == 1000 || references.size() == size) { break; }
                validateRow(row, position.lastId, position.bounds.get(phase));
                position.lastId = row.scanId(); scanned++;
                Reference reference = reference(code, row);
                if (reference != null) { references.add(reference); }
            }
            if (rows.isEmpty() || rows.size() < limit && references.size() < size) { position.phase++; position.lastId = 0; }
        }
        boolean more = position.phase < PHASES.size();
        return new Page(List.copyOf(references), more ? encode(position) : null, more, scanned);
    }

    /** 删除持根写锁后建立本库读取视图；损坏或超时向上传播，绝不降级为无引用。 */
    @Override public boolean hasReferences(String code) {
        String cursor = null;
        do {
            Page page = page(code, "ALL", cursor, 1);
            if (!page.items().isEmpty()) { return true; }
            cursor = page.nextCursor();
        } while (cursor != null);
        return false;
    }

    /** 首次上界来自同库 SQL，不接受客户端提供权限或数据库目标。 */
    Cursor initial(String code, String scope) {
        Map<String, Long> bounds = new LinkedHashMap<>();
        repository.bounds().forEach(b -> bounds.put(b.phase(), b.upperBound()));
        if (!bounds.keySet().equals(new HashSet<>(PHASES)) || bounds.values().stream().anyMatch(v -> v < 0)) {
            throw new ErrorException("变量引用扫描上界不完整");
        }
        return new Cursor(0, 0, bounds, code, scope);
    }

    /** 非流程引用已由索引精确筛选；流程逐棵严格解析两个格式并提取全部位置。 */
    Reference reference(String code, VariableReferenceRow row) {
        String location = row.location();
        if ("DECISION_FLOW".equals(row.consumerType())) {
            Condition condition = codec.readCondition(row.conditionJson());
            if (condition == null) { throw new ErrorException("条件连线缺少持久化条件"); }
            List<String> paths = new ArrayList<>();
            paths(condition.root(), code, "root", paths);
            if (paths.isEmpty()) { return null; }
            location += ":" + String.join(",", paths);
        }
        return new Reference(row.consumerType(), row.rootId(), row.versionId(), row.versionNo(), row.code(), row.name(), row.scope(), location, code);
    }

    /** 同一连线聚合所有命中路径，在一个候选行内完整返回，避免半行游标漏报。 */
    void paths(Expression expression, String code, String path, List<String> paths) {
        if (expression instanceof Group group) {
            for (int index = 0; index < group.children().size(); index++) {
                paths(group.children().get(index), code, path + ".children[" + index + "]", paths);
            }
            return;
        }
        Predicate predicate = (Predicate) expression;
        if (predicate.operands() == null) { return; }
        for (int index = 0; index < predicate.operands().size(); index++) {
            var operand = predicate.operands().get(index);
            if (code.equals(operand.variableCode())) { paths.add(path + ".operands[" + index + "].variableCode"); }
            if (code.equals(operand.currencyVariableCode())) { paths.add(path + ".operands[" + index + "].currencyVariableCode"); }
        }
    }

    /** 缺失父级或非法排序应阻断删除，不能通过 JOIN 漏掉损坏引用。 */
    void validateRow(VariableReferenceRow row, long lastId, long bound) {
        if (row.scanId() <= lastId || row.scanId() > bound || row.rootId() == null || row.rootId() <= 0
            || row.code() == null || row.name() == null || row.location() == null
            || !Set.of("RULE_SET", "DECISION_FLOW", "VARIABLE").contains(row.consumerType())
            || !Set.of("CURRENT", "HISTORY").contains(row.scope())) { throw new ErrorException("变量使用关系或扫描顺序损坏"); }
        if (!"VARIABLE".equals(row.consumerType()) && (row.versionId() == null || row.versionId() <= 0
            || row.versionNo() == null || row.versionNo() <= 0)) { throw new ErrorException("变量引用缺少精确版本"); }
    }

    /** 游标只是有界位置，客户端无法据此绕过每次请求的授权或作用域。 */
    Cursor decode(String cursor, String code, String scope) {
        if (cursor.length() > 2048 || !cursor.matches("[A-Za-z0-9_-]+")) { throw invalid(); }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(cursor)) { throw invalid(); }
            JsonNode tree = JSON.readTree(Rfc8785JsonCanonicalizer.canonicalizeObject(bytes));
            if (!tree.isObject() || !tree.propertyNames().equals(Set.of("phase", "lastId", "upperBounds", "scope", "variableCode"))) { throw invalid(); }
            int phase = PHASES.indexOf(text(tree.get("phase")));
            if (phase < 0 || !scope.equals(text(tree.get("scope"))) || !code.equals(text(tree.get("variableCode")))) { throw invalid(); }
            JsonNode upper = tree.get("upperBounds");
            if (!upper.isObject() || !upper.propertyNames().equals(new HashSet<>(PHASES))) { throw invalid(); }
            Map<String, Long> bounds = new LinkedHashMap<>();
            PHASES.forEach(p -> bounds.put(p, id(upper.get(p))));
            long last = id(tree.get("lastId"));
            if (last > bounds.get(PHASES.get(phase))) { throw invalid(); }
            return new Cursor(phase, last, bounds, code, scope);
        } catch (JacksonException | IllegalArgumentException | com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException failure) { throw invalid(); }
    }

    /** 只编码规范十进制行号，不包含任意 SQL、URL 或权限。 */
    String encode(Cursor cursor) {
        Map<String, String> bounds = new LinkedHashMap<>();
        cursor.bounds.forEach((key, value) -> bounds.put(key, Long.toString(value)));
        var body = Map.of("phase", PHASES.get(cursor.phase), "lastId", Long.toString(cursor.lastId),
            "upperBounds", bounds, "scope", cursor.scope, "variableCode", cursor.code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(body));
    }

    /** 行号使用非负有符号 BIGINT 字符串，拒绝数值 JSON 与溢出。 */
    long id(JsonNode node) {
        String value = text(node);
        if (!value.matches("0|[1-9][0-9]{0,18}")) { throw invalid(); }
        return Long.parseLong(value);
    }
    /** 不对游标标量做宽松类型转换。 */
    String text(JsonNode node) {
        if (node == null || !node.isString()) { throw invalid(); }
        return node.stringValue();
    }
    /** 只返回安全的通用参数错误，不泄露内部解析细节。 */
    InvalidParameterException invalid() { return new InvalidParameterException("变量引用游标或查询参数无效"); }

    /** 单次分页内部的可变扫描位置，不保存到服务实例或数据库。 */
    static final class Cursor {
        int phase;
        long lastId;
        final Map<String, Long> bounds;
        final String code;
        final String scope;
        /** 固定一次遍历的上界和身份，只有扫描位置可推进。 */
        Cursor(int phase, long lastId, Map<String, Long> bounds, String code, String scope) {
            this.phase = phase; this.lastId = lastId; this.bounds = Map.copyOf(bounds); this.code = code; this.scope = scope;
        }
    }
}
