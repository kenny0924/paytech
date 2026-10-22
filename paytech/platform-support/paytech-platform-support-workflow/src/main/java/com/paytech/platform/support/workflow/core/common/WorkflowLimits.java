package com.paytech.platform.support.workflow.core.common;

/**
 * 流程草稿的大小上限：草稿保存超过即拒绝，发布校验同样报告。
 * <li>两者之和加请求其他字段须在 Gateway 草稿路由的 65536 字节 Body 上限内。</li>
 */
public final class WorkflowLimits {

    /** graph_json 上限：48 KiB。 */
    public static final int MAX_GRAPH_BYTES = 48 * 1024;

    /** start_form_json 上限：12 KiB。 */
    public static final int MAX_START_FORM_BYTES = 12 * 1024;

    private WorkflowLimits() {
    }
}
