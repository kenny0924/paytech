package com.paytech.platform.support.integration.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Platform 对内提供的标准 API 定义；字段由 ApiField 维护。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StandardApi {

    /** 标准 API 内部技术标识。 */
    private Long standardApiId;

    /** 标准 API 编码，大小写敏感，创建后不可修改。 */
    private String standardApiCode;

    /** 标准 API 名称。 */
    private String standardApiName;

    /** 用途说明，未填写时为空。 */
    private String description;

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
