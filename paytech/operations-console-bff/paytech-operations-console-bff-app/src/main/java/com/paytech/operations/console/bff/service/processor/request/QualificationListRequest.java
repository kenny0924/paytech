package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** B8 资格列表的内部请求；筛选与分页原样交给 compliance 校验。 */
@Getter
@AllArgsConstructor
public class QualificationListRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 可选角色类型 */
    private final String roleType;

    /** 可选角色标识；须与 roleType 一起提供 */
    private final String roleId;

    /** 可选资格类型 */
    private final String qualificationType;

    /** 可选存储状态 */
    private final String status;

    /** 可选页码 */
    private final Integer page;

    /** 可选每页条数 */
    private final Integer size;
}
