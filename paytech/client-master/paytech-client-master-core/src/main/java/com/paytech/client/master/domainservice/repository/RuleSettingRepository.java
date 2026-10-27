package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.RuleSetting;
import java.util.Collection;
import java.util.List;

/** t_rule_setting 仓储。 */
public interface RuleSettingRepository {
    /**
     * 给定作用对象代码（含 *）的 ACTIVE 规则
     * 
     * @param scopeCodes 作用对象代码
     * @return ACTIVE 规则；空集合不访问数据库
     */
    List<RuleSetting> findActive(Collection<String> scopeCodes);

    /**
     * 全部规则，供运营只读查看
     * 
     * @return 规则列表
     */
    List<RuleSetting> findAll();
}
