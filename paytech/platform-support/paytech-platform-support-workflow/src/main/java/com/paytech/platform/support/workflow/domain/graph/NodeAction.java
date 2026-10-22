package com.paytech.platform.support.workflow.domain.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务节点引用的一个动作。
 *
 * @param key 动作 key，版本内全局唯一，同时是输出变量前缀
 * @param templateId 引用的动作模板 ID；草稿中可能缺失
 * @param required true 为必选（MANDATORY），false 为可选（OPTIONAL）
 * @param executorId 默认执行人 Operations 账号 ID；草稿中可能缺失
 * @param inputs 输入参数映射，键为模板声明的参数或字段 key，保持配置顺序
 */
public record NodeAction(String key, Long templateId, boolean required, Long executorId,
    Map<String, InputMapping> inputs) {

    /** 复制映射，保持不可变。 */
    public NodeAction {
        inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }
}
