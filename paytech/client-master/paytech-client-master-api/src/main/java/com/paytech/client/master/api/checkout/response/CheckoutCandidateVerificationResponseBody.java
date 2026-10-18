package com.paytech.client.master.api.checkout.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 候选复核结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutCandidateVerificationResponseBody {
    /** true 表示根版本、选定授权与候选事实均未变化。 */
    private boolean unchanged;

    /**
     * 变化项，unchanged 为 true 时为空数组。
     * <li>根变化为 {@code <RECORD_TYPE>:<recordId>}；选定候选不再可用为 {@code CANDIDATE:<entityNo>}。</li>
     */
    private List<String> changedRoots;
}
