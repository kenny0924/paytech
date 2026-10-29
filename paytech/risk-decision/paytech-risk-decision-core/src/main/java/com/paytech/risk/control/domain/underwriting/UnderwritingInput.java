package com.paytech.risk.control.domain.underwriting;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.risk.control.core.enums.FulfillmentType;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.control.core.enums.UnderwritingDueDiligenceTier;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;

import lombok.Data;

/**
 * 承保输入快照：operations-console-bff 从 compliance、client-master 与 Platform 读取后随命令带入
 * <li>提交与自动承保时冻结进版本行；之后来源变化不改已提交记录。</li>
 */
@Data
public class UnderwritingInput {
    /** 快照时 Client 名称。 */
    private String clientDisplayName;

    /** 快照时 Client 编号。 */
    private String clientNo;

    /** 快照时 Client 类型。 */
    private UnderwritingClientType clientType;

    /** 快照时辖区代码。 */
    private String jurisdictionCode;

    /** 尽调 CRR；存量未评为空。 */
    private UnderwritingRiskLevel crr;

    /** 尽调档次；可空。 */
    private UnderwritingDueDiligenceTier ddTier;

    /** 主 MCC；草稿可空。 */
    private String mccCode;

    /** 主行业代码；可空。 */
    private String industryCode;

    /** MCC 是否卡组织高风险。 */
    private boolean networkHighRiskMcc;

    /** 预计月交易金额；可空。 */
    private BigDecimal expectedMonthlyVolume;

    /** 金额币种；有金额时非空。 */
    private String volumeCurrency;

    /** 预计单笔金额；可空。 */
    private BigDecimal expectedTicket;

    /** 交付方式；未采集为空。 */
    private FulfillmentType fulfillmentType;

    /** 预售交付天数；仅 PRE_ORDER。 */
    private Integer fulfillmentDays;

    /** 申报网站。 */
    private String websiteUrl;

    /** 申报退款政策链接。 */
    private String refundPolicyUrl;

    /** 期望支付方式规范化逗号串。 */
    private String expectedPaymentMethods;

    /** 来源尽调案件 ID。 */
    private Long sourceCaseId;

    /** 来源尽调案件版本。 */
    private Long sourceCaseVersion;

    /** 来源入驻申请 ID。 */
    private Long sourceApplicationId;

    /** 来源申请资料版本。 */
    private Long sourceDraftRevision;

    /** 运营确认分类的时间；为空表示未确认。 */
    private Instant sourceClassificationConfirmedTime;

    /** 复核级别：CRR 非空时等于 CRR，存量未评按 MEDIUM。 */
    public UnderwritingRiskLevel reviewLevel() {
        return crr == null ? UnderwritingRiskLevel.MEDIUM : crr;
    }
}
