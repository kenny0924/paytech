package com.paytech.platform.support.integration.domainservice.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.verify.VerifyAssertion;
import com.paytech.platform.support.integration.domain.verify.VerifyMatch;
import com.paytech.platform.support.integration.domain.verify.VerifyStep;
import com.paytech.platform.support.integration.domain.verify.VerifyStepResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 核对一个步骤的期望（沙箱验证技术方案第 4 节）：执行状态或问题状态与代码、出口 ID、输出字段。
 * 正则在实际值的前 4096 个字符中查找。未能执行时只有一条未满足的 execution 核对。
 */
@Component
public class VerifyAssertionEvaluator {

    static final int REGEX_SUBJECT_LIMIT = 4096;
    static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * 核对期望。
     *
     * @param step 步骤及期望
     * @param result 实际结果
     * @param returnId 轨迹中的出口 ID；没有为 null
     * @return 每条期望的核对结果，至少一条
     */
    public List<VerifyAssertion> evaluate(VerifyStep step, VerifyStepResult result, String returnId) {
        var assertions = new ArrayList<VerifyAssertion>();
        if (result.getError() != null) {
            assertions.add(new VerifyAssertion("execution", "EQUALS", NODES.stringNode("EXECUTED"),
                NODES.stringNode(result.getError()), false));
            return assertions;
        }
        if (step.getProblemStatus() != null) {
            assertions.add(equals("problem.status", NODES.numberNode(step.getProblemStatus()),
                result.getProblemStatus() == null ? null : NODES.numberNode(result.getProblemStatus())));
            if (step.getProblemCode() != null) {
                assertions.add(equals("problem.code", NODES.stringNode(step.getProblemCode()), text(result.getProblemCode())));
            }
            return assertions;
        }
        JsonNode actualStatus = result.getExecuteStatus() != null ? text(result.getExecuteStatus())
            : text("PROBLEM " + result.getProblemStatus() + " " + result.getProblemCode());
        assertions.add(equals("executeStatus", NODES.stringNode(step.getExecuteStatus()), actualStatus));
        if (step.getReturnId() != null) {
            assertions.add(equals("returnId", NODES.stringNode(step.getReturnId()), text(returnId)));
        }
        for (VerifyStep.OutputExpectation expectation : step.getOutputs()) {
            JsonNode actual = result.getOutputs() == null ? null : at(result.getOutputs(), expectation.path());
            assertions.add(new VerifyAssertion("outputs." + expectation.path(), expectation.match().name(),
                expectation.value(), actual, matches(expectation, actual)));
        }
        return assertions;
    }

    /** 全部满足。 */
    public static boolean passed(List<VerifyAssertion> assertions) {
        return !assertions.isEmpty() && assertions.stream().allMatch(VerifyAssertion::passed);
    }

    static boolean matches(VerifyStep.OutputExpectation expectation, JsonNode actual) {
        boolean present = actual != null && !actual.isNull() && !actual.isMissingNode();
        VerifyMatch match = expectation.match();
        return switch (match) {
            case EXISTS -> present;
            case ABSENT -> !present;
            case EQUALS -> present && actual.equals(expectation.value());
            case ONE_OF -> present && java.util.stream.StreamSupport.stream(expectation.value().spliterator(), false)
                .anyMatch(actual::equals);
            case REGEX -> present && actual.isValueNode() && Pattern.compile(expectation.value().asString())
                .matcher(subject(actual.asString())).find();
        };
    }

    static String subject(String text) {
        return text.length() > REGEX_SUBJECT_LIMIT ? text.substring(0, REGEX_SUBJECT_LIMIT) : text;
    }

    static VerifyAssertion equals(String target, JsonNode expected, JsonNode actual) {
        return new VerifyAssertion(target, VerifyMatch.EQUALS.name(), expected, actual,
            actual != null && actual.equals(expected));
    }

    static JsonNode text(String value) {
        return value == null ? null : NODES.stringNode(value);
    }

    /** 按点分路径读取；不存在为 null。 */
    static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }
}
