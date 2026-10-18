package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新资源已完成唯一编码、权限和完整候选树校验。 */
@Getter
@AllArgsConstructor
public class CreateConsoleResourceContext {
    /** 待插入的本表对象，创建状态固定 DISABLED。 */
    private final ConsoleResource resource;
}
