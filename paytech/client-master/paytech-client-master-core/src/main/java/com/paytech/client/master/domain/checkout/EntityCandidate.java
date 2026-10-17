package com.paytech.client.master.domain.checkout;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ConsumerLegalDocument;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.SellerDisclosure;
import com.paytech.client.master.domain.checkout.CheckoutEligibilityContext.EntityEligibility;

import lombok.Value;
import lombok.experimental.Accessors;

/** 一个通过全部主档条件的 OE 候选及其随行的原始记录。 */
@Value
@Accessors(fluent = true)
public class EntityCandidate {
    /** 原 OE、授权、来源及准确 Scope。 */
    EntityEligibility eligibility;
    /** PARTNER 来源的 Local Partner；其他来源或主档缺失时为空。 */
    LocalPartner partner;
    /** 请求国家与 Locale 下该 OE 的全部消费者法律文件。 */
    List<ConsumerLegalDocument> legalDocuments;
    /** 请求国家与 Locale 下该 OE 的全部卖方披露。 */
    List<SellerDisclosure> sellerDisclosures;
    /** 候选全部主档事实的摘要。 */
    String digest;

    /** 候选 OE 编号。 */
    public String entityNo() {
        return eligibility.entity().entityNo();
    }
}
