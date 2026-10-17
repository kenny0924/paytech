package com.paytech.platform.support.integration.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接入方的单次外部协议操作。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationApi {

    /** 渠道原子 API 内部技术标识。 */
    private Long integrationApiId;

    /** 所属接入方标识。 */
    private Long integrationId;

    /** 接入方内唯一的原子 API 编码，创建后不可修改。 */
    private String apiCode;

    /** 原子 API 名称。 */
    private String apiName;

    /** 说明，未填写时为空。 */
    private String description;

    /** 执行定义 JSON，由领域层解析，最多 65536 UTF-8 字节。 */
    private String definitionJson;

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
