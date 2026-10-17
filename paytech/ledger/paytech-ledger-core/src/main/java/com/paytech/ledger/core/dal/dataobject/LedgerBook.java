package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_ledger_book：为单一 Operating Entity 或 Finance Entity 和单一币种建立独立会计账簿；主体类型与标识创建后不可修改。 */
@Getter
@Setter
public class LedgerBook {
    /**
     * 服务侧生成的总账账簿 Snowflake 主键
     */
    private Long id;

    /**
     * 区分由 CCT Operating Entity 或 Finance 本地内部财务主体拥有的账簿
     * <li>允许值：OPERATING_ENTITY、FINANCE_ENTITY。</li>
     */
    private LedgerEntityType entityType;

    /**
     * 按主体类型解释的稳定业务ID；Operating Entity 来自 CCT，Finance Entity 使用本地主键，不建立多态外键
     */
    private String entityId;

    /**
     * 该账簿唯一使用的 ISO 4217 三位币种代码
     * <li>取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 该账簿采用的本库稳定会计科目表
     */
    private Long chartId;

    /**
     * 账簿是否允许接收新的会计分录
     * <li>允许值：OPEN、SUSPENDED、CLOSED。</li>
     */
    private BookStatus bookStatus;

    /**
     * 更新账簿状态时进行条件更新的乐观锁版本号
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
