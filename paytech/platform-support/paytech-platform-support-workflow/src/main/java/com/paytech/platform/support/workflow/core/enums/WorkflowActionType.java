package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 动作模板类型。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowActionType implements CodeEnum {

    /** 按字段定义填写表单。 */
    FORM("FORM"),

    /** 经 file-document 上传文件。 */
    FILE_UPLOAD("FILE_UPLOAD"),

    /** 跳到控制台页面完成操作后人工确认。 */
    PAGE_TASK("PAGE_TASK"),

    /** 执行人人工确认。 */
    MANUAL_CONFIRM("MANUAL_CONFIRM"),

    /** 数据查询验证；本期只预留，不能被发布的流程引用。 */
    DATA_QUERY("DATA_QUERY");

    private final String code;
}
