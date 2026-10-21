package com.paytech.commercial.domain;

import java.util.List;
import java.util.Objects;

import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.*;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;

import lombok.Value;
import lombok.experimental.Accessors;

/** 同一只读快照取得的全部商务原始事实；空列表仅表示对应查询没有返回记录。 */
@Value
@Accessors(fluent = true)
public class CommercialCheckoutFacts {
    /** 适用合同。 */
    List<Agreement> agreements;
    /** Partner 合同覆盖范围。 */
    List<AgreementOperatingEntity> agreementEntities;
    /** 合同参与方和共签证据。 */
    List<AgreementParty> agreementParties;
    /** 合同签署文件。 */
    List<AgreementDocumentRef> agreementDocuments;
    /** 条款计划。 */
    List<CommercialTermPlan> termPlans;
    /** 不可变条款版本。 */
    List<CommercialTermVersion> termVersions;
    /** 准确 OE 定价。 */
    List<CommercialTermPrice> termPrices;
    /** 定价组件。 */
    List<CommercialTermComponent> termComponents;
    /** 定价阶梯。 */
    List<CommercialTermComponentBand> termBands;
    /** 支付方式 Profile。 */
    List<PricingProfile> methodProfiles;
    /** 合同业务根。 */
    List<BusinessChangeRecord> roots;
    /** CLIENT 合同订阅的产品。 */
    List<AgreementProduct> agreementProducts;
    /** CLIENT 合同在各 OE 下的开通项。 */
    List<AgreementCapability> agreementCapabilities;
    /** 上述开通项的接受条件行。 */
    List<AgreementCapabilityLimit> capabilityLimits;
    /** 上述开通项引用的可开通项目录。 */
    List<ProductCapability> productCapabilities;

    /** 支付方式 Profile 的条件行（判断开通项是否有生效价格）。 */
    List<PricingProfileCond> profileConditions;

    /** 合同参与方引用的资料 ID，去重升序；签署校验据此向 client-master 读取资料。 */
    public List<Long> partyProfileIds() {
        return agreementParties.stream().map(AgreementParty::partyProfileId).filter(Objects::nonNull).distinct()
            .sorted().toList();
    }
}
