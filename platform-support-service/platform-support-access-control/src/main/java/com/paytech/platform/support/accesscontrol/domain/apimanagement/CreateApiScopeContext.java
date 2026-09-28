package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已通过创建校验的完整目录对象。 */
@Getter
@AllArgsConstructor
public class CreateApiScopeContext {

    /** 本次待插入的目录对象。 */
    private final ApiScope scope;
}
