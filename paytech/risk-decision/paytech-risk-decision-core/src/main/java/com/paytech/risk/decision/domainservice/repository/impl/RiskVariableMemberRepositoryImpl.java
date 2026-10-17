package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.core.dal.mapper.RiskVariableMapper;
import com.paytech.risk.decision.domainservice.repository.RiskVariableMemberRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 集合成员表读写，完整替换加入调用方的同一事务。 */
@Repository
@AllArgsConstructor
public class RiskVariableMemberRepositoryImpl implements RiskVariableMemberRepository {
    private final RiskVariableMapper mapper;
    /** 查询单个集合的有界成员页。 */
    @Override public List<RiskVariableMember> members(String code, String pattern, int limit, int offset, boolean currentRead) {
        return mapper.members(code, pattern, limit, offset, currentRead);
    }
    /** 统计同筛选范围成员。 */
    @Override public long memberCount(String code, String pattern) { return mapper.memberCount(code, pattern); }
    /** 批量读取各集合的有限预览，空编码集合不执行 SQL。 */
    @Override public List<RiskVariableMember> previewMembers(List<String> codes, int limit) {
        return codes.isEmpty() ? List.of() : mapper.previewMembers(codes, limit);
    }
    /** 批量返回当前成员数，空列表不产生无效 IN 条件。 */
    @Override public List<VariableMemberCount> memberCounts(List<String> codes) {
        return codes.isEmpty() ? List.of() : mapper.memberCounts(codes);
    }
    /** 在根事务内先清除再写入，任何异常由原事务全部回滚。 */
    @Override public void replaceMembers(String code, List<RiskVariableMember> members) {
        mapper.deleteMembers(code);
        for (RiskVariableMember member : members) {
            if (mapper.insertMember(member) != 1) { throw new ErrorException("集合成员写入行数错误"); }
        }
    }
}
