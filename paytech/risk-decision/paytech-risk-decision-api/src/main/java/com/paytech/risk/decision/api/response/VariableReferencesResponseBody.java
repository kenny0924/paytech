package com.paytech.risk.api.decision.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.api.decision.RiskVariableModels.*;

/** 游标扫描可能返回空页且 hasMore=true；不提供虚假总数。 */
public class VariableReferencesResponseBody {

    /** 本次查询返回的有界条目。 */
    private List<VariableReference> items;

    /** 后续扫描游标；扫描结束时为空。 */
    private String nextCursor;

    /** 是否仍需继续扫描；空页也可能为 true。 */
    private boolean hasMore;

    /** 本页实际扫描行数。 */
    private int scannedRows;

    /** JSON 反序列化构造。 */
    public VariableReferencesResponseBody() { }

    /** 按完整合同构造。 */
    public VariableReferencesResponseBody(List<VariableReference> items, String nextCursor, boolean hasMore, int scannedRows) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.scannedRows = scannedRows;
    }

    /** 读取本次查询返回的有界条目。 */
    public List<VariableReference> getItems() { return items; }

    /** 设置本次查询返回的有界条目。 */
    public void setItems(List<VariableReference> items) { this.items = items; }

    /** 读取后续扫描游标。 */
    public String getNextCursor() { return nextCursor; }

    /** 设置后续扫描游标。 */
    public void setNextCursor(String nextCursor) { this.nextCursor = nextCursor; }

    /** 读取是否仍需继续扫描。 */
    public boolean getHasMore() { return hasMore; }

    /** 设置是否仍需继续扫描。 */
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }

    /** 读取本页实际扫描行数。 */
    public int getScannedRows() { return scannedRows; }

    /** 设置本页实际扫描行数。 */
    public void setScannedRows(int scannedRows) { this.scannedRows = scannedRows; }
}
