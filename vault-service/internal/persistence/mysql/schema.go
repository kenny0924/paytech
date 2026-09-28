package mysql

import (
	"context"
	"github.com/paytech/vault-service/internal/domain"
	"reflect"
	"strings"
)

// Contract rows are deliberately explicit, not inferred from whatever happens
// to be installed. No DDL or initialization is executed by this adapter.
var schemaChecks = []struct {
	query string
	want  []string
}{
	{`SELECT CONCAT(TABLE_NAME,'|',ENGINE,'|',TABLE_COLLATION,'|',TABLE_TYPE) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME`, []string{
		"t_vault_access_event|InnoDB|utf8mb4_0900_ai_ci|BASE TABLE", "t_vault_protect_request|InnoDB|utf8mb4_0900_ai_ci|BASE TABLE", "t_vault_record|InnoDB|utf8mb4_0900_ai_ci|BASE TABLE"}},
	{`SELECT CONCAT(TABLE_NAME,'|',COLUMN_NAME,'|',COLUMN_TYPE,'|',IS_NULLABLE,'|',COALESCE(COLLATION_NAME,''),'|',COALESCE(COLUMN_DEFAULT,'<NULL>'),'|',EXTRA,'|',GENERATION_EXPRESSION) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION`, []string{
		"t_vault_access_event|access_event_id|bigint|NO||<NULL>||",
		"t_vault_access_event|request_id|varchar(64)|NO|utf8mb4_0900_ai_ci|<NULL>||",
		"t_vault_access_event|trace_id|varchar(64)|YES|utf8mb4_0900_ai_ci|<NULL>||",
		"t_vault_access_event|caller_service|varchar(128)|NO|ascii_bin|<NULL>||",
		"t_vault_access_event|action|varchar(16)|NO|ascii_bin|<NULL>||",
		"t_vault_access_event|data_type|varchar(64)|YES|ascii_bin|<NULL>||",
		"t_vault_access_event|purpose|varchar(64)|YES|ascii_bin|<NULL>||",
		"t_vault_access_event|record_id|bigint|YES||<NULL>||",
		"t_vault_access_event|outcome|varchar(16)|NO|ascii_bin|<NULL>||",
		"t_vault_access_event|result_code|varchar(64)|NO|ascii_bin|<NULL>||",
		"t_vault_access_event|policy_version|varchar(64)|YES|ascii_bin|<NULL>||",
		"t_vault_access_event|create_time|datetime(6)|NO||<NULL>||",
		"t_vault_protect_request|protect_request_id|bigint|NO||<NULL>||",
		"t_vault_protect_request|caller_service|varchar(128)|NO|ascii_bin|<NULL>||",
		"t_vault_protect_request|idempotency_key|varchar(128)|NO|ascii_bin|<NULL>||",
		"t_vault_protect_request|request_hash|binary(32)|NO||<NULL>||",
		"t_vault_protect_request|record_id|bigint|NO||<NULL>||",
		"t_vault_protect_request|create_time|datetime(6)|NO||<NULL>||",
		"t_vault_protect_request|update_time|datetime(6)|NO||<NULL>||",
		"t_vault_record|record_id|bigint|NO||<NULL>||",
		"t_vault_record|token|varchar(64)|NO|ascii_bin|<NULL>||",
		"t_vault_record|data_type|varchar(64)|NO|ascii_bin|<NULL>||",
		"t_vault_record|token_mode|varchar(16)|NO|ascii_bin|<NULL>||",
		"t_vault_record|value_fingerprint|binary(32)|YES||<NULL>||",
		"t_vault_record|ciphertext|blob|NO||<NULL>||",
		"t_vault_record|encryption_nonce|binary(12)|NO||<NULL>||",
		"t_vault_record|wrapped_dek|blob|NO||<NULL>||",
		"t_vault_record|kms_key_ref|varchar(512)|NO|utf8mb4_0900_ai_ci|<NULL>||",
		"t_vault_record|create_time|datetime(6)|NO||<NULL>||",
		"t_vault_record|update_time|datetime(6)|NO||<NULL>||"}},
	{`SELECT CONCAT(TABLE_NAME,'|',INDEX_NAME,'|',NON_UNIQUE,'|',COLUMN_NAME,'|',SEQ_IN_INDEX,'|',COALESCE(SUB_PART,0),'|',COLLATION,'|',INDEX_TYPE,'|',IS_VISIBLE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX`, []string{
		"t_vault_access_event|idx_vault_access_caller|1|caller_service|1|0|A|BTREE|YES", "t_vault_access_event|idx_vault_access_caller|1|create_time|2|0|A|BTREE|YES",
		"t_vault_access_event|idx_vault_access_record|1|record_id|1|0|A|BTREE|YES", "t_vault_access_event|idx_vault_access_record|1|create_time|2|0|A|BTREE|YES",
		"t_vault_access_event|idx_vault_access_request|1|request_id|1|0|A|BTREE|YES", "t_vault_access_event|PRIMARY|0|access_event_id|1|0|A|BTREE|YES",
		"t_vault_protect_request|idx_vault_protect_record|1|record_id|1|0|A|BTREE|YES", "t_vault_protect_request|PRIMARY|0|protect_request_id|1|0|A|BTREE|YES",
		"t_vault_protect_request|uk_vault_protect_request|0|caller_service|1|0|A|BTREE|YES", "t_vault_protect_request|uk_vault_protect_request|0|idempotency_key|2|0|A|BTREE|YES",
		"t_vault_record|PRIMARY|0|record_id|1|0|A|BTREE|YES", "t_vault_record|uk_vault_record_stable|0|data_type|1|0|A|BTREE|YES", "t_vault_record|uk_vault_record_stable|0|value_fingerprint|2|0|A|BTREE|YES", "t_vault_record|uk_vault_record_token|0|token|1|0|A|BTREE|YES"}},
	{`SELECT CONCAT(k.TABLE_NAME,'|',k.CONSTRAINT_NAME,'|',k.COLUMN_NAME,'|',k.ORDINAL_POSITION,'|',k.REFERENCED_TABLE_SCHEMA=DATABASE(),'|',k.REFERENCED_TABLE_NAME,'|',k.REFERENCED_COLUMN_NAME,'|',r.UPDATE_RULE,'|',r.DELETE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION`, []string{
		"t_vault_access_event|fk_vault_access_record|record_id|1|1|t_vault_record|record_id|NO ACTION|NO ACTION", "t_vault_protect_request|fk_vault_protect_record|record_id|1|1|t_vault_record|record_id|NO ACTION|NO ACTION"}},
	{`SELECT CONCAT(t.TABLE_NAME,'|',c.CONSTRAINT_NAME,'|',t.ENFORCED,'|',c.CHECK_CLAUSE) FROM information_schema.CHECK_CONSTRAINTS c JOIN information_schema.TABLE_CONSTRAINTS t ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME WHERE c.CONSTRAINT_SCHEMA=DATABASE() ORDER BY t.TABLE_NAME,c.CONSTRAINT_NAME`, []string{
		"t_vault_access_event|ck_vault_access_action|YES|(action in ('PROTECT','REVEAL'))",
		"t_vault_access_event|ck_vault_access_outcome|YES|(outcome in ('SUCCESS','DENIED','FAILED'))",
		"t_vault_record|ck_vault_record_fingerprint|YES|(((token_mode = 'STABLE') and (value_fingerprint is not null)) or ((token_mode = 'REFERENCE') and (value_fingerprint is null)))",
		"t_vault_record|ck_vault_record_mode|YES|(token_mode in ('REFERENCE','STABLE'))"}},
}

