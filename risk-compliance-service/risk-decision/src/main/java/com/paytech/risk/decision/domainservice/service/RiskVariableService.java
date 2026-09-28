package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 变量库当前事实及保存资格，跨模块调用者只接收领域模型。 */
public interface RiskVariableService {
    /** 旧字段对照来自系统定义，不把工程合同转成变量名单。 */
    List<Entry> systemDefinitions(String pointCode);
    /** 在调用者一致性快照内查询目录及成员预览。 */
    Page list(Query query, Instant valuesAsOf);
    /** 读取当前详情，包括停用/墓碑及选项集合预览。 */
    View detail(String code);
    /** 同一快照验证翻页根版本并读取成员。 */
    MemberPage members(String code, String pattern, int page, int size, Long expectedVersion, Instant valuesAsOf);
    /** 新配置保存时按编码排序共享锁定变量及依赖，返回真实当前定义。 */
    Map<String, Entry> lockDefinitions(Set<String> codes);
    /** 在现有快照/锁保护内读取集合的完整有界当前成员。 */
    List<Member> collection(Entry entry);
    /** 已持有变量根共享锁后，以当前读获取同一根版本的成员，避免旧 MVCC 快照。 */
    List<Member> selectedCollection(Entry entry);
    /** 当前值的历史展示投影不改变原结构或内容摘要。 */
    Catalog currentValues(Set<String> codes, Instant valuesAsOf);
}
