package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.RuleSetting;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_rule_setting 只读 SQL。 */
public interface RuleSettingMapper {
    /** 给定作用对象代码的 ACTIVE 规则。 */
    List<RuleSetting> findActive(@Param("scopeCodes") Collection<String> scopeCodes);

    /** 全部规则。 */
    List<RuleSetting> findAll();
}
