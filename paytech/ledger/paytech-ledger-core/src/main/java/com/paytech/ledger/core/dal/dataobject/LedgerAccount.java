package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** t_ledger_account：在具体账簿和可过账科目下按核算对象与余额桶建立唯一会计科目账户，只保存身份不保存余额；余额由不可变 Journal 聚合得出，Owner 余额桶的实时权威是 account 服务（design/04 §3.1）。 */
@Getter
@Setter
public class LedgerAccount {
    /**
     * 服务侧生成的会计科目账户 Snowflake 主键，也是全局账户 ID
     */
    private Long id;

    /**
     * 该账户严格归属的本库经营主体币种账簿
     */
    private Long ledgerBookId;

    /**
     * 该账户使用且必须允许过账的本库会计科目定义
     */
    private Long chartAccountId;

    /**
     * 决定账户余额按经营主体、内部财务主体、Client、Local Partner、渠道账户、银行账户或税务机关隔离的类型
     * <li>允许值：OPERATING_ENTITY、FINANCE_ENTITY、CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。</li>
     */
    private OwnerType ownerType;

    /**
     * 按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键
     */
    private String ownerId;

    /**
     * PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner 的 PENDING 是共享待结算桶；PARTNER_RECOVERY_RECEIVABLE 使用 NONE；CLIENT_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，CLIENT_RECOVERY_RECEIVABLE/NONE、CLIENT_SECURITY_DEPOSIT/NONE 以及 Channel 三阶段账户均使用独立会计科目并采用 NONE，不复用应付款余额桶
     * <li>允许值：PENDING、AVAILABLE、LOCKED、NONE。</li>
     */
    private BalanceBucket balanceBucket;

    /**
     * 创建账户时从会计科目复制并冻结的正常余额方向；账户仍允许借贷双向过账
     * <li>允许值：DEBIT、CREDIT。</li>
     */
    private NormalSide normalSide;

    /**
     * 该具体核算账户当前是否允许新的会计分录过账
     * <li>允许值：ACTIVE、INACTIVE、CLOSED。</li>
     */
    private AccountStatus accountStatus;






    /**
     * 该记录在 Ledger 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
