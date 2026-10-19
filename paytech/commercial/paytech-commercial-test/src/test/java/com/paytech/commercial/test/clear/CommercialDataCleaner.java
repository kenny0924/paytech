package com.paytech.commercial.test.clear;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * commercial 测试专用库的全量清理（design/10 §3.1）：表集合等于 init/ddl.sql 的表集合，不触碰其他服务的库。
 * <li>在夹具定向清理之后执行；只连接 mor_commercial 或并行任务的隔离库 mor_commercial_&lt;后缀&gt;。</li>
 */
@TestComponent
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommercialDataCleaner implements Clear {
    /** 本库全部表，与 init/ddl.sql 一致。 */
    public static final List<String> TABLES = List.of("t_agreement", "t_agreement_party", "t_agreement_document_ref",
        "t_agreement_operating_entity", "t_agreement_product", "t_agreement_capability", "t_agreement_capability_limit",
        "t_product", "t_product_capability", "t_product_fee_event", "t_pricing_profile", "t_pricing_profile_cond",
        "t_commercial_term_plan", "t_commercial_term_version", "t_commercial_term_price", "t_commercial_term_component",
        "t_commercial_term_component_band", "t_business_change_record", "t_business_change_version", "t_compensation_event",
        "t_consumed_event");

    private final JdbcTemplate jdbc;

    /** 使用被测应用的同一数据源。 */
    public CommercialDataCleaner(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 逐表删除本库全部测试数据。 */
    @Override
    public void clear() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (database == null || !database.matches("mor_commercial(_[a-z0-9]+)?")) {
            throw new IllegalStateException("清理器只允许连接 mor_commercial 或其隔离库，实际: " + database);
        }
        for (String table : TABLES) {
            jdbc.update("DELETE FROM " + table);
        }
    }
}
