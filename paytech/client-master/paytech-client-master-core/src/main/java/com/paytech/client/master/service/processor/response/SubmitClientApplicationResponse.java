package com.paytech.client.master.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** C14 内部响应。 */
@Getter
@AllArgsConstructor
public class SubmitClientApplicationResponse {
    /** 申请 ID。 */
    private final long applicationId;
    /** 当前提交编号。 */
    private final String submissionId;
    /** 申请当前状态代码。 */
    private final String status;
    /** 是否本次新建提交；已提交过为 false。 */
    private final boolean created;
}
