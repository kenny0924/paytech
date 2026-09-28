package mysql

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

func TestSchemaMatchesApprovedThreeTableContract(t *testing.T) {
	schema := loadSchema(t, "../../../docs/database/sql/vault-schema.sql")
	assertContract(t, schema, schemaContract{
		tables:        3,
		fields:        30,
		uniqueIndexes: 3,
		accessIndexes: 4,
		foreignKeys:   2,
		checks:        4,
	})
	assertTableColumns(t, schema, "t_vault_record", 11)
	assertTableColumns(t, schema, "t_vault_protect_request", 7)
	assertTableColumns(t, schema, "t_vault_access_event", 12)
	assertContains(t, schema,
		"UNIQUE KEY uk_vault_record_token (token)",
		"UNIQUE KEY uk_vault_record_stable (data_type, value_fingerprint)",
		"UNIQUE KEY uk_vault_protect_request (caller_service, idempotency_key)",
		"KEY idx_vault_protect_record (record_id)",
		"KEY idx_vault_access_record (record_id, create_time)",
		"KEY idx_vault_access_caller (caller_service, create_time)",
		"KEY idx_vault_access_request (request_id)",
		"CONSTRAINT fk_vault_protect_record FOREIGN KEY (record_id)",
		"CONSTRAINT fk_vault_access_record FOREIGN KEY (record_id)",
		"CONSTRAINT ck_vault_record_mode CHECK (token_mode IN ('REFERENCE','STABLE'))",
		"CONSTRAINT ck_vault_record_fingerprint CHECK ((token_mode='STABLE' AND value_fingerprint IS NOT NULL) OR (token_mode='REFERENCE' AND value_fingerprint IS NULL))",
		"CONSTRAINT ck_vault_access_action CHECK (action IN ('PROTECT','REVEAL'))",
		"CONSTRAINT ck_vault_access_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))",
		"ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
		"CHARACTER SET ascii COLLATE ascii_bin",
	)
	if actual := strings.Count(schema, "CHARACTER SET ascii COLLATE ascii_bin"); actual != 12 {
		t.Fatalf("ascii_bin machine identifier columns = %d, want 12", actual)
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
		if regexp.MustCompile(`^[a-z][a-z0-9_]* (BIGINT|VARCHAR|BINARY|BLOB|DATETIME)`).MatchString(trimmed) {
			fields++
		}
	}
	if fields != want {
		t.Fatalf("%s fields = %d, want %d", tableName, fields, want)
	}
}

func countFields(schema string) int {
	count := 0
	for _, tableName := range []string{"t_vault_record", "t_vault_protect_request", "t_vault_access_event"} {
		pattern := regexp.MustCompile(`(?s)CREATE TABLE ` + regexp.QuoteMeta(tableName) + ` \((.*?)\) ENGINE=InnoDB`)
		matches := pattern.FindStringSubmatch(schema)
		if len(matches) != 2 {
			continue
		}
		for _, line := range strings.Split(matches[1], "\n") {
			trimmed := strings.TrimSpace(line)
			if regexp.MustCompile(`^[a-z][a-z0-9_]* (BIGINT|VARCHAR|BINARY|BLOB|DATETIME)`).MatchString(trimmed) {
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
