package com.paytech.tax.core.dal.dataobject;

import java.time.Instant;

import com.paytech.tax.api.enums.TaxDocumentKind;

import lombok.Getter;
import lombok.Setter;

/** t_tax_document_sequence：开票主体在一份注册下的号码序列。 */
@Getter
@Setter
public class TaxDocumentSequence {
    /** Snowflake 主键 */
    private Long id;

    /** 开票主体 Party ID */
    private Long issuerPartyId;

    /** 注册 ID */
    private Long registrationId;

    /** 序列代码 */
    private String seriesCode;

    /** 单据类型 */
    private TaxDocumentKind documentKind;

    /** 下一个可分配序号 */
    private Long nextNumber;

    /** 号码格式 */
    private String numberFormat;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
