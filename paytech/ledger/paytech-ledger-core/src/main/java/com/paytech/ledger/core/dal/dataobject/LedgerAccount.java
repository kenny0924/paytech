package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
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
     * 决定账户按经营主体、内部财务主体、渠道账户、银行账户或税务机关隔离的类型
     * <li>允许值：OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。</li>
     * <li>Client / Local Partner 不在总账开户：其科目以账簿主体为 Owner 的控制账户表达，明细在 account。</li>
     */
    private LedgerOwnerType ownerType;

    /**
     * 按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键
     */
    private String ownerId;

    /**
     * 总账账户不再按余额桶分账户，一律 NONE；Owner 余额桶由 account 明细账维护
     * <li>允许值：NONE。</li>
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
