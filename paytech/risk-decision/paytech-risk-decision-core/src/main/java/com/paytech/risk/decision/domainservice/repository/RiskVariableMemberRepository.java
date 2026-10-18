package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.*;
import java.util.List;

/** 集合成员表仓储，参与调用方的根事务。 */
public interface RiskVariableMemberRepository {
    /** 查询当前成员页或整批校验所需的有界成员。 */
    List<RiskVariableMember> members(String code, String pattern, int limit, int offset, boolean currentRead);
    /** 统计相同条件的成员。 */
    long memberCount(String code, String pattern);
    /** 批量预览用于列表，避免逐变量查询。 */
    List<RiskVariableMember> previewMembers(List<String> codes, int limit);
    /** 批量计数用于列表，空集合补零由领域层处理。 */
    List<VariableMemberCount> memberCounts(List<String> codes);
    /** 根锁及 CAS 由调用方持有，成员与根在同事务整体提交。 */
    void replaceMembers(String code, List<RiskVariableMember> members);
}
