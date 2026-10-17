package com.paytech.risk.decision.service.processor;

/** 执行发布用例共用的资源锁键。 */
final class ExecutionReleaseLocks {
    /** 纯工具。 */
    private ExecutionReleaseLocks() { }

    /** 同一决策点的执行发布创建串行。 */
    static String point(String pointCode) {
        return "decision:execution-release:point:" + pointCode;
    }

    /** 同一执行发布的校验与影子运行启动串行。 */
    static String release(long releaseId) {
        return "decision:execution-release:" + releaseId;
    }

    /** 激活会替代同环境的生效发布，全部激活串行。 */
    static String activation() {
        return "decision:execution-release:activation";
    }
}
