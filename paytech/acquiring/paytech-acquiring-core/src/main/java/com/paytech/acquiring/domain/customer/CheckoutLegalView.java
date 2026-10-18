package com.paytech.acquiring.domain.customer;

import lombok.Getter;
import lombok.Setter;

/** 冻结上下文、法律卖方和消费者合同的三表展示结果，不读取定价及风控事实。 */
@Getter
@Setter
public class CheckoutLegalView {
    /** 冻结上下文主键；存在本行表示上下文已冻结。 */
    private long orderContextId;
    /** 原冻结计税国家。 */
    private String consumerCountryCode;
    /** 原合同语言。 */
    private String locale;
    /** 法律事实主键；关联缺失时为空，待版本重验后报告错误。 */
    private Long orderLegalId;
    /** 个性化消费者合同主键；关联缺失时为空。 */
    private Long consumerContractId;
    /** 个性化销售条款文件。 */
    private Long renderedFileId;
    /** 个性化销售条款文件摘要。 */
    private String renderedContentHash;
    /** 原退款政策文件。 */
    private Long refundPolicyFileId;
    /** 原退款政策文件摘要。 */
    private String refundPolicyContentHash;
    /** 原隐私声明文件。 */
    private Long privacyNoticeFileId;
    /** 原隐私声明文件摘要。 */
    private String privacyNoticeContentHash;
    /** 已冻结法律卖方名称。 */
    private String publicLegalName;
    /** 已冻结卖方注册号，可为空。 */
    private String publicRegistrationNumber;
    /** 已冻结卖方税号，可为空。 */
    private String publicTaxRegistrationNumber;
    /** 已冻结卖方公开地址。 */
    private String publicAddressText;
    /** 已冻结公开支持邮箱，可为空。 */
    private String publicSupportEmail;
    /** 已冻结公开支持电话，可为空。 */
    private String publicSupportPhone;

    /** 关联法律事实与消费者合同齐全后才可使用文件引用。 */
    public boolean hasContract() {
        return orderLegalId != null && consumerContractId != null;
    }
}
