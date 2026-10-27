package com.paytech.client.master.domain.onboardingadmin;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;

/**
 * 运营只读查看的一个 Schema 版本
 *
 * @param schema 版本
 * @param fields 字段，按展示顺序
 */
public record SchemaAdminDetail(OnboardingSchema schema, List<OnboardingSchemaField> fields) {
}
