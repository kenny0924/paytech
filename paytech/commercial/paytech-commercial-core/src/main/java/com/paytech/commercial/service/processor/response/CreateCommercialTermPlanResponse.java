package com.paytech.commercial.service.processor.response;

import com.paytech.commercial.domain.CommercialTermVersionView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建方案的结果：方案视图与是否本次新建（同号同内容重放为 false）。 */
@Getter
@AllArgsConstructor
public class CreateCommercialTermPlanResponse {
    /** 方案与根版本 */
    private final CommercialTermVersionView view;

    /** 本次新建 */
    private final boolean created;
}
