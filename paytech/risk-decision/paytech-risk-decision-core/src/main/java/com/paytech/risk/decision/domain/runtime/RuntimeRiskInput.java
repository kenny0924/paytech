package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.util.Map;

import com.paytech.risk.control.domain.list.RiskSubjectType;

/**
 * 一次运行期评估的冻结输入；引擎、Feature 计算与影子回放只通过本接口读取
 * <li>各风控点以自己的输入记录实现，字段格式与语义在构造前由评估合同校验。</li>
 */
public interface RuntimeRiskInput {

    /**
     * 输入合同字段的规范值，供引擎按变量来源字段取值
     *
     * @return 字段编码到规范值；金额与数值为去尾零的十进制文本，缺省的可选字段不出现
     */
    Map<String, Object> contractValues();

    /**
     * 主体在本次评估中的规范化主体键，供实时累计与名单命中
     *
     * @param type 主体类型
     * @return 规范化主体键；本次没有该主体时为空
     */
    String subjectKey(RiskSubjectType type);

    /**
     * 本次评估的金额，供累计金额类 Feature
     *
     * @return 金额，币种主单位
     */
    BigDecimal amount();

    /**
     * 金额币种
     *
     * @return ISO 4217 币种
     */
    String currency();
}
