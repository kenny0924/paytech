package com.paytech.channel.connector.protocol.core.dal.dataobject;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接入方的沙箱验证用例：使用的验证账号与有序步骤。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationVerifyCase {

    /** 验证用例内部技术标识。 */
    private Long verifyCaseId;

    /** 所属接入方标识。 */
    private Long integrationId;

    /** 接入方内唯一的用例编码，创建后不可修改。 */
    private String caseCode;

    /** 用例名称。 */
    private String caseName;

    /** 说明，未填写时为空。 */
    private String description;

    /** 运行时使用的验证账号标识。 */
    private Long verifyAccountId;

    /** 有序步骤 JSON 数组，由领域层解析。 */
    private String stepsJson;

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
