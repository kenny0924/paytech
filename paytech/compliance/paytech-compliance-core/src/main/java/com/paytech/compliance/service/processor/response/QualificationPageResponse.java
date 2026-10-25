package com.paytech.compliance.service.processor.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 角色资格分页结果。 */
@Getter
@AllArgsConstructor
public class QualificationPageResponse {
    /** 当前页资格。 */
    private final List<QualificationResponse> items;
    /** 页码。 */
    private final int page;
    /** 每页条数。 */
    private final int size;
    /** 总数。 */
    private final long total;
}
