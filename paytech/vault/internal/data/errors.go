package data

import (
	"context"
	"database/sql/driver"
	"errors"
	"io"
	"net"
	"regexp"

	mysqlDriver "github.com/go-sql-driver/mysql"

	"github.com/paytech/vault-service/internal/biz"
)

var indexSuffix = regexp.MustCompile(`for key '(?:[a-zA-Z0-9_]+\.)?(uk_vault_record_token|uk_vault_record_stable|uk_vault_protect_request|uk_vault_data_type_code|uk_vault_access_rule|uk_vault_policy_version)'$`)

// classify 把驱动错误归类为可重试竞争、已知唯一冲突或安全的领域错误，不外泄驱动原文。
func classify(err error) error {
	if err == nil {
		return nil
	}
	var mysqlError *mysqlDriver.MySQLError
	if errors.As(err, &mysqlError) {
		return classifyMySQL(mysqlError)
	}
	var networkError net.Error
	if errors.As(err, &networkError) || errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) || errors.Is(err, driver.ErrBadConn) || errors.Is(err, mysqlDriver.ErrInvalidConn) || errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
		return biz.NewError(biz.DependencyUnavailable)
	}
	return biz.NewError(biz.InternalError)
}

// classifyMySQL 归类服务端错误：死锁/锁等待超时可重试，已知唯一索引冲突可恢复，其余为内部错误。
func classifyMySQL(mysqlError *mysqlDriver.MySQLError) error {
	switch mysqlError.Number {
	case 1213, 1205:
		return biz.ErrRetryableContention
	case 1062:
		if match := indexSuffix.FindStringSubmatch(mysqlError.Message); len(match) == 2 {
			return &biz.DuplicateError{Kind: biz.DuplicateKind(match[1])}
		}
	}
	return biz.NewError(biz.InternalError)
}