func (s *Store) verifySchema(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	for _, check := range schemaChecks {
		rows, err := s.db.QueryContext(ctx, check.query)
		if err != nil {
			return classify(err)
		}
		var got []string
		for rows.Next() {
			var row string
			if err = rows.Scan(&row); err != nil {
				rows.Close()
				return domain.NewError(domain.InternalError)
			}
			got = append(got, normalizeMetadata(row))
		}
		err = rows.Err()
		rows.Close()
		if err != nil {
			return classify(err)
		}
		if !reflect.DeepEqual(got, check.want) {
			return domain.NewError(domain.InternalError)
		}
	}
	return nil
}
func normalizeMetadata(s string) string {
	var out strings.Builder
	for i := 0; i < len(s); {
		// Charset introducers are syntax only immediately before a literal.
		if strings.HasPrefix(s[i:], "_utf8mb4") && (i == 0 || strings.ContainsRune(" (=,", rune(s[i-1]))) && literalDelimiter(s[i+8:]) != "" {
			i += 8
		}
		if delimiter := literalDelimiter(s[i:]); delimiter != "" {
			out.WriteByte('\'')
			i += len(delimiter)
			end := strings.Index(s[i:], delimiter)
			if end < 0 {
				out.WriteString(s[i:])
				break
			}
			// Preserve literal content verbatim, including introducer-like text.
			out.WriteString(s[i : i+end])
			out.WriteByte('\'')
			i += end + len(delimiter)
			continue
		}
		if s[i] == '`' {
			end := strings.IndexByte(s[i+1:], '`')
			if end >= 0 {
				out.WriteString(s[i+1 : i+1+end])
				i += end + 2
				continue
			}
		}
		out.WriteByte(s[i])
		i++
	}
	return out.String()
}

func literalDelimiter(s string) string {
	if strings.HasPrefix(s, "\\'") {
		return "\\'"
	}
	if strings.HasPrefix(s, "'") {
		return "'"
	}
	return ""
}
