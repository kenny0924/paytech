package com.paytech.tax.domain.view;

import java.util.List;

import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.core.dal.dataobject.TaxMarket;
import com.paytech.tax.core.dal.dataobject.TaxPeriod;
import com.paytech.tax.core.dal.dataobject.TaxPolicy;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.dal.dataobject.TaxReportingEntry;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.domain.calculation.TaxCalculationView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个订单的税务全貌（多表组合只读查询）：计算、正式交易、底稿与期间、单据，以及被引用的注册与地区包。 */
@Getter
@AllArgsConstructor
public class TaxOrderView {
    /** 订单号 */
    private final String orderNo;

    /** 全部计算，按准备版本升序 */
    private final List<TaxCalculationView> calculations;

    /** 正式交易 */
    private final List<TaxTransaction> transactions;

    /** 申报底稿 */
    private final List<TaxReportingEntry> entries;

    /** 底稿归属的期间 */
    private final List<TaxPeriod> periods;

    /** 单据 */
    private final List<TaxDocument> documents;

    /** 组件、底稿与单据引用的注册 */
    private final List<TaxRegistration> registrations;

    /** 组件引用的准入（managedBy 来源） */
    private final List<TaxMarket> markets;

    /** 组件引用的地区包 */
    private final List<TaxPolicy> policies;
}
