package com.paytech.client.contract.api.onboarding.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 提交前核对结果；与正式提交使用同一套需求计算。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCheckResponseBody {
    /** 是否可以提交；errors 为空时为 true。 */
    private boolean ready;
    /** 阻止提交的需求级错误，按主体与 Schema 顺序排列。 */
    private List<SubmissionErrorResponseBody> errors;
}
