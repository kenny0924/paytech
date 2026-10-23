package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import tools.jackson.databind.JsonNode;

/** 完成动作的提交内容。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubmitWorkflowActionRequestBody {

    /**
     * 提交内容
     * <li>FORM 为 {字段 key: 值}；FILE_UPLOAD 为 {"files": ["/files/v1/{fileId}", ...]}；PAGE_TASK、MANUAL_CONFIRM 为 {"note": "确认说明"}。</li>
     * <li>文件值一律为文件地址数组，格式不合法返回 422（字段路径 output.files[i] 或 output.{key}[i]）。</li>
     */
    @NotNull
    private JsonNode output;
}
