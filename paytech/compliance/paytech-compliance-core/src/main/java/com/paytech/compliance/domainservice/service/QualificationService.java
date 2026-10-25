package com.paytech.compliance.domainservice.service;

import com.paytech.compliance.domain.QualificationChangeContext;

/** 角色资格手工登记与撤销的本地保存；不访问外部服务。 */
public interface QualificationService {
    /**
     * 在一个本地事务中新增决定、新建或切换投影，并追加状态变更
     * <li>投影版本已变化或同一资格已被建立时抛出 QUALIFICATION_CHANGED 并整体回滚。</li>
     * <li>成功后上下文中的投影已同步为变更后的状态与版本。</li>
     */
    void saveChange(QualificationChangeContext context);
}
