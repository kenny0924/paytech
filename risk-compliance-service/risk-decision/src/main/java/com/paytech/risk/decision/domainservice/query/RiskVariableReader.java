package com.paytech.risk.decision.domainservice.query;

import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.RiskVariableDefinition;
import com.paytech.risk.decision.core.dal.dataobject.RiskVariableMember;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import com.paytech.risk.decision.domainservice.repository.RiskVariableMemberRepository;
import com.paytech.risk.decision.domainservice.repository.RiskVariableRepository;
import com.paytech.risk.decision.domainservice.service.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/** 复用变量本地读取算法，事务和跨能力编排由 Owner Processor 管理。 */
@Service
@AllArgsConstructor
public class RiskVariableReader {
    private final RiskVariableRepository repository;
    private final RiskVariableMemberRepository memberRepository;

    /** 系统映射仅供历史对照，超出兼容容量时报错而不截断。 */
    public List<Entry> systemDefinitions(String pointCode) {
        var values = repository.systemDefinitions(pointCode);
        if (values.size() > 100) throw new ErrorException("系统映射数量超出兼容合同容量");
        return values.stream().map(this::verified).toList();
    }

    /** 列表根、当前常量和集合预览属于同一快照；计数及预览批量读取。 */
    public Page list(Query query, Instant valuesAsOf) {
        int limit = query.codes() == null ? query.size() : query.codes().size();
        int offset = query.codes() == null ? Math.multiplyExact(query.page() - 1, query.size()) : 0;
        List<Entry> entries = repository.list(query, limit, offset).stream().map(this::verified).toList();
        Set<String> found = entries.stream().map(e -> e.definition().variableCode()).collect(Collectors.toSet());
        List<String> missing = query.codes() == null ? null : query.codes().stream().filter(c -> !found.contains(c)).toList();
        return new Page(views(entries), query.codes() == null ? repository.count(query) : null, missing, valuesAsOf);
    }

    /** 单个变量详情允许读取停用和墓碑，未知编码返回业务未找到。 */
    public View detail(String code) {
        Entry entry = required(code, null);
        View summary = views(List.of(entry)).getFirst();
        return new View(entry, summary.memberCount(), summary.previewMembers(), allowedValues(entry.definition()));
    }

    /** 翻页必须持原根版本，保证用户不会把两次成员集拼成一个集合。 */
    public MemberPage members(String code, String pattern, int page, int size, Long expectedVersion, Instant valuesAsOf) {
        Entry entry = required(code, null);
        requireCollection(entry);
        if (page > 1 && expectedVersion == null) { throw invalid("成员翻页须携带首次查询版本"); }
        if (expectedVersion != null && expectedVersion != entry.version()) {
            throw failure(VARIABLE_VERSION_CONFLICT, "集合成员已改变，请重新加载");
        }
        List<Member> members = memberRepository.members(code, pattern, size, Math.multiplyExact(page - 1, size), false).stream()
            .map(row -> RiskVariablePersistenceConverter.member(entry.definition().valueType(), row)).toList();
        return new MemberPage(code, entry.version(), members, memberRepository.memberCount(code, pattern), valuesAsOf);
    }

