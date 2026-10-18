package data

import (
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

type schemaContract struct {
	tables        int
	fields        int
	uniqueIndexes int
	accessIndexes int
	foreignKeys   int
	checks        int
}

func TestSchemaMatchesApprovedContract(t *testing.T) {
	schema := loadSchema(t, "../../docs/database/sql/vault-schema.sql")
	assertContract(t, schema, schemaContract{
		tables:        6,
		fields:        66,
		uniqueIndexes: 6,
		accessIndexes: 7,
		foreignKeys:   2,
		checks:        4,
	})
	assertTableColumns(t, schema, "t_vault_record", 11)
	assertTableColumns(t, schema, "t_vault_protect_request", 7)
	assertTableColumns(t, schema, "t_vault_access_event", 15)
	assertTableColumns(t, schema, "t_vault_data_type", 13)
	assertTableColumns(t, schema, "t_vault_access_rule", 9)
	assertTableColumns(t, schema, "t_vault_policy_change", 11)
	assertContains(t, schema,
		"UNIQUE KEY uk_vault_record_token (token)",
		"UNIQUE KEY uk_vault_record_stable (data_type, value_fingerprint)",
		"UNIQUE KEY uk_vault_protect_request (caller_service, idempotency_key)",
		"KEY idx_vault_protect_record (record_id)",
		"KEY idx_vault_access_record (record_id, create_time)",
		"KEY idx_vault_access_caller (caller_service, create_time)",
		"KEY idx_vault_access_request (request_id)",
		"KEY idx_vault_access_actor (actor_id, create_time)",
		"UNIQUE KEY uk_vault_data_type_code (data_type_code)",
		"UNIQUE KEY uk_vault_access_rule (caller_service, action, data_type_code, purpose_code)",
		"KEY idx_vault_access_rule_type (data_type_code)",
		"UNIQUE KEY uk_vault_policy_version (policy_version_no)",
		"KEY idx_vault_policy_change_target (target_type, target_key, create_time)",
		"CONSTRAINT fk_vault_protect_record FOREIGN KEY (record_id)",
		"CONSTRAINT fk_vault_access_record FOREIGN KEY (record_id)",
		"CONSTRAINT ck_vault_record_mode CHECK (token_mode IN ('REFERENCE','STABLE'))",
		"CONSTRAINT ck_vault_record_fingerprint CHECK ((token_mode='STABLE' AND value_fingerprint IS NOT NULL) OR (token_mode='REFERENCE' AND value_fingerprint IS NULL))",
		"CONSTRAINT ck_vault_access_action CHECK (action IN ('PROTECT','REVEAL'))",
		"CONSTRAINT ck_vault_access_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))",
		"ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
		"CHARACTER SET ascii COLLATE ascii_bin",
	)
	if actual := strings.Count(schema, "CHARACTER SET ascii COLLATE ascii_bin"); actual != 29 {
		t.Fatalf("ascii_bin machine identifier columns = %d, want 29", actual)
	}
}

func loadSchema(t *testing.T, relativePath string) string {
	t.Helper()
	bytes, err := os.ReadFile(filepath.Clean(relativePath))
	if err != nil {
		t.Fatalf("read schema: %v", err)
	}
	return string(bytes)
}

func assertContract(t *testing.T, schema string, want schemaContract) {
	t.Helper()
	got := schemaContract{
		tables:        strings.Count(schema, "CREATE TABLE "),
		fields:        countFields(schema),
		uniqueIndexes: strings.Count(schema, "UNIQUE KEY uk_"),
		accessIndexes: strings.Count(schema, "KEY idx_"),
		foreignKeys:   strings.Count(schema, " FOREIGN KEY "),
		checks:        strings.Count(schema, " CHECK ("),
	}
	if got != want {
		t.Fatalf("schema contract = %+v, want %+v", got, want)
	}
}

func assertTableColumns(t *testing.T, schema, tableName string, want int) {
	t.Helper()
	pattern := regexp.MustCompile(`(?s)CREATE TABLE ` + regexp.QuoteMeta(tableName) + ` \((.*?)\) ENGINE=InnoDB`)
	matches := pattern.FindStringSubmatch(schema)
	if len(matches) != 2 {
		t.Fatalf("missing CREATE TABLE for %s", tableName)
	}
	fields := 0
	for _, line := range strings.Split(matches[1], "\n") {
		trimmed := strings.TrimSpace(line)
		if regexp.MustCompile(`^[a-z][a-z0-9_]* (BIGINT|VARCHAR|BINARY|BLOB|DATETIME|TINYINT|INT|JSON)`).MatchString(trimmed) {
			fields++
		}
	}
	if fields != want {
		t.Fatalf("%s fields = %d, want %d", tableName, fields, want)
	}
}

func countFields(schema string) int {
	count := 0
	for _, tableName := range []string{"t_vault_record", "t_vault_protect_request", "t_vault_access_event", "t_vault_data_type", "t_vault_access_rule", "t_vault_policy_change"} {
		pattern := regexp.MustCompile(`(?s)CREATE TABLE ` + regexp.QuoteMeta(tableName) + ` \((.*?)\) ENGINE=InnoDB`)
		matches := pattern.FindStringSubmatch(schema)
		if len(matches) != 2 {
			continue
		}
		for _, line := range strings.Split(matches[1], "\n") {
			trimmed := strings.TrimSpace(line)
			if regexp.MustCompile(`^[a-z][a-z0-9_]* (BIGINT|VARCHAR|BINARY|BLOB|DATETIME|TINYINT|INT|JSON)`).MatchString(trimmed) {
				count++
			}
		}
	}
	return count
}

func assertContains(t *testing.T, schema string, expected ...string) {
	t.Helper()
	for _, fragment := range expected {
		if !strings.Contains(schema, fragment) {
			t.Fatalf("schema is missing %q", fragment)
		}
	}
}
