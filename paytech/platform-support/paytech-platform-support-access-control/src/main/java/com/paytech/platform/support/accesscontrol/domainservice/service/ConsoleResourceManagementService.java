package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ChangeConsoleResourceStatusContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.CreateConsoleResourceContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.UpdateConsoleResourceContext;

/** 已验证的菜单资源本地保存，不查询关系或调用其他 Service。 */
public interface ConsoleResourceManagementService {
    /** 保存新资源，保持初始停用。 */
    void create(CreateConsoleResourceContext context);

    /** 保存编辑内容并同步 Context 中的原对象。 */
    void update(UpdateConsoleResourceContext context);

    /** 保存单条状态，保持子项和引用。 */
    void changeStatus(ChangeConsoleResourceStatusContext context);
}
