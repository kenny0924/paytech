package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.rule.CreateRuleSetVersionContext;
import com.paytech.risk.decision.domain.rule.DeleteRuleSetContext;
import com.paytech.risk.decision.domain.rule.FreezeRuleSetVersionContext;

/** 在 Processor 已开启的本地事务中保存完整上下文，不调用其他 Service。 */
public interface RuleSetManagementService {
    /** 追加规则事实、推进管理根，并退役旧草稿。 */
    void create(CreateRuleSetVersionContext context);
    /** 原子冻结规则和可选旧协议，推进管理根。 */
    void freeze(FreezeRuleSetVersionContext context);
    /** 仅保存整体根删除事实。 */
    void delete(DeleteRuleSetContext context);
}
