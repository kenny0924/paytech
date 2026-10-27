package com.paytech.compliance.integration;

import com.paytech.compliance.domain.QualificationChangedFact;

/** 角色资格状态迁移的出站端口；只能在数据库事务外调用。 */
public interface QualificationChangePublisher {
    /**
     * 发布资格状态迁移
     * <li>收到 Broker 确认才返回；未确认、退回或超时抛出运行时异常，由补偿任务重试。</li>
     *
     * @param fact 已提交的资格迁移
     */
    void publish(QualificationChangedFact fact);
}
