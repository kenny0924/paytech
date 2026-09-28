package mysql

import (
	"context"
	"database/sql/driver"
	"errors"
	mysqlDriver "github.com/go-sql-driver/mysql"
	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/domain"
	"io"
	"net"
	"regexp"
)

var indexSuffix = regexp.MustCompile(`for key '(?:[a-zA-Z0-9_]+\.)?(uk_vault_record_token|uk_vault_record_stable|uk_vault_protect_request)'$`)

func classify(err error) error {
	if err == nil {
		return nil
	}
	var mysqlError *mysqlDriver.MySQLError
	if errors.As(err, &mysqlError) {
		if mysqlError.Number == 1213 || mysqlError.Number == 1205 {
			return application.ErrRetryableContention
		}
		if mysqlError.Number == 1062 {
			if match := indexSuffix.FindStringSubmatch(mysqlError.Message); len(match) == 2 {
				return &application.DuplicateError{Kind: application.DuplicateKind(match[1])}
			}
		}
		return domain.NewError(domain.InternalError)
	}
	var networkError net.Error
	if errors.As(err, &networkError) || errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) || errors.Is(err, driver.ErrBadConn) || errors.Is(err, mysqlDriver.ErrInvalidConn) || errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
		return domain.NewError(domain.DependencyUnavailable)
	}
	return domain.NewError(domain.InternalError)
}
