package com.paytech.operations.console.bff.test.simulator;

import java.util.HashMap;
import java.util.Map;

import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.Request;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.SemanticFailure;

/**
 * Risk 承保接口（/risk-underwriting/v1）的外部 DTO 场景：按"方法 + 路径"返回用例配置的 Owner DTO 或 Problem
 * <li>不实现承保状态机；未配置的请求视为意外调用（Simulator 记录并返回 500）。</li>
 */
final class RiskUnderwritingScenario {
    private static final String ROOT = "/risk-underwriting/v1/";

    /** 一条配置的回复：成功 DTO 或 Problem Code。 */
    private record Reply(int status, Object body, String problemCode) { }

    private final Map<String, Reply> replies = new HashMap<>();

    /** 清空配置。 */
    synchronized void reset() {
        replies.clear();
    }

    /** 配置成功 DTO。 */
    synchronized void reply(String method, String path, int status, Object body) {
        replies.put(method + " " + path, new Reply(status, body, null));
    }

    /** 配置 Problem。 */
    synchronized void problem(String method, String path, int status, String code) {
        replies.put(method + " " + path, new Reply(status, null, code));
    }

    /** 承保接口路径。 */
    boolean known(String method, String path) {
        return path.startsWith(ROOT);
    }

    /** 成功状态码；未配置时为 200（随后 dispatch 记为意外调用）。 */
    synchronized int status(Request request) {
        Reply reply = replies.get(request.method() + " " + request.path());
        return reply == null ? 200 : reply.status();
    }

    /** 返回配置的 DTO；配置为 Problem 时抛出语义失败，未配置时抛出意外调用。 */
    synchronized Object dispatch(Request request) {
        Reply reply = replies.get(request.method() + " " + request.path());
        if (reply == null) {
            throw new IllegalStateException("未配置的承保请求: " + request.method() + " " + request.path());
        }
        if (reply.problemCode() != null) {
            throw new SemanticFailure(reply.status(), reply.problemCode());
        }
        return reply.body();
    }
}
