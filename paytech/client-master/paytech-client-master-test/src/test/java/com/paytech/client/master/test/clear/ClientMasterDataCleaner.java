package com.paytech.client.master.test.clear;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * client-master 测试专用库的全量清理（design/10 §3.1）：表集合等于 init/ddl.sql 的表集合，不触碰其他服务的库。
 * <li>在各 Mocker 定向清理之后执行，删除本库全部测试数据，包括生产链随之生成的补偿任务与审计行。</li>
 * <li>只连接 mor_client_master；其他库与共享开发数据不受影响。</li>
 */
@TestComponent
@Order(Ordered.LOWEST_PRECEDENCE)
public class ClientMasterDataCleaner implements Clear {
    /** 本库全部表，与 init/ddl.sql 一致。 */
    public static final List<String> TABLES = List.of("t_party", "t_party_profile", "t_individual_profile",
        "t_organization_profile", "t_party_identity_key", "t_party_profile_identifier", "t_party_address", "t_party_contact",
        "t_party_relation", "t_client", "t_local_partner", "t_operating_entity", "t_client_business_profile",
        "t_client_business_classification", "t_client_application", "t_partner_application",
        "t_operating_entity_application", "t_operating_entity_scope", "t_application_document",
        "t_client_entity_affiliation", "t_entity_source_relation", "t_consumer_legal_document", "t_seller_disclosure",
        "t_business_change_record", "t_business_change_version", "t_compensation_event", "t_client_stored_card_setting",
        "t_consumed_event", "t_consumed_aggregate_version");

    private final JdbcTemplate jdbc;

    /** 使用被测应用的同一数据源。 */
    public ClientMasterDataCleaner(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 逐表删除本库全部测试数据。 */
    @Override
    public void clear() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (!"mor_client_master".equals(database)) {
            throw new IllegalStateException("清理器只允许连接 mor_client_master，实际: " + database);
        }
        for (String table : TABLES) {
            jdbc.update("DELETE FROM " + table);
        }
    }
}
