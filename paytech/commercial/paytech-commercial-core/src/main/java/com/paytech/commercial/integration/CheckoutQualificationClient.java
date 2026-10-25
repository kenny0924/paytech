package com.paytech.commercial.integration;

import java.util.Collection;
import java.util.Map;

import com.paytech.commercial.domain.relationship.QualificationSubject;

/** compliance 角色资格判定端口；只能在数据库事务外调用。 */
public interface CheckoutQualificationClient {
    /**
     * 批量判定主体在当前时间是否具备交易所需的角色资格
     *
     * @param subjects 去重后的主体；为空时不发起调用
     * @return 每个请求主体一条结论，true 为合格
     *         <li>compliance 不可用、返回任何错误或响应不符合合同时抛出 OWNER_UNAVAILABLE（503），不返回部分结果。</li>
     */
    Map<QualificationSubject, Boolean> evaluate(Collection<QualificationSubject> subjects);
}