    /** 领域引用消费者只收到当前结构投影。 */
    public Map<String, Entry> lockDefinitions(Set<String> codes) {
        return lockRoots(codes).entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey, e -> RiskVariablePersistenceConverter.entry(e.getValue())));
    }

    /** 按固定结构闭包排序共享锁，Context 保留实际读取的根。 */
    public Map<String, RiskVariableDefinition> lockRoots(Set<String> codes) {
        Map<String, RiskVariableDefinition> result = new LinkedHashMap<>();
        for (String code : closure(codes)) {
            RiskVariableDefinition row = requiredRoot(code, "SHARE");
            if (row.status() != VariableStatus.ACTIVE) throw failure(VARIABLE_DISABLED, "变量未启用：" + code);
            result.put(code, row);
        }
        return Map.copyOf(result);
    }

    /** 全部成员仍受一千条容量限制，不展开为规则内联 literal。 */
    public List<Member> collection(Entry entry) {
        return collection(entry, false);
    }

    /** 先持根共享锁，再当前读成员；规则幂等事务可能已建立较早的一致性读取视图。 */
    public List<Member> selectedCollection(Entry entry) {
        return collection(entry, true);
    }

    /** 同一根版本的集合不允许被拆成混合批次。 */
    List<Member> collection(Entry entry, boolean currentRead) {
        requireCollection(entry);
        var rows = memberRepository.members(entry.definition().variableCode(), null, 1001, 0, currentRead);
        if (rows.size() > 1000) { throw new ErrorException("集合成员超过批准容量"); }
        return rows.stream().map(r -> RiskVariablePersistenceConverter.member(entry.definition().valueType(), r)).toList();
    }

    /** 历史缺失定义只产生展示问题，不改变保存结构或 Hash。 */
    public Catalog currentValues(Set<String> codes, Instant valuesAsOf) {
        Map<String, View> values = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        Set<String> pending = new TreeSet<>(codes);
        Set<String> read = new HashSet<>();
        while (!pending.isEmpty()) {
            String code = pending.iterator().next(); pending.remove(code);
            if (!read.add(code)) { continue; }
            readCurrentValue(code, values, issues, pending);
        }
        return new Catalog(Map.copyOf(values), List.copyOf(issues), valuesAsOf);
    }

    /** 数据问题作为当前投影问题显式报告，普通系统异常仍向上传播。 */
    void readCurrentValue(String code, Map<String, View> values, List<Issue> issues, Set<String> pending) {
        try {
            Entry entry = required(code, null);
            View view = views(List.of(entry)).getFirst(); values.put(code, view);
            pending.addAll(dependencies(view.entry().definition()));
            if (view.entry().status() != VariableStatus.ACTIVE) { issues.add(new Issue(code, "VARIABLE_DISABLED", "当前定义未启用")); }
        } catch (RiskVariableException failure) {
            issues.add(new Issue(code, failure.reason().getCode(), failure.getMessage()));
        } catch (ErrorException failure) {
            issues.add(new Issue(code, "VARIABLE_DATA_INVALID", "变量当前内容损坏，请联系管理员"));
        }
    }

    /** 查询原始根并验证完整性；WRITE 必须先于删除事务内任何一致性读。 */
    public RiskVariableDefinition requiredRoot(String code, String lock) {
        VariableDefinitionValidator.code(code);
        RiskVariableDefinition row = repository.find(code, lock);
        if (row == null) throw failure(VARIABLE_NOT_FOUND, "变量不存在：" + code);
        row.verifyIntegrity();
        return row;
    }

    /** 领域只读消费者取得验证后的最小投影。 */
    Entry required(String code, String lock) {
        return RiskVariablePersistenceConverter.entry(requiredRoot(code, lock));
    }

    /** 批量查询逐根校验，不把损坏内容降级为空值。 */
    Entry verified(RiskVariableDefinition row) {
        row.verifyIntegrity();
        return RiskVariablePersistenceConverter.entry(row);
    }

    /** 批量目录只需两个成员查询，不附加逐行引用统计。 */
    List<View> views(List<Entry> entries) {
        List<String> collections = entries.stream().filter(e -> e.definition().sourceType() == VariableSource.COLLECTION)
            .map(e -> e.definition().variableCode()).toList();
        Map<String, Long> counts = memberRepository.memberCounts(collections).stream().collect(Collectors.toMap(
            r -> r.variableCode(), r -> r.memberCount()));
        var previews = memberRepository.previewMembers(collections, 3).stream().collect(Collectors.groupingBy(r -> r.variableCode()));
        return entries.stream().map(e -> new View(e, e.definition().sourceType() == VariableSource.COLLECTION
            ? counts.getOrDefault(e.definition().variableCode(), 0L) : null,
            previews.getOrDefault(e.definition().variableCode(), List.of()).stream().map(r ->
                RiskVariablePersistenceConverter.member(e.definition().valueType(), r)).toList(), null)).toList();
    }

    /** 当前详情的可选值预览有一百项上限，超过时通过成员分页继续读取。 */
    AllowedValues allowedValues(Definition definition) {
        if (definition.allowedValuesVariableCode() == null) { return null; }
        Entry options = required(definition.allowedValuesVariableCode(), null);
        List<Member> members = collection(options);
        return new AllowedValues(options.definition().variableCode(), options.variableName(), options.version(), members.size(),
            members.stream().limit(100).toList());
    }

    /** 只收集真实依赖，按固定结构闭包排序；自依赖或环形数据报完整性错误。 */
    Set<String> closure(Set<String> codes) {
        Set<String> result = new TreeSet<>();
        var pending = new ArrayDeque<>(codes);
        while (!pending.isEmpty()) {
            String code = pending.removeFirst();
            if (!result.add(code)) { continue; }
            Entry entry = required(code, null);
            Set<String> children = dependencies(entry.definition());
            if (children.contains(code)) { throw new ErrorException("变量不能依赖自身"); }
            pending.addAll(children);
        }
        return result;
    }

    /** 集合身份与墓碑状态由服务端独立检查。 */
    void requireCollection(Entry entry) {
        if (entry.status() == VariableStatus.DELETED) { throw failure(VARIABLE_NOT_FOUND, "变量已删除"); }
        if (entry.definition().sourceType() != VariableSource.COLLECTION) { throw failure(VARIABLE_TYPE_MISMATCH, "该变量不是集合"); }
    }


    /** 依赖根已共享锁定，选项成员当前读必须与该根版本一致。 */
    public List<RiskVariableMember> optionMembers(RiskVariableDefinition root, Map<String, RiskVariableDefinition> dependencies) {
        if (root.allowedValuesVariableCode() == null) return List.of();
        RiskVariableDefinition options = dependencies.get(root.allowedValuesVariableCode());
        if (options == null) throw new ErrorException("已锁定的可选值依赖缺失");
        options.verifyCollection();
        var rows = memberRepository.members(options.variableCode(), null, 1001, 0, true);
        if (rows.size() > 1000) throw new ErrorException("集合成员超过批准容量");
        rows.forEach(row -> RiskVariablePersistenceConverter.member(options.valueType(), row));
        return rows;
    }
}
