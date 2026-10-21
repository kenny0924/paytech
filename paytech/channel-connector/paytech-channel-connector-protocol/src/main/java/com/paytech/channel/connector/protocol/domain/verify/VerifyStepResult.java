package com.paytech.channel.connector.protocol.domain.verify;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/**
 * 一个步骤的实际结果：已执行（executeStatus 与 outputs）或执行前返回问题（HTTP 状态与代码），
 * 或执行前出错（error，例如模板渲染失败、出站地址未标记沙箱）。
 */
@Getter
@AllArgsConstructor
public class VerifyStepResult {

    /** 已执行时的执行状态；否则为 null。 */
    private final String executeStatus;

    /** 已执行时的输出；否则为 null。 */
    private final JsonNode outputs;

    /** 执行前问题的 HTTP 状态；否则为 null。 */
    private final Integer problemStatus;

    /** 执行前问题的代码；否则为 null。 */
    private final String problemCode;

    /** 执行前问题的字段错误位置；否则为空。 */
    private final List<String> problemFields;

    /** 未能执行的原因；否则为 null。 */
    private final String error;

    /** 已执行。 */
    public static VerifyStepResult executed(String executeStatus, JsonNode outputs) {
        return new VerifyStepResult(executeStatus, outputs, null, null, List.of(), null);
    }

    /** 执行前返回问题。 */
    public static VerifyStepResult problem(int status, String code, List<String> fields) {
        return new VerifyStepResult(null, null, status, code, List.copyOf(fields), null);
    }

    /** 未能执行。 */
    public static VerifyStepResult failed(String error) {
        return new VerifyStepResult(null, null, null, null, List.of(), error);
    }
}
