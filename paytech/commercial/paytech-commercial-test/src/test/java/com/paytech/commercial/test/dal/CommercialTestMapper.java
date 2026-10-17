package com.paytech.commercial.test.dal;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/** 只修改本例商务夹具的合成行，制造复核负例；只操作 mor_commercial。 */
@TestComponent
public class CommercialTestMapper {
    private final JdbcTemplate jdbc;

    /** 使用被测应用的同一数据源。 */
    public CommercialTestMapper(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 推进某合同的 AGREEMENT 根修订。 */
    public void advanceAgreementRoot(long agreementId) {
        jdbc.update("UPDATE t_business_change_record SET current_version=current_version+1 "
            + "WHERE record_type='AGREEMENT' AND record_id=? AND parent_history_id IS NULL", Long.toString(agreementId));
    }

    /** 终止某合同。 */
    public void terminateAgreement(long agreementId) {
        jdbc.update("UPDATE t_agreement SET status='TERMINATED' WHERE agreement_id=?", agreementId);
    }
}
