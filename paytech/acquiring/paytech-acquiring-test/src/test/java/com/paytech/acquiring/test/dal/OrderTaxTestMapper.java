package com.paytech.acquiring.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 订单税务计算、当前报价、合同绑定与上下文计算引用的只读快照，供 Checker 独立断言。 */
@Mapper
public interface OrderTaxTestMapper {
    /** 订单全部计算记录，按 revision 升序。 */
    @Select("""
        SELECT revision,tax_calculation_id,calculation_stage,tax_status,basis_country_code,tax_amount,rounding_amount,
          total_amount,block_reason_code,expires_at,lines_json,components_json
        FROM t_order_tax_calculation WHERE order_id=#{orderId} ORDER BY revision
        """)
    List<Map<String, Object>> calculations(long orderId);

    /** 订单当前报价。 */
    @Select("SELECT amount_status,tax_amount,rounding_amount,total_amount FROM t_commerce_order WHERE order_id=#{orderId}")
    Map<String, Object> quote(long orderId);

    /** 订单全部合同，按呈现先后。 */
    @Select("""
        SELECT consumer_contract_id,tax_calculation_id,rendered_file_id
        FROM t_consumer_contract WHERE order_id=#{orderId} ORDER BY presented_at,consumer_contract_id
        """)
    List<Map<String, Object>> contracts(long orderId);

    /** 上下文的旧快照与建单计算引用。 */
    @Select("""
        SELECT finance_tax_snapshot_id,finance_tax_snapshot_hash,initial_tax_calculation_id
        FROM t_order_context WHERE order_id=#{orderId}
        """)
    Map<String, Object> context(long orderId);
}
