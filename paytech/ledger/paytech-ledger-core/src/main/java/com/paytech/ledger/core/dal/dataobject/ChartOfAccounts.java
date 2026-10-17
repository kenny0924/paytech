package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.ReferenceDataStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_chart_of_accounts：保存可供账簿采用的稳定多级会计科目目录。 */
@Getter
@Setter
public class ChartOfAccounts {
    /**
     * 服务侧生成的会计科目表 Snowflake 主键
     */
    private Long id;

    /**
     * 科目表稳定且唯一的业务代码
     * <li>取值来源：Finance 会计科目表代码；非固定枚举。</li>
     */
    private String chartCode;

    /**
     * 供财务人员识别该科目目录的中文名称
     */
    private String chartName;

    /**
     * 科目表当前是否允许新账簿采用
     * <li>允许值：DRAFT、ACTIVE、INACTIVE。</li>
     */
    private ReferenceDataStatus status;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
