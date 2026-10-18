package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 目录原对象及本次待保存的编辑内容。 */
@Getter
@AllArgsConstructor
public class UpdateApiScopeContext {

    /** 已锁定并核对版本的原目录对象。 */
    private final ApiScope scope;

    /** 本次编辑后的名称。 */
    private final String displayName;

    /** 本次编辑后的说明，可空。 */
    private final String description;

    /** 本次更新的 UTC 微秒时点。 */
    private final Instant updateTime;
}
