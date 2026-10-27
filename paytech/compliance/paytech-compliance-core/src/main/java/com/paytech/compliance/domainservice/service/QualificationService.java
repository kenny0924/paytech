package com.paytech.compliance.domainservice.service;

import com.paytech.compliance.domain.QualificationChangeContext;

/** 角色资格手工登记与撤销的本地保存；不访问外部服务。 */
public interface QualificationService {
    /**
     * 在一个本地事务中新增决定、新建或切换投影、追加状态变更并登记 QualificationChanged 发布任务
     * <li>投影版本已变化或同一资格已被建立时抛出 QUALIFICATION_CHANGED 并整体回滚。</li>
     * <li>成功后上下文中的投影已同步为变更后的状态与版本。</li>
     */
    void saveChange(QualificationChangeContext context);

    /**
     * 只做投影切换、状态变更与发布任务登记，决定由调用方已插入
     * <li>必须在调用方开启的事务内执行（案件决定与资格投影原子提交）；本方法不自行开启事务。</li>
     * <li>投影版本已变化抛出 QUALIFICATION_CHANGED，由调用方事务整体回滚。</li>
     */
    void applyProjection(QualificationChangeContext context);
}
