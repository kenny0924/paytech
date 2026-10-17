package com.paytech.platform.support.integration.domain;

import java.time.Instant;

import com.paytech.platform.support.integration.core.dal.dataobject.Integration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接入方修改：按原版本更新名称、说明与出站地址。 */
@Getter
@AllArgsConstructor
public class UpdateIntegrationContext {

    /** 已锁定并核对版本的接入方，保存成功后同步新版本。 */
    private final Integration integration;

    /** 新名称。 */
    private final String name;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 新出站地址的规范化 JSON。 */
    private final String endpointsJson;

    /** 操作账号。 */
    private final long updatedBy;

    /** 本次保存时间。 */
    private final Instant updateTime;
}
