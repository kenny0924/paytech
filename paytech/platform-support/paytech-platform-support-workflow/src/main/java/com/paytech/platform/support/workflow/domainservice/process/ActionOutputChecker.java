package com.paytech.platform.support.workflow.domainservice.process;

import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;
import com.paytech.platform.support.workflow.domainservice.validator.FormValueValidator;

import tools.jackson.databind.JsonNode;

/**
 * 完成动作时的检查（需求 E1 / E7，2026-10-22 起替代动作验证）：只检查必填与平台技术保护，返回请求体字段路径到原因，
 * 空表示通过。
 *
 * <ul>
 * <li>FORM：表单必填、值类型（网关条件依赖）与平台文本长度上限；路径 {@code output.<key>}、文件元素 {@code output.<key>[i]}。</li>
 * <li>FILE_UPLOAD：{@code {"files": [...]}}；配置 required（或存量 minFiles ≥ 1）时至少一个文件；不检查数量与类型，扫描中不挡，已确认未通过扫描的文件报告在 {@code output.files[i]}。</li>
 * <li>PAGE_TASK / MANUAL_CONFIRM：{@code {"note": "..."}} 说明选填，最长 4000；存量 noteRequired 忽略。</li>
 * <li>DATA_QUERY：没有人工提交入口（B3），一律拒绝。</li>
 * <li>文件归属与地址格式由调用方在检查前拒绝；这里只确认文件存在、扫描未被判定为未通过且对象可用。</li>
 * </ul>
 */
final class ActionOutputChecker {

    /** 提交内容在请求体中的属性名。 */
    static final String ROOT = "output";

    /** 确认说明的平台长度上限。 */
    static final int NOTE_LIMIT = 4000;

    private final FormValueValidator forms = new FormValueValidator();

    /**
     * 检查完成动作的提交内容。
     *
     * @param template 版本快照中的模板
     * @param output 提交内容
     * @param files 引用文件的事实，按文件 ID
     * @return 字段路径到原因；通过时为空
     */
    Map<String, String> check(ActionTemplateReference template, JsonNode output, Map<Long, AttachmentFact> files) {
        if (template == null) {
            throw new ErrorException("版本快照缺少动作模板");
        }
        if (output == null || !output.isObject()) {
            return Map.of(ROOT, "必须是 JSON 对象");
        }
        Map<String, String> errors = switch (template.config()) {
            case FormActionConfig form -> forms.fieldErrors(form.form(), output, files);
            case FileUploadActionConfig upload -> fileErrors(upload, output, files);
            case ManualConfirmActionConfig confirm -> noteErrors(output);
            case PageTaskActionConfig page -> noteErrors(output);
            case DataQueryActionConfig query -> throw new BizCheckFailException("系统检查动作由查询结果自动完成，不能手动完成");
        };
        Map<String, String> prefixed = new LinkedHashMap<>();
        errors.forEach((path, reason) -> prefixed.put(ROOT + "." + path, reason));
        return prefixed;
    }

    /**
     * 上传文件动作：只接受 files 属性；需要至少一个时为空即缺失，有文件时检查形态与文件可用。
     *
     * @param config 上传配置
     * @param output 提交内容
     * @param files 引用文件的事实
     * @return 相对路径到原因
     */
    Map<String, String> fileErrors(FileUploadActionConfig config, JsonNode output, Map<Long, AttachmentFact> files) {
        Map<String, String> errors = unknownProperties(output, FileUploadActionConfig.FILES_OUTPUT);
        JsonNode value = output.get(FileUploadActionConfig.FILES_OUTPUT);
        boolean empty = value == null || value.isNull() || value.isArray() && value.isEmpty();
        if (empty) {
            if (config.requiresFiles()) {
                errors.put(FileUploadActionConfig.FILES_OUTPUT, "至少上传一个文件");
            }
            return errors;
        }
        errors.putAll(AttachmentRules.valueErrors(value, FileUploadActionConfig.FILES_OUTPUT, files));
        return errors;
    }

    /**
     * 说明类动作：只接受 note 属性，说明选填，有值时须为文本且不超过平台上限。
     *
     * @param output 提交内容
     * @return 相对路径到原因
     */
    Map<String, String> noteErrors(JsonNode output) {
        Map<String, String> errors = unknownProperties(output, ManualConfirmActionConfig.NOTE_OUTPUT);
        JsonNode note = output.get(ManualConfirmActionConfig.NOTE_OUTPUT);
        if (note == null || note.isNull()) {
            return errors;
        }
        if (!note.isString()) {
            errors.put(ManualConfirmActionConfig.NOTE_OUTPUT, "必须是文本");
        } else if (note.asString().codePointCount(0, note.asString().length()) > NOTE_LIMIT) {
            errors.put(ManualConfirmActionConfig.NOTE_OUTPUT, "超过 " + NOTE_LIMIT + " 个字符");
        }
        return errors;
    }

    /**
     * 未定义的属性。
     *
     * @param output 提交内容
     * @param allowed 唯一允许的属性
     * @return 相对路径到原因
     */
    static Map<String, String> unknownProperties(JsonNode output, String allowed) {
        Map<String, String> errors = new LinkedHashMap<>();
        output.propertyNames().stream().filter(name -> !allowed.equals(name))
            .forEach(name -> errors.put(name, "未定义的字段"));
        return errors;
    }
}
