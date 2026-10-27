package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_onboarding_schema_field 只读 SQL。 */
public interface OnboardingSchemaFieldMapper {
    /** 多个版本的全部字段。 */
    List<OnboardingSchemaField> findBySchemas(@Param("schemaIds") Collection<Long> schemaIds);
}
