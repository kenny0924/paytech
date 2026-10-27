package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import java.util.Collection;
import java.util.List;

/** t_onboarding_schema_field 仓储。 */
public interface OnboardingSchemaFieldRepository {
    /**
     * 多个 Schema 版本的全部字段，按版本与展示顺序
     * 
     * @param schemaIds 版本 ID
     * @return 字段；空集合不访问数据库
     */
    List<OnboardingSchemaField> findBySchemas(Collection<Long> schemaIds);
}
