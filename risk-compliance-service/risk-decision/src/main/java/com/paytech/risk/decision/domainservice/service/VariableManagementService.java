package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.variable.*;

/** 参与 Processor 事务的本地变量写入，不查询关系或调用其他能力。 */
public interface VariableManagementService {
    /** 保存CreateVariable 已确认事实，失败整体回滚。 */
    void create(CreateVariableContext context);
    /** 保存UpdateVariable 已确认事实，失败整体回滚。 */
    void update(UpdateVariableContext context);
    /** 保存ReplaceVariableMembers 已确认事实，失败整体回滚。 */
    void replaceMembers(ReplaceVariableMembersContext context);
    /** 保存DeleteVariable 已确认事实，失败整体回滚。 */
    void delete(DeleteVariableContext context);
}
