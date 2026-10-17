package com.paytech.client.contract.api.onboarding.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 提交前核对结果；与正式提交使用同一套检查。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCheckResponseBody {
    /** 是否可以提交；issues 为空时为 true。 */
    private boolean ready;
    /** 逐项问题，按主体和表单区块排列。 */
    private List<SubmissionIssueResponseBody> issues;
}
