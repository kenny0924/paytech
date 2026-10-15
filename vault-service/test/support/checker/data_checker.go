package checker

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/dal"
)

// RecordChecker 检查 t_vault_record 与 t_vault_protect_request。
type RecordChecker struct {
	t      *testing.T
	mapper *dal.TestMapper
}

// Records 创建记录 Checker。
func Records(t *testing.T, mapper *dal.TestMapper) *RecordChecker {
	return &RecordChecker{t: t, mapper: mapper}
}

// Stored 断言 Token 对应的记录存在，数据类型与模式符合预期；STABLE 必有指纹，REFERENCE 必无指纹。返回记录。
func (c *RecordChecker) Stored(token, dataType string, mode biz.TokenMode) dal.RecordRow {
	c.t.Helper()
	row, found, err := c.mapper.FindRecordByToken(context.Background(), token)
	require.NoError(c.t, err)
	require.True(c.t, found, "记录不存在：token 前缀 %.6s", token)
	assert.Equal(c.t, dataType, row.DataType)
	assert.Equal(c.t, string(mode), row.TokenMode)
	assert.Equal(c.t, mode == biz.Stable, row.HasFingerprint, "指纹与模式不一致")
	return row
}

// Count 断言数据类型下的记录数。
func (c *RecordChecker) Count(dataType string, want int) *RecordChecker {
	c.t.Helper()
	n, err := c.mapper.CountRecords(context.Background(), dataType)
	require.NoError(c.t, err)
	assert.Equal(c.t, want, n, "数据类型 %s 的记录数", dataType)
	return c
}

// ProtectRequests 断言调用方的幂等映射：数量一致，且逐条指向期望的记录。
func (c *RecordChecker) ProtectRequests(caller string, recordIDs ...int64) *RecordChecker {
	c.t.Helper()
	rows, err := c.mapper.ListProtectRequests(context.Background(), caller)
	require.NoError(c.t, err)
	require.Len(c.t, rows, len(recordIDs), "调用方 %s 的幂等映射数量", caller)
	for i, row := range rows {
		assert.Equal(c.t, recordIDs[i], row.RecordID, "第 %d 条映射指向的记录", i+1)
	}
	return c
}

// AccessEventChecker 检查某个请求 ID 的访问审计。
type AccessEventChecker struct {
	t   *testing.T
	row dal.AccessEventRow
}

// AccessEvent 断言请求 ID 恰有一条审计事件并返回其 Checker。
func AccessEvent(t *testing.T, mapper *dal.TestMapper, requestID string) *AccessEventChecker {
	t.Helper()
	rows, err := mapper.ListAccessEvents(context.Background(), requestID)
	require.NoError(t, err)
	require.Len(t, rows, 1, "请求 %s 的审计事件数量", requestID)
	return &AccessEventChecker{t: t, row: rows[0]}
}

// NoAccessEvent 断言请求 ID 没有审计事件（如方法不支持，不进入业务边界）。
func NoAccessEvent(t *testing.T, mapper *dal.TestMapper, requestID string) {
	t.Helper()
	rows, err := mapper.ListAccessEvents(context.Background(), requestID)
	require.NoError(t, err)
	assert.Empty(t, rows, "请求 %s 不应产生审计", requestID)
}

// Result 断言动作、结果与结果码。
func (c *AccessEventChecker) Result(action biz.Action, outcome biz.Outcome, resultCode string) *AccessEventChecker {
	c.t.Helper()
	assert.Equal(c.t, string(action), c.row.Action)
	assert.Equal(c.t, string(outcome), c.row.Outcome)
	assert.Equal(c.t, resultCode, c.row.ResultCode)
	return c
}

// Caller 断言审计记录的调用方。
func (c *AccessEventChecker) Caller(want string) *AccessEventChecker {
	c.t.Helper()
	assert.Equal(c.t, want, c.row.Caller)
	return c
}

// DataType 断言数据类型；空串表示未记录。
func (c *AccessEventChecker) DataType(want string) *AccessEventChecker {
	c.t.Helper()
	assert.Equal(c.t, want, c.row.DataType.String)
	assert.Equal(c.t, want != "", c.row.DataType.Valid)
	return c
}

// Purpose 断言用途；空串表示未记录。
func (c *AccessEventChecker) Purpose(want string) *AccessEventChecker {
	c.t.Helper()
	assert.Equal(c.t, want, c.row.Purpose.String)
	assert.Equal(c.t, want != "", c.row.Purpose.Valid)
	return c
}

// Record 断言审计关联的记录；0 表示未关联。
func (c *AccessEventChecker) Record(want int64) *AccessEventChecker {
	c.t.Helper()
	assert.Equal(c.t, want, c.row.RecordID.Int64)
	assert.Equal(c.t, want != 0, c.row.RecordID.Valid)
	return c
}

// AnyPolicyVersion 表示已查询策略：版本为数据库策略标识 db-<版本号>（共享库的全局版本，不预测具体数值）。
const AnyPolicyVersion = "db-*"

// PolicyVersion 断言策略版本；空串表示未查询策略。
func (c *AccessEventChecker) PolicyVersion(want string) *AccessEventChecker {
	c.t.Helper()
	if want == AnyPolicyVersion {
		assert.True(c.t, c.row.PolicyVersion.Valid, "应记录策略版本")
		assert.Regexp(c.t, `^db-\d+$`, c.row.PolicyVersion.String)
		return c
	}
	assert.Equal(c.t, want, c.row.PolicyVersion.String)
	assert.Equal(c.t, want != "", c.row.PolicyVersion.Valid)
	return c
}

// Manual 断言人工操作的操作人、理由码与工单号；空串表示服务调用（三列为空）。
func (c *AccessEventChecker) Manual(actorID, reasonCode, ticketRef string) *AccessEventChecker {
	c.t.Helper()
	assert.Equal(c.t, actorID, c.row.ActorID.String)
	assert.Equal(c.t, actorID != "", c.row.ActorID.Valid)
	assert.Equal(c.t, reasonCode, c.row.ReasonCode.String)
	assert.Equal(c.t, ticketRef, c.row.TicketRef.String)
	return c
}

// LogChecker 检查被测服务输出的日志。
type LogChecker struct {
	t    *testing.T
	logs string
}

// Logs 创建日志 Checker。
func Logs(t *testing.T, logs string) *LogChecker { return &LogChecker{t: t, logs: logs} }

// NotLeaking 断言日志不含给定敏感值。
func (c *LogChecker) NotLeaking(values ...string) *LogChecker {
	c.t.Helper()
	for _, value := range values {
		assert.NotContains(c.t, c.logs, value, "日志泄漏敏感值")
	}
	return c
}
