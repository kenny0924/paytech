package com.paytech.acquiring.test.dal;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;

import org.apache.ibatis.annotations.*;

import java.time.Instant;

/** 支付首建场景的独立 Fixture、故障和只读检查。 */
@Mapper
public interface CheckoutPaymentTestMapper {
    /** 本例准备事实，直接 INSERT。 */
    @Insert(
"""
INSERT INTO t_order_context (order_context_id,order_id,commercial_context_time,affiliation_id,entity_scope_id,
 entity_source_relation_id,client_pricing_version_id,partner_share_version_id,platform_charge_version_id,
 platform_service_party_id,risk_checkout_assessment_id,finance_tax_snapshot_id,finance_tax_snapshot_hash,
 consumer_country_code,locale,create_time) VALUES
 (#{orderContextId},#{orderId},#{commercialContextTime},#{affiliationId},#{entityScopeId},#{entitySourceRelationId},
 #{clientPricingVersionId},#{partnerShareVersionId},#{platformChargeVersionId},#{platformServicePartyId},
 #{riskCheckoutAssessmentId},#{financeTaxSnapshotId},#{financeTaxSnapshotHash},#{consumerCountryCode},#{locale},#{presentedAt})
""")
    void insertContext(FrozenCheckoutRecord row);

    /** 本例准备事实，直接 INSERT。 */
    @Insert(
"""
INSERT INTO t_order_legal (order_legal_id,order_context_id,client_agreement_id,partner_agreement_id,
 partner_agreement_operating_entity_id,consumer_terms_document_id,consumer_terms_file_id,consumer_terms_content_hash,
 refund_policy_document_id,refund_policy_file_id,refund_policy_content_hash,privacy_notice_document_id,
 privacy_notice_file_id,privacy_notice_content_hash,seller_disclosure_id,public_legal_name,public_registration_number,
 public_tax_registration_number,public_address_text,public_support_email,public_support_phone,create_time) VALUES
 (#{orderLegalId},#{orderContextId},#{clientAgreementId},#{partnerAgreementId},#{partnerAgreementOperatingEntityId},
 #{consumerTermsDocumentId},#{consumerTermsFileId},#{consumerTermsContentHash},#{refundPolicyDocumentId},
 #{refundPolicyFileId},#{refundPolicyContentHash},#{privacyNoticeDocumentId},#{privacyNoticeFileId},#{privacyNoticeContentHash},
 #{sellerDisclosureId},#{publicLegalName},#{publicRegistrationNumber},#{publicTaxRegistrationNumber},#{publicAddressText},
 #{publicSupportEmail},#{publicSupportPhone},#{presentedAt})
""")
    void insertLegal(FrozenCheckoutRecord row);

    /** 本例准备事实，直接 INSERT。 */
    @Insert(
"""
INSERT INTO t_consumer_contract (consumer_contract_id,order_id,order_legal_id,rendered_file_id,rendered_content_hash,
 locale,presented_at,create_time) VALUES (#{consumerContractId},#{orderId},#{orderLegalId},#{renderedFileId},
 #{renderedContentHash},#{locale},#{presentedAt},#{presentedAt})
""")
    void insertContract(FrozenCheckoutRecord row);

    @Update(
            "UPDATE t_commerce_order SET"
                + " entity_source_type='PLATFORM',industry_code='RETAIL',mcc_code='5999' WHERE"
                + " order_id=#{orderId}")
    void completeOrder(long orderId);

    @Insert(
            """
            INSERT INTO t_consumer_context (consumer_context_id,order_id,email_vault_token,consumer_profile_vault_token,
              email_domain,billing_country_code,version,create_time,update_time)
            VALUES (#{orderId},#{orderId},'fixture-email','fixture-profile','example.invalid','US',1,#{now},#{now})
            """)
    void insertConsumer(@Param("orderId") long orderId, @Param("now") Instant now);

    @Select("SELECT * FROM t_order_payment_projection WHERE order_id=#{orderId}")
    @ResultMap(
            "com.paytech.acquiring.core.dal.mapper.order.OrderPaymentProjectionMapper.OrderPaymentProjection")
    OrderPaymentProjection payment(long orderId);

    @Select("SELECT COUNT(*) FROM t_consumer_acceptance WHERE consumer_contract_id=#{orderId}")
    int acceptances(long orderId);

    @Select("SELECT COUNT(*) FROM t_checkout_network_evidence WHERE order_id=#{orderId}")
    int evidence(long orderId);

    @Update("UPDATE t_commerce_order SET version=version+1 WHERE order_id=#{orderId}")
    void changeVersion(long orderId);
}
