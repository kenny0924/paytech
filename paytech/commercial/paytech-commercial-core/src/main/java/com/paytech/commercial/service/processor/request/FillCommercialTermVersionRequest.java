package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 从标准价目模板补齐 DRAFT 合同版本的内部请求。 */
@Getter
@AllArgsConstructor
public class FillCommercialTermVersionRequest {
    /** 版本 ID */
    @Positive
    private final long versionId;

    /** 方案根期望版本 */
    @Positive
    private final long expectedChangeVersion;

    /** 操作人账号 ID */
    @Positive
    private final long operatorAccountId;

    /** 模板版本 ID */
    @Positive
    private final long templateVersionId;
}
