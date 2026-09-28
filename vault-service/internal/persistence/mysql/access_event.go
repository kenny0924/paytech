package mysql

import (
	"context"
	"github.com/paytech/vault-service/internal/domain"
)

func (t *transaction) InsertAccessEvent(ctx context.Context, e domain.AccessEvent) error {
	_, err := t.tx.ExecContext(ctx, "INSERT INTO t_vault_access_event (access_event_id,request_id,trace_id,caller_service,action,data_type,purpose,record_id,outcome,result_code,policy_version,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", e.ID, e.RequestID, e.TraceID, e.Caller, e.Action, e.DataType, e.Purpose, e.RecordID, e.Outcome, e.ResultCode, e.PolicyVersion, utc(e.CreateTime))
	return classify(err)
}
