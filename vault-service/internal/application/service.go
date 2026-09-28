package application

import (
	"context"
	"errors"
	"io"
	"regexp"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/paytech/vault-service/internal/domain"
)

type IDGenerator interface {
	Next(context.Context) (int64, error)
}
type Authorizer interface {
	DataTypeMode(string) (domain.TokenMode, bool)
	Authorize(string, domain.Action, string, string) bool
	Version() string
}
type Attempt struct {
	RequestID, TraceID, Caller string
	Action                     domain.Action
}
type Service struct {
	store    Store
	keys     KeyService
	ids      IDGenerator
	policy   Authorizer
	random   io.Reader
	clock    func() time.Time
	randomMu sync.Mutex
}

func NewService(store Store, keys KeyService, ids IDGenerator, policy Authorizer, random io.Reader, clock func() time.Time) *Service {
	return &Service{store: store, keys: keys, ids: ids, policy: policy, random: random, clock: clock}
}

var (
	codePattern   = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
	callerPattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,127}$`)
	keyPattern    = regexp.MustCompile(`^[A-Za-z0-9._~-]{1,128}$`)
	tokenPattern  = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	tracePattern  = regexp.MustCompile(`^[0-9a-f]{32}$`)
)

func validateProtect(a Attempt, in domain.ProtectInput) error {
	if !callerPattern.MatchString(a.Caller) || !codePattern.MatchString(in.DataType) || !codePattern.MatchString(in.Purpose) || !keyPattern.MatchString(in.IdempotencyKey) || !utf8.Valid(in.Value) {
		return domain.NewError(domain.InvalidRequest)
	}
	if in.TokenMode != domain.Reference && in.TokenMode != domain.Stable {
		return domain.NewError(domain.InvalidRequest)
	}
	if len(in.Value) > 32*1024 {
		return domain.NewError(domain.ValueTooLarge)
	}
	return nil
}

func safeError(err error) error {
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return domain.NewError(domain.DependencyUnavailable)
	}
	return domain.NewError(domain.CodeOf(err))
}

func (s *Service) nextID(ctx context.Context) (int64, error) {
	n, err := s.ids.Next(ctx)
	if err != nil {
		return 0, safeError(err)
	}
	if n <= 0 {
		return 0, domain.NewError(domain.InternalError)
	}
	return n, nil
}

func (s *Service) nextToken(ctx context.Context) (string, error) {
	s.randomMu.Lock()
	defer s.randomMu.Unlock()
	if err := ctx.Err(); err != nil {
		return "", safeError(err)
	}
	return domain.NewToken(s.random)
}

// wipe is best effort only: Go and cryptographic libraries may retain copies.
//
//go:noinline
func wipe(value []byte) { clear(value) }
