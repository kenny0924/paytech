package com.paytech.platform.support.workflow.domainservice.convert;

import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageParameter;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;

import tools.jackson.databind.JsonNode;

/**
 * 按动作类型解析动作模板 config_json（技术方案 3.3）。
 *
 * <p>DATA_QUERY 只预留：只读取 queryCode 并保留原文，不解析其余结构。</p>
 */
public final class ActionConfigJsonReader {

    private static final String ROOT = "config_json";

    private final FormDefinitionJsonReader formReader = new FormDefinitionJsonReader();

    /**
     * 解析动作模板配置。
     *
     * @param actionType 模板的动作类型
     * @param json config_json 文本
     * @return 对应类型的配置
     * @throws WorkflowJsonFieldException JSON 不合法或结构不符
     */
    public ActionConfig read(WorkflowActionType actionType, String json) {
        JsonNode root = StrictJson.parseObject(json, ROOT);
        return switch (actionType) {
            case FORM -> new FormActionConfig(formReader.readObject(root, ROOT));
            case FILE_UPLOAD -> readFileUpload(root);
            case PAGE_TASK -> readPageTask(root);
            case MANUAL_CONFIRM -> readManualConfirm(root);
            case DATA_QUERY -> readDataQuery(root, json);
        };
    }

    /**
     * 解析上传文件配置；minFiles / maxFiles / mimeTypes 为兼容存量配置仍按结构解析。
     *
     * @param root 配置对象
     * @return 上传文件配置
     */
    private FileUploadActionConfig readFileUpload(JsonNode root) {
        StrictJson.allowOnly(root, ROOT, Set.of("minFiles", "maxFiles", "mimeTypes", "instruction", "required"));
        return new FileUploadActionConfig(StrictJson.integer(root, "minFiles", ROOT),
            StrictJson.integer(root, "maxFiles", ROOT), StrictJson.texts(root, "mimeTypes", ROOT),
            StrictJson.text(root, "instruction", ROOT), StrictJson.bool(root, "required", ROOT, false));
    }

    /**
     * 解析页面任务配置。
     *
     * @param root 配置对象
     * @return 页面任务配置
     */
    private PageTaskActionConfig readPageTask(JsonNode root) {
        StrictJson.allowOnly(root, ROOT, Set.of("pageRoute", "parameters", "instruction"));
        return new PageTaskActionConfig(StrictJson.text(root, "pageRoute", ROOT),
            StrictJson.array(root, "parameters", ROOT, this::readParameter), StrictJson.text(root, "instruction", ROOT));
    }

    /**
     * 解析页面参数。
     *
     * @param parameter 参数 JSON
     * @param path JSON 路径
     * @return 页面参数
     */
    private PageParameter readParameter(JsonNode parameter, String path) {
        StrictJson.object(parameter, path);
        StrictJson.allowOnly(parameter, path, Set.of("key", "label"));
        return new PageParameter(StrictJson.text(parameter, "key", path), StrictJson.text(parameter, "label", path));
    }

    /**
     * 解析人工确认配置；noteRequired 为兼容存量配置仍按结构解析，完成时忽略（说明一律选填）。
     *
     * @param root 配置对象
     * @return 人工确认配置
     */
    private ManualConfirmActionConfig readManualConfirm(JsonNode root) {
        StrictJson.allowOnly(root, ROOT, Set.of("instruction", "noteRequired"));
        return new ManualConfirmActionConfig(StrictJson.text(root, "instruction", ROOT),
            StrictJson.bool(root, "noteRequired", ROOT, false));
    }

    /**
     * 解析预留的数据查询配置。
     *
     * @param root 配置对象
     * @param json 原文
     * @return 数据查询配置
     */
    private DataQueryActionConfig readDataQuery(JsonNode root, String json) {
        StrictJson.allowOnly(root, ROOT, Set.of("queryCode", "parameters", "passRule", "evidenceColumns"));
        return new DataQueryActionConfig(StrictJson.text(root, "queryCode", ROOT), json);
    }
}
