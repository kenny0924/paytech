package com.paytech.operations.console.bff.service.model.qualificationadmin.response;

import com.paytech.compliance.api.response.QualificationResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** B8 资格列表的一行：compliance 资格原样保留，补充 client-master 主体名称。 */
@Getter
@AllArgsConstructor
public class QualificationListItemResponseBody {

    /** compliance 角色资格，字段与 Owner 合同一致 */
    private final QualificationResponseBody qualification;

    /**
     * 主体展示名称
     * <li>规则同主体核对的 displayName；client-master 不可用、超时或主体不存在时为 null，页面只显示 roleId。</li>
     */
    private final String subjectName;
}
