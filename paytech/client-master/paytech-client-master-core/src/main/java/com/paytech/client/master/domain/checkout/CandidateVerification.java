package com.paytech.client.master.domain.checkout;

import java.util.Set;

import com.paytech.client.master.domain.checkout.CheckoutEligibilityContext.EntityEligibility;

import lombok.Value;
import lombok.experimental.Accessors;

/** 候选复核输入：首次查询条件、首次根版本、选定授权与候选摘要。 */
@Value
@Accessors(fluent = true)
public class CandidateVerification {
    /** 首次查询条件。 */
    CheckoutCandidateQuery query;
    /** 首次根版本，元素为 {@link CheckoutEligibilityContext#rootKey} 格式。 */
    Set<String> expectedRoots;
    /** 选定授权。 */
    SelectedAuthorization selected;
    /** 首次返回的候选摘要。 */
    String candidateDigest;

    /** 选定的不可替代授权身份。 */
    @Value
    @Accessors(fluent = true)
    public static class SelectedAuthorization {
        /** OE 编号。 */
        String entityNo;
        /** Client/OE 关联 ID。 */
        long affiliationId;
        /** 关联优先级。 */
        int priorityNo;
        /** 准确 Scope ID。 */
        long scopeId;
        /** 来源关系 ID。 */
        long sourceRelationId;

        /** 同 OE 后继记录不能代替已选授权，即使当前也合法。 */
        boolean sameAuthorization(EntityEligibility current) {
            return entityNo.equals(current.entity().entityNo())
                && current.affiliation().affiliationId() == affiliationId
                && current.affiliation().priorityNo() == priorityNo && current.scope().scopeId() == scopeId
                && current.source().sourceRelationId() == sourceRelationId;
        }
    }
}
