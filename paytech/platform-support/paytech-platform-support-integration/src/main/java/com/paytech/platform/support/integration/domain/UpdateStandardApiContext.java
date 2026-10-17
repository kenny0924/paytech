package com.paytech.platform.support.integration.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 标准 API 的整体修改：主表按原版本更新，字段整组替换。 */
@Getter
@AllArgsConstructor
public class UpdateStandardApiContext {

    /** 已锁定并核对版本的标准 API，保存成功后同步新版本。 */
    private final StandardApi standardApi;

    /** 新名称。 */
    private final String name;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 新的请求与响应字段。 */
    private final List<ApiField> fields;

    /** 操作账号。 */
    private final long updatedBy;

    /** 本次保存时间。 */
    private final Instant updateTime;
}
