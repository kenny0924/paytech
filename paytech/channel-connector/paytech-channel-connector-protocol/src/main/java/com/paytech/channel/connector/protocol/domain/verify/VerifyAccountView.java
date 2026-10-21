package com.paytech.channel.connector.protocol.domain.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 验证账号详情或列表项；列表项不含参数与凭据（为空映射）。 */
@Getter
@AllArgsConstructor
public class VerifyAccountView {

    /** 账号编码。 */
    private final String accountCode;

    /** 名称。 */
    private final String accountName;

    /** 渠道编码。 */
    private final String channelCode;

    /** 渠道账号标识。 */
    private final String channelAccountId;

    /** 经营主体编号。 */
    private final String entityNo;

    /** 普通参数。 */
    private final Map<String, String> parameters;

    /** 沙箱凭据。 */
    private final Map<String, String> credentials;

    /** 引用该账号的用例编码，按编码排序。 */
    private final List<String> referencedCaseCodes;

    /** 版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 最后修改账号。 */
    private final Long updatedBy;
}
