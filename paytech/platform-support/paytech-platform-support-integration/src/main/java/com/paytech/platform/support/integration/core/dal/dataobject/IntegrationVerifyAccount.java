package com.paytech.platform.support.integration.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 沙箱验证运行使用的渠道账号材料；按用户决定明文保存非敏感沙箱材料，正式执行不读取。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationVerifyAccount {

    /** 验证账号内部技术标识。 */
    private Long verifyAccountId;

    /** 所属接入方标识。 */
    private Long integrationId;

    /** 接入方内唯一的账号编码，创建后不可修改。 */
    private String accountCode;

    /** 账号名称。 */
    private String accountName;

    /** 渠道编码，流程初始化时与输入中的账号身份核对。 */
    private String channelCode;

    /** 渠道账号标识，流程初始化时核对。 */
    private String channelAccountId;

    /** 经营主体编号，流程初始化时核对。 */
    private String entityNo;

    /** 普通参数 JSON 对象（字符串键值），由领域层解析。 */
    private String parameters;

    /** 沙箱凭据 JSON 对象（字符串键值），执行时包装为受控句柄。 */
    private String credentials;

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
