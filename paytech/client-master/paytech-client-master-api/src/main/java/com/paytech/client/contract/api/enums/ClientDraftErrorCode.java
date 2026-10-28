package com.paytech.client.contract.api.enums;
import com.paytech.foundation.common.enums.CodeMessageEnum;
/** 安全业务错误，不包含表单值、Token 或其他客户资料。 */
public enum ClientDraftErrorCode implements CodeMessageEnum {
    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", "材料引用不存在或无权访问"),

    DOCUMENT_ALREADY_EXISTS("DOCUMENT_ALREADY_EXISTS", "该材料引用已存在"),

    MATERIAL_REQUIREMENT_INVALID("MATERIAL_REQUIREMENT_INVALID", "材料要求或类型不匹配"),

    MATERIAL_FILE_UNAVAILABLE("MATERIAL_FILE_UNAVAILABLE", "文件尚不可用于关联"),

    MATERIAL_CONTEXT_CHANGED("MATERIAL_CONTEXT_CHANGED", "申请材料上下文已变化，请刷新后重试"),

    INVALID_DRAFT("INVALID_DRAFT", "入驻字段不符合约定"),

    CLIENT_SESSION_INVALID("CLIENT_SESSION_INVALID", "当前客户会话不可用"),

    APPLICATION_NOT_FOUND("APPLICATION_NOT_FOUND", "申请不存在或无权访问"),

    PERSON_NOT_FOUND("PERSON_NOT_FOUND", "人员不存在或无权访问"),

    RELATION_NOT_FOUND("RELATION_NOT_FOUND", "关系不存在或无权访问"),

    PERSON_NOT_EDITABLE("PERSON_NOT_EDITABLE", "人员资料已批准或属于其他申请"),

    RELATION_NOT_EDITABLE("RELATION_NOT_EDITABLE", "关系已生效或不可修改"),

    RELATION_ALREADY_EXISTS("RELATION_ALREADY_EXISTS", "该人员已有相同类型的关系"),

    ORGANIZATION_REQUIRED("ORGANIZATION_REQUIRED", "只有组织申请可以管理人员关系"),

    ACTIVE_APPLICATION_TYPE_MISMATCH("ACTIVE_APPLICATION_TYPE_MISMATCH", "已有另一类型的未完成申请"),

    APPLICATION_NOT_EDITABLE("APPLICATION_NOT_EDITABLE", "当前申请资料不可修改"),

    IDENTITY_CONFLICT("IDENTITY_CONFLICT", "证件号码已被其他主体使用，请核对后联系客服"),

    SUBMISSION_INCOMPLETE("SUBMISSION_INCOMPLETE", "资料或材料尚不完整，请按提交核对结果补全"),

    DRAFT_CONTEXT_INVALID("DRAFT_CONTEXT_INVALID", "申请资料关联不完整"),

    ONBOARDING_DEPENDENCY_UNAVAILABLE("ONBOARDING_DEPENDENCY_UNAVAILABLE", "入驻依赖暂不可用"),

    REVIEW_CASE_NOT_FOUND("REVIEW_CASE_NOT_FOUND", "审核任务不存在或不是申请的当前任务"),

    CLASSIFICATION_INVALID("CLASSIFICATION_INVALID", "分类不符合目录或规则"),

    CLASSIFICATION_CONFIRMED("CLASSIFICATION_CONFIRMED", "本任务分类已确认，不能修改"),

    /** 声明所附条款或告知已更新，termsVersion 与当前版本不符；客户端提示重新阅读后确认。 */
    DECLARATION_DOCUMENT_OUTDATED("DECLARATION_DOCUMENT_OUTDATED", "条款已更新，请重新阅读后确认");
    private final String code;
    private final String message;
    ClientDraftErrorCode(String code, String message) { this.code=code; this.message=message; }
    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}
