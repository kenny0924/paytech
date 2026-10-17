package com.paytech.platform.support.integration.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 渠道或其他出站外部接入方，及本环境出站地址。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Integration {

    /** 接入方内部技术标识。 */
    private Long integrationId;

    /** 接入方编码，大小写敏感，创建后不可修改。 */
    private String integrationCode;

    /** 接入方名称。 */
    private String integrationName;

    /** 说明，未填写时为空。 */
    private String description;

    /** 出站地址 JSON 数组 [{endpointCode, baseUrl}]，由领域层解析。 */
    private String endpoints;

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
