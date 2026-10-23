package com.paytech.platform.support.workflow.domain.action;

import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * FILE_UPLOAD 动作配置：输出 {@code <actionKey>.files}（文件地址列表）。
 *
 * <p>2026-10-22 起只保留「至少上传一个」：{@code required} 为 true，或存量配置 {@code minFiles >= 1}，视为必须上传；
 * {@code minFiles / maxFiles / mimeTypes} 仍按结构接受以兼容已发布快照，但不再校验文件数量与类型。</p>
 *
 * @param minFiles 存量的最少文件数；只用于判断兼容的「至少一个」
 * @param maxFiles 存量的最多文件数；忽略
 * @param mimeTypes 存量的允许 MIME 类型；忽略
 * @param instruction 给执行人的说明；可能为空
 * @param required 是否至少上传一个文件
 */
public record FileUploadActionConfig(Integer minFiles, Integer maxFiles, List<String> mimeTypes, String instruction,
    boolean required) implements ActionConfig {

    /** 输出变量的属性名。 */
    public static final String FILES_OUTPUT = "files";

    /** 复制列表，保持不可变。 */
    public FileUploadActionConfig {
        mimeTypes = List.copyOf(mimeTypes);
    }

    /**
     * 完成时是否至少需要一个文件：显式 required，或存量 minFiles 不小于 1。
     *
     * @return 需要至少一个文件返回 true
     */
    public boolean requiresFiles() {
        return required || minFiles != null && minFiles >= 1;
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
