package com.paytech.platform.support.workflow.domain.action;

import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * FILE_UPLOAD 动作配置：输出 {@code <actionKey>.files}，验证文件属于本实例、扫描已通过且数量与类型符合配置。
 *
 * @param minFiles 最少文件数
 * @param maxFiles 最多文件数
 * @param mimeTypes 允许的 MIME 类型；为空表示不限
 * @param instruction 给执行人的说明；可能为空
 */
public record FileUploadActionConfig(Integer minFiles, Integer maxFiles, List<String> mimeTypes, String instruction)
    implements ActionConfig {

    /** 输出变量的属性名。 */
    public static final String FILES_OUTPUT = "files";

    /** 复制列表，保持不可变。 */
    public FileUploadActionConfig {
        mimeTypes = List.copyOf(mimeTypes);
    }

    /** 本配置固定对应的动作类型。 */
    @Override
    public WorkflowActionType actionType() {
        return WorkflowActionType.FILE_UPLOAD;
    }

    /** 输出上传的文件地址列表。 */
    @Override
    public List<VariableDeclaration> outputs(String actionKey) {
        return List.of(VariableDeclaration.of(actionKey + "." + FILES_OUTPUT, FormFieldType.FILE));
    }

    /** 不接受输入映射。 */
    @Override
    public Set<String> inputKeys() {
        return Set.of();
    }
}
