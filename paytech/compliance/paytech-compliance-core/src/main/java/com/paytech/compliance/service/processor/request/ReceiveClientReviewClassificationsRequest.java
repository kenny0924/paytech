package com.paytech.compliance.service.processor.request;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** I02 CCT 已确认的同一任务分类。 */
@Getter
@AllArgsConstructor
public class ReceiveClientReviewClassificationsRequest {
    /** 主任务 ID。 */
    @Positive
    private final long caseId;
    /** CCT 申请 ID。 */
    @Positive
    private final long applicationId;
    /** 确认人 Account ID。 */
    @Positive
    private final long confirmedBy;
    /** 来源提交编号。 */
    @NotBlank
    private final String submissionId;
    /** 确认时间。 */
    @NotNull
    private final Instant confirmedAt;
    /** 分类集合。 */
    @NotEmpty
    @Size(max = 20)
    private final List<@Valid Item> classifications;

    /** 一条分类。 */
    @Getter
    @AllArgsConstructor
    public static class Item {
        /** 是否主要分类。 */
        private final boolean primary;
        /** 四位 MCC。 */
        @NotBlank
        @Pattern(regexp = "^[0-9]{4}$")
        private final String mccCode;
        /** 行业代码。 */
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
        private final String industryCode;
    }
}
