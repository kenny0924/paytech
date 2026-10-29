package com.paytech.operations.console.bff.service.model.riskunderwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工作台各输入来源的读取结果
 * <li>每项为 AVAILABLE、UNAVAILABLE（读取失败或超时）或 NOT_APPLICABLE（尽调未批准，无案件可读）。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingSourcesBody {
    /** compliance 尽调案件（CRR、档次、案件版本）。 */
    private String dueDiligenceCase;

    /** client-master 申请资料（业务信息、资料版本）。 */
    private String application;

    /** client-master 经营分类（MCC / 行业与确认时间）。 */
    private String classification;

    /** Platform MCC 参考数据（卡组织高风险标记）。 */
    private String mcc;
}
