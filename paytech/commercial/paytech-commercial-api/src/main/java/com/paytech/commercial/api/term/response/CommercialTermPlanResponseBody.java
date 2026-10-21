package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 商业条款方案；ID 为十进制字符串。 */
@Getter
@Setter
public class CommercialTermPlanResponseBody {
    /** 方案 ID */
    private String planId;

    /** 方案编号 */
    private String planCode;

    /** 条款类型 */
    private String termType;

    /** CLIENT 合同 ID；Partner 条款（版本冻结 PARTNER 合同）与模板为空 */
    private String agreementId;

    /** CLIENT 合同 OE 或模板 OE；Partner 条款为空 */
    private String entityNo;

    /** Client Owner；Partner 条款为空 */
    private String clientId;

    /** Local Partner Owner；Client 条款为空 */
    private String localPartnerId;

    /** 方案 Owner 类型：CLIENT、LOCAL_PARTNER、PLATFORM_TEMPLATE */
    private String ownerType;

    /** 模板显示名，仅 PLATFORM_TEMPLATE */
    private String templateName;

    /** 方案状态：DRAFT、ACTIVE、RETIRED */
    private String status;

    /** 方案 BCR 根当前修订版本，下一次写入的 expectedChangeVersion */
    private String changeVersion;
}
