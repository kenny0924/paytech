package com.paytech.risk.decision.domainservice.service.impl;

import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domainservice.service.RiskVariableService;
import com.paytech.risk.decision.domainservice.query.RiskVariableReader;
import java.time.Instant;
import java.util.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/** 规则与流程复用的变量只读能力，复用同一组本地读取算法。 */
@Service
@AllArgsConstructor
public class RiskVariableServiceImpl implements RiskVariableService {
    private final RiskVariableReader reader;
    /** 旧字段对照来自系统定义，不把工程合同转成变量名单。 */
    @Override public List<Entry> systemDefinitions(String pointCode) {
        return reader.systemDefinitions(pointCode);
    }

    /** 在调用者一致性快照内查询目录及成员预览。 */
    @Override public Page list(Query query, Instant valuesAsOf) {
        return reader.list(query, valuesAsOf);
    }

    /** 读取当前详情，包括停用/墓碑及选项集合预览。 */
    @Override public View detail(String code) {
        return reader.detail(code);
    }

    /** 同一快照验证翻页根版本并读取成员。 */
    @Override public MemberPage members(String code, String pattern, int page, int size, Long expectedVersion, Instant valuesAsOf) {
        return reader.members(code, pattern, page, size, expectedVersion, valuesAsOf);
    }

    /** 新配置保存时按编码排序共享锁定变量及依赖，返回真实当前定义。 */
    @Override public Map<String, Entry> lockDefinitions(Set<String> codes) {
        return reader.lockDefinitions(codes);
    }

    /** 运行期不加锁读取变量及依赖的当前定义。 */
    @Override public Map<String, Entry> definitions(Set<String> codes) {
        return reader.definitions(codes);
    }

    /** 在现有快照/锁保护内读取集合的完整有界当前成员。 */
    @Override public List<Member> collection(Entry entry) {
        return reader.collection(entry);
    }

    /** 已持有变量根共享锁后，以当前读获取同一根版本的成员，避免旧 MVCC 快照。 */
    @Override public List<Member> selectedCollection(Entry entry) {
        return reader.selectedCollection(entry);
    }

    /** 当前值的历史展示投影不改变原结构或内容摘要。 */
    @Override public Catalog currentValues(Set<String> codes, Instant valuesAsOf) {
        return reader.currentValues(codes, valuesAsOf);
    }

}
