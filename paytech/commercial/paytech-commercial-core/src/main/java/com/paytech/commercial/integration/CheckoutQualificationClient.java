package com.paytech.commercial.integration;

/** 新鲜资格的待接入边界；不能用 CCT 入驻历史或请求参数代替当前 Risk 权威。 */
public interface CheckoutQualificationClient {
    /** Client 级拒绝必须结束整次查询；技术失败必须抛出，不允许返回 false。 */
    void requireClientEligible(long clientId);
    /** false 仅表示此准确 OE/Partner 的明确资格不满足；客户端拒绝仍以异常终止。 */
    boolean isCandidateEligible(String entityNo, Long localPartnerId);
}
