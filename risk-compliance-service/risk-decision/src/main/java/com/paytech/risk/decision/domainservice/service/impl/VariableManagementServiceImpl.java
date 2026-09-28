package com.paytech.risk.decision.domainservice.service.impl;

import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domainservice.repository.*;
import com.paytech.risk.decision.domainservice.service.VariableManagementService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/** 控制变量根与成员的本地原子写入顺序。 */
@Service
@AllArgsConstructor
public class VariableManagementServiceImpl implements VariableManagementService {
    private final RiskVariableRepository repository;
    private final RiskVariableMemberRepository members;

    /** 保存已确认上下文；SQL 行数或后续成员失败直接向上传播。 */
    @Override public void create(CreateVariableContext context) {
        repository.insert(context.getRoot());
    }

    /** 保存已确认上下文；SQL 行数或后续成员失败直接向上传播。 */
    @Override public void update(UpdateVariableContext context) {
        repository.update(context.getRoot(), context.getChange(), context.getActorId(), context.getCommandTime());
    }

    /** 保存已确认上下文；SQL 行数或后续成员失败直接向上传播。 */
    @Override public void replaceMembers(ReplaceVariableMembersContext context) {
        repository.update(context.getRoot(), context.change(), context.getActorId(), context.getCommandTime());
        members.replaceMembers(context.getRoot().variableCode(), context.getMembers());
    }

    /** 保存已确认上下文；SQL 行数或后续成员失败直接向上传播。 */
    @Override public void delete(DeleteVariableContext context) {
        repository.update(context.getRoot(), context.change(), context.getActorId(), context.getCommandTime());
    }

}
