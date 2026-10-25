package com.paytech.compliance.service.processor.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 批量资格判定结果；结果顺序与请求中主体首次出现的顺序一致。 */
@Getter
@AllArgsConstructor
public class QualificationEvaluationResponse {
    /** 判定时间。 */
    private final Instant evaluatedAt;
    /** 每个主体一条结果。 */
    private final List<Result> results;

    /** 一个主体的判定结果。 */
    @Getter
    @AllArgsConstructor
    public static class Result {
        /** 角色类型。 */
        private final String roleType;
        /** 角色标识。 */
        private final String roleId;
        /** 实时状态。 */
        private final String status;
        /** 选中资格的类型；未登记为空。 */
        private final String qualificationType;
        /** 选中资格 ID；未登记为空。 */
        private final Long qualificationId;
        /** 有效开始时间；未登记为空。 */
        private final Instant validFrom;
        /** 有效结束时间；未登记或没有预定结束时间为空。 */
        private final Instant validTo;
    }
}
