package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_fiscal_calendar：保存 Operating Entity 或 Finance Entity 使用的财年起始月份和 IANA 时区规则；主体类型与标识创建后不可修改。 */
@Getter
@Setter
public class FiscalCalendar {
    /**
     * 服务侧生成的财务日历 Snowflake 主键
     */
    private Long id;

    /**
     * 区分使用该财务日历的 CCT Operating Entity 或 Finance 本地内部财务主体
     * <li>允许值：OPERATING_ENTITY、FINANCE_ENTITY。</li>
     */
    private LedgerEntityType entityType;

    /**
     * 按主体类型解释的稳定业务ID；Finance Entity 使用本地主键且不建立多态外键
     */
    private String entityId;

    /**
     * V1 中该经营主体唯一法定财务日历的稳定展示和接口代码
     * <li>取值来源：Finance 财务日历代码；非固定枚举。</li>
     */
    private String calendarCode;

    /**
     * 用于确定财务日期边界的 IANA 时区代码
     * <li>取值来源：IANA 时区数据库；非固定枚举。</li>
     */
    private String timezone;

    /**
     * 财年开始月份，取值范围为一至十二
     */
    private Integer yearStartMonth;

    /**
     * 更新财务日历规则时进行条件更新的乐观锁版本号
     */
    private Long version;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
