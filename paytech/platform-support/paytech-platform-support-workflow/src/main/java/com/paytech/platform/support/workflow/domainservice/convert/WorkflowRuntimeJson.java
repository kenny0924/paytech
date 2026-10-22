package com.paytech.platform.support.workflow.domainservice.convert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.runtime.CallbackTarget;
import com.paytech.platform.support.workflow.domain.runtime.WorkflowAssignment;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 运行期本库 JSON 列的读写：人员分配、动作模板快照与通知回调目标。ID 一律写成十进制字符串。
 *
 * <p>这些列只由本模块写入；读取时结构不符属于数据不一致，抛 {@link ErrorException}。</p>
 */
public final class WorkflowRuntimeJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ActionConfigJsonReader configReader = new ActionConfigJsonReader();

    /**
     * 写人员分配。
     *
     * @param assignment 人员分配
     * @return assignment_json
     */
    public String writeAssignment(WorkflowAssignment assignment) {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode approvers = root.putObject("approvers");
        assignment.approvers().forEach((key, ids) -> {
            ArrayNode array = approvers.putArray(key);
            ids.forEach(id -> array.add(Long.toString(id)));
        });
        ObjectNode executors = root.putObject("executors");
        assignment.executors().forEach((key, id) -> executors.put(key, Long.toString(id)));
        return JSON.writeValueAsString(root);
    }

    /**
     * 读人员分配。
     *
     * @param json assignment_json
     * @return 人员分配
     */
    public WorkflowAssignment readAssignment(String json) {
        JsonNode root = parse(json, "assignment_json");
        Map<String, List<Long>> approvers = new LinkedHashMap<>();
        for (String key : root.path("approvers").propertyNames()) {
            List<Long> ids = new ArrayList<>();
            root.path("approvers").get(key).forEach(id -> ids.add(Long.valueOf(id.asString())));
            approvers.put(key, ids);
        }
        Map<String, Long> executors = new LinkedHashMap<>();
        for (String key : root.path("executors").propertyNames()) {
            executors.put(key, Long.valueOf(root.path("executors").get(key).asString()));
        }
        return new WorkflowAssignment(approvers, executors);
    }

    /**
     * 写发布时冻结的动作模板快照：模板 ID、模板版本、类型与原配置。
     *
     * @param templates 模板与其 config_json 原文
     * @return action_snapshot_json
     */
    public String writeSnapshot(Collection<FrozenTemplate> templates) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode array = root.putArray("templates");
        for (FrozenTemplate template : templates) {
            ObjectNode item = array.addObject();
            item.put("templateId", Long.toString(template.templateId()));
            item.put("templateVersion", template.templateVersion());
            item.put("templateCode", template.templateCode());
            item.put("templateName", template.templateName());
            item.put("actionType", template.actionType().getCode());
            item.set("config", parse(template.configJson(), "config_json"));
        }
        return JSON.writeValueAsString(root);
    }

    /**
     * 快照中的模板名称（发布时冻结），按模板 ID；早期快照没有名称时不含该模板。
     *
     * @param json action_snapshot_json；可为 null
     * @return 模板 ID 到名称
     */
    public Map<Long, String> snapshotNames(String json) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (json == null) {
            return result;
        }
        for (JsonNode item : parse(json, "action_snapshot_json").path("templates")) {
            if (item.hasNonNull("templateName")) {
                result.put(Long.parseLong(item.path("templateId").asString()), item.path("templateName").asString());
            }
        }
        return result;
    }

    /**
     * 快照中各模板冻结的版本，按模板 ID。
     *
     * @param json action_snapshot_json；可为 null
     * @return 模板 ID 到版本
     */
    public Map<Long, Integer> snapshotVersions(String json) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (json == null) {
            return result;
        }
        for (JsonNode item : parse(json, "action_snapshot_json").path("templates")) {
            result.put(Long.parseLong(item.path("templateId").asString()), item.path("templateVersion").intValue());
        }
        return result;
    }

    /**
     * 读动作模板快照；快照中的模板按发布时状态视为可用。
     *
     * @param json action_snapshot_json
     * @return 模板 ID 到模板事实
     */
    public Map<Long, ActionTemplateReference> readSnapshot(String json) {
        Map<Long, ActionTemplateReference> result = new LinkedHashMap<>();
        for (JsonNode item : parse(json, "action_snapshot_json").path("templates")) {
            long id = Long.parseLong(item.path("templateId").asString());
            WorkflowActionType type = StrictJson.parseCode(item.path("actionType").asString(), "actionType",
                WorkflowActionType.class);
            result.put(id, new ActionTemplateReference(id, item.path("templateVersion").intValue(), type,
                ActionTemplateStatus.ACTIVE, configReader.read(type, JSON.writeValueAsString(item.get("config")))));
        }
        return result;
    }

    /**
     * 写通知回调目标。
     *
     * @param target 目标
     * @return target 列 JSON
     */
    public String writeTarget(CallbackTarget target) {
        ObjectNode root = JSON.createObjectNode();
        if (target.callbackCode() == null) {
            ArrayNode recipients = root.putArray("recipientIds");
            target.recipientIds().forEach(id -> recipients.add(Long.toString(id)));
            ArrayNode channels = root.putArray("channels");
            target.channels().forEach(channels::add);
        } else {
            root.put("callbackCode", target.callbackCode());
        }
        root.put("fromStatus", target.fromStatus());
        root.put("toStatus", target.toStatus());
        root.put("actorId", target.actorId() == null ? null : Long.toString(target.actorId()));
        root.put("comment", target.comment());
        root.put("instanceVersion", Long.toString(target.instanceVersion()));
        return JSON.writeValueAsString(root);
    }

    /**
     * 读通知回调目标。
     *
     * @param json target 列 JSON
     * @return 目标
     */
    public CallbackTarget readTarget(String json) {
        JsonNode root = parse(json, "target");
        List<Long> recipients = new ArrayList<>();
        root.path("recipientIds").forEach(id -> recipients.add(Long.valueOf(id.asString())));
        List<String> channels = new ArrayList<>();
        root.path("channels").forEach(channel -> channels.add(channel.asString()));
        String actor = text(root, "actorId");
        return new CallbackTarget(recipients, channels, text(root, "callbackCode"), text(root, "fromStatus"),
            text(root, "toStatus"), actor == null ? null : Long.valueOf(actor), text(root, "comment"),
            Long.parseLong(root.path("instanceVersion").asString()));
    }

    /**
     * 解析 JSON 文本为树。
     *
     * @param json 文本
     * @return 树
     */
    public JsonNode tree(String json) {
        return parse(json, "json");
    }

    /**
     * 把树写回文本。
     *
     * @param node 树
     * @return JSON 文本
     */
    public String write(JsonNode node) {
        return JSON.writeValueAsString(node);
    }

    /**
     * 可空文本属性。
     *
     * @param root 对象
     * @param name 属性
     * @return 文本或 null
     */
    private String text(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }

    /**
     * 解析本库 JSON 列。
     *
     * @param json 文本
     * @param column 列名，用于错误信息
     * @return 树
     */
    private JsonNode parse(String json, String column) {
        try {
            return JSON.readTree(json);
        } catch (JacksonException | IllegalArgumentException malformed) {
            throw new ErrorException("工作流本库 JSON 列结构不合法：" + column, malformed);
        }
    }

    /**
     * 发布时冻结的一个模板。
     *
     * @param templateId 模板 ID
     * @param templateVersion 模板版本
     * @param templateCode 模板编码（展示用）
     * @param templateName 模板名称（展示用）
     * @param actionType 动作类型
     * @param configJson 配置原文
     */
    public record FrozenTemplate(long templateId, int templateVersion, String templateCode, String templateName,
        WorkflowActionType actionType, String configJson) {
    }
}
