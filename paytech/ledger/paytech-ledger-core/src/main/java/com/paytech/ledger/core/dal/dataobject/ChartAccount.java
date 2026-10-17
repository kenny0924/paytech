package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_chart_account：定义稳定会计科目表中的五类多级科目及其正常余额方向和可过账属性。 */
@Getter
@Setter
public class ChartAccount {
    /**
     * 服务侧生成的会计科目 Snowflake 主键
     */
    private Long id;

    /**
     * 该科目所属的本库会计科目表
     */
    private Long chartId;

    /**
     * 该科目的直接上级科目，顶级科目为空；科目表被任一账簿采用后既有父子关系不可修改
     * <li>可为空。</li>
     */
    private Long parentAccountId;

    /**
     * 在同一会计科目表内唯一且激活后不可改变的科目代码
     * <li>取值来源：Finance 会计科目代码；非固定枚举。</li>
     */
    private String accountCode;

    /**
     * 财务报表和记账界面使用的科目名称
     */
    private String accountName;

    /**
     * 会计恒等式中的五类科目分类
     * <li>允许值：ASSET、LIABILITY、EQUITY、REVENUE、EXPENSE。</li>
     */
    private AccountClass accountClass;

    /**
     * 该科目余额增加时采用的借贷方向
     * <li>允许值：DEBIT、CREDIT。</li>
     */
    private NormalSide normalSide;

    /**
     * 是否允许 Journal Line 直接记入该科目的结构属性；汇总父科目必须为否，科目表被账簿采用后不可修改
     * <li>1 表示是，0 表示否。</li>
     */
    private Boolean postingAllowed;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
