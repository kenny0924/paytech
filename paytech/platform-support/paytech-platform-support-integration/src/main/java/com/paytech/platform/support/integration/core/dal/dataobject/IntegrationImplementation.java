package com.paytech.platform.support.integration.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接入方对某个标准 API 的当前实现（表 t_integration_standard_api）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationImplementation {

    /** 渠道标准 API 实现内部技术标识。 */
    private Long integrationStandardApiId;

    /** 实现该标准 API 的接入方标识。 */
    private Long integrationId;

    /** 被实现的标准 API 标识。 */
    private Long standardApiId;

    /** 流程定义 JSON，由领域层解析，最多 65536 UTF-8 字节。 */
    private String flowDefinitionJson;

    /** Operations 保存时按版本条件更新的并发版本，初始为 1。 */
    private int version;

    /** 创建该配置的 Operations 账号标识。 */
    private Long createdBy;

    /** 最后一次修改该配置的 Operations 账号标识。 */
    private Long updatedBy;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
