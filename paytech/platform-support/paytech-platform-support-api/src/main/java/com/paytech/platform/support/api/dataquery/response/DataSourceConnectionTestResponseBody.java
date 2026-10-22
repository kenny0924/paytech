package com.paytech.platform.support.api.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 连接检查结果：连通性与账号权限分别给出，未通过时列出具体原因。 */
@Getter
@Setter
public class DataSourceConnectionTestResponseBody {

    /** 总结果：PASSED 或 FAILED。 */
    private String result;

    /** 是否建立连接并成功执行 SELECT 1。 */
    private boolean connected;

    /** 账号权限是否全部为只读；未连通时为 false。 */
    private boolean privilegesPassed;

    /**
     * 账号授权原文
     * <li>SHOW GRANTS 的逐行结果；未连通时为空列表。</li>
     */
    private List<String> grants;

    /**
     * 不通过的原因
     * <li>如“连接失败: …”“含写权限 INSERT: GRANT INSERT ON …”“含角色授权: …”；通过时为空列表。</li>
     */
    private List<String> violations;

    /** 检查时间，UTC ISO-8601 微秒精度。 */
    private String checkedAt;
}
