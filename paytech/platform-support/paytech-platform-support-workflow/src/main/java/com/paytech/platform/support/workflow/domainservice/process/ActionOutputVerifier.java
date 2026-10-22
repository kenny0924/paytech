package com.paytech.platform.support.workflow.domainservice.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domainservice.validator.FormValueValidator;

import tools.jackson.databind.JsonNode;

/**
 * 动作提交的同步验证（规则 B2，技术方案 3.3）：返回全部未通过原因，空列表表示通过。
 *
 * <ul>
 * <li>FORM：按字段规则校验 {@code {字段: 值}}。</li>
 * <li>PAGE_TASK / MANUAL_CONFIRM：执行人确认，提交 {@code {"note": "..."}}；MANUAL_CONFIRM 配置要求时说明必填，说明最长 4000。</li>
 * <li>FILE_UPLOAD：提交 {@code {"files": ["/files/v1/{fileId}", ...]}}（文件地址），按配置校验数量、类型，文件须扫描通过；扫描中记为未通过，
 * 扫描结束后重新验证。FORM 的 FILE 字段同样规则。文件归属由调用方在保存前拒绝。</li>
 * </ul>
 */
final class ActionOutputVerifier {

    /** 确认说明最大长度。 */
    private static final int NOTE_LIMIT = 4000;

    private final FormValueValidator forms = new FormValueValidator();

    /**
     * 验证提交内容。
     *
     * @param template 版本快照中的模板
     * @param output 提交内容
     * @param files 引用文件的事实，按文件 ID
     * @return 未通过原因
     */
    List<String> verify(ActionTemplateReference template, JsonNode output, Map<Long, AttachmentFact> files) {
        if (template == null) {
            throw new ErrorException("版本快照缺少动作模板");
        }
        if (output == null || !output.isObject()) {
            return List.of("提交内容必须是 JSON 对象");
        }
        return switch (template.config()) {
            case FormActionConfig form -> forms.validate(form.form(), output, files);
            case FileUploadActionConfig upload -> fileProblems(upload, output, files);
            case ManualConfirmActionConfig confirm -> noteProblems(output, confirm.noteRequired());
            case PageTaskActionConfig page -> noteProblems(output, false);
            default -> throw new BizCheckFailException("该类型动作的提交暂未开放：" + template.actionType().getCode());
        };
    }

    /**
     * 上传文件动作的问题：只接受 files 属性，按配置校验文件。
     *
     * @param config 上传配置
     * @param output 提交内容
     * @param files 引用文件的事实
     * @return 问题
     */
    List<String> fileProblems(FileUploadActionConfig config, JsonNode output, Map<Long, AttachmentFact> files) {
        List<String> problems = new ArrayList<>();
        output.propertyNames().stream().filter(name -> !FileUploadActionConfig.FILES_OUTPUT.equals(name))
            .forEach(name -> problems.add("未定义的字段：" + name));
        JsonNode value = output.get(FileUploadActionConfig.FILES_OUTPUT);
        String problem = value == null || value.isNull() ? "必填" : AttachmentRules.valueProblem(value,
            config.minFiles(), config.maxFiles(), config.mimeTypes(), files);
        if (problem != null) {
            problems.add("文件（files）" + problem);
        }
        return problems;
    }

    /**
     * 确认说明的问题。
     *
     * @param output 提交内容
     * @param required 说明是否必填
     * @return 问题
     */
    List<String> noteProblems(JsonNode output, boolean required) {
        List<String> problems = new ArrayList<>();
        output.propertyNames().stream().filter(name -> !Set.of("note").contains(name))
            .forEach(name -> problems.add("未定义的字段：" + name));
        JsonNode note = output.get("note");
        boolean blank = note == null || note.isNull() || note.isString() && WorkflowValueFormats.isBlank(note.asString());
        if (blank) {
            if (required) {
                problems.add("确认说明必填");
            }
            return problems;
        }
        if (!note.isString()) {
            problems.add("确认说明必须是文本");
        } else if (note.asString().length() > NOTE_LIMIT) {
            problems.add("确认说明超过 " + NOTE_LIMIT + " 个字符");
        }
        return problems;
    }
}
