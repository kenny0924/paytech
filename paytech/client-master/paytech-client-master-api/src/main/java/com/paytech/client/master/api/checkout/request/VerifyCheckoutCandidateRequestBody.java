package com.paytech.client.master.api.checkout.request;

import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody.RootVersions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 候选复核输入：首次查询条件、首次根版本与选定候选身份。 */
@Getter
@Setter
public class VerifyCheckoutCandidateRequestBody {
    /** 首次候选查询的完整条件，必须原样回传。 */
    @Valid
    @NotNull
    private CheckoutCandidatesRequestBody query;

    /** 首次候选查询返回的根版本，必须原样回传。 */
    @Valid
    @NotNull
    private RootVersions rootVersions;

    /** 选定候选的不可替代授权身份。 */
    @Valid
    @NotNull
    private SelectedCandidate candidate;

    /** 选定候选；同 OE 的后继 Affiliation、Scope 或来源关系不能代替原授权。 */
    @Getter
    @Setter
    public static class SelectedCandidate {
        /** 选定 OE 编号。 */
        @NotBlank
        private String entityNo;

        /** 选定 Client/OE 关联 ID。 */
        @NotBlank
        private String affiliationId;

        /** 关联优先级。 */
        @NotNull
        private Integer priorityNo;

        /** 批准的准确 Scope ID。 */
        @NotBlank
        private String scopeId;

        /** 来源关系 ID。 */
        @NotBlank
        private String sourceRelationId;

        /** 首次查询返回的候选摘要，覆盖候选使用的全部主档事实。 */
        @NotBlank
        private String candidateDigest;
    }
}
