package com.paytech.compliance.api.response;

import java.util.List;

/** 按角色与状态查询的结算资格控制集合；出款检查点据此冻结 control_set_hash（design/07 §2）。 */
public class SettlementControlListResponseBody {

    /** 符合条件的控制，按 control_id 升序。 */
    private List<SettlementControlResponseBody> items;

    /** 读取符合条件的控制，按 control_id 升序。 */
    public List<SettlementControlResponseBody> getItems() {
        return items;
    }

    /** 设置符合条件的控制，按 control_id 升序。 */
    public void setItems(List<SettlementControlResponseBody> items) {
        this.items = items;
    }
}
