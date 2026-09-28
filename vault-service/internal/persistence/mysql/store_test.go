package mysql

import (
	"context"
	"errors"
	"github.com/paytech/vault-service/internal/domain"
	"io"
	"testing"
	"time"
)

func TestConnectorConfigurationBoundsUTCAndSuppressesDriverLogs(t *testing.T) {
	options := Options{5 * time.Second, 30 * time.Second, 10, 5}
	cfg, err := driverConfig("user:synthetic-secret@tcp(127.0.0.1:13306)/mor_vault?parseTime=false&loc=Local&readTimeout=0s", options)
	if err != nil {
		t.Fatal(err)
	}
	if !cfg.ParseTime || cfg.Loc != time.UTC || cfg.Timeout != 5*time.Second || cfg.ReadTimeout != 30*time.Second || cfg.WriteTimeout != 30*time.Second || cfg.Params["time_zone"] != "'+00:00'" {
		t.Fatal("unsafe connector settings")
	}
	if _, ok := cfg.Logger.(quietLogger); !ok {
		t.Fatal("driver logger may leak raw failures")
	}
	cfg.Logger.Print("synthetic-sensitive-driver-canary")
}

func TestDatabaseFailuresAreSafeAndUnknownFailuresAreInternal(t *testing.T) {
	for _, tc := range []struct {
		err  error
		code domain.ErrorCode
	}{{errors.New("synthetic-canary"), domain.InternalError}, {io.EOF, domain.DependencyUnavailable}, {context.DeadlineExceeded, domain.DependencyUnavailable}} {
		got := classify(tc.err)
		if domain.CodeOf(got) != tc.code {
			t.Fatal("incorrect database failure category")
		}
		if errors.Is(got, tc.err) {
			t.Fatal("raw cause escaped")
		}
	}
}
func TestOpenRejectsInvalidPoolAndDSN(t *testing.T) {
	for _, options := range []Options{{time.Second, time.Second, -1, 0}, {time.Second, time.Second, 1, 2}, {time.Hour, time.Second, 1, 0}} {
		if s, err := Open(context.Background(), "synthetic-invalid", options); err == nil || s != nil {
			t.Fatal("unsafe pool accepted")
		}
	}
	if s, err := Open(context.Background(), "synthetic-invalid", Options{}); err == nil || s != nil {
		t.Fatal("invalid DSN accepted")
	}
}
