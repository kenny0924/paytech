package observability

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/collectors"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/paytech/vault-service/internal/biz"
)

type metrics struct {
	registry                     *prometheus.Registry
	requests                     *prometheus.CounterVec
	requestTime, dbTime, keyTime *prometheus.HistogramVec
	auditUnavailable             prometheus.Counter
	races                        *prometheus.CounterVec
}

func newMetrics() *metrics {
	labels := []string{"action", "outcome", "result_code", "http_status"}
	m := &metrics{registry: prometheus.NewRegistry(),
		requests:         prometheus.NewCounterVec(prometheus.CounterOpts{Name: "vault_requests_total", Help: "Completed Vault business HTTP attempts."}, labels),
		requestTime:      prometheus.NewHistogramVec(prometheus.HistogramOpts{Name: "vault_request_duration_seconds", Help: "Vault business HTTP duration."}, labels),
		dbTime:           prometheus.NewHistogramVec(prometheus.HistogramOpts{Name: "vault_database_duration_seconds", Help: "Vault database operation duration."}, []string{"action", "result_code"}),
		keyTime:          prometheus.NewHistogramVec(prometheus.HistogramOpts{Name: "vault_key_duration_seconds", Help: "Vault key operation duration."}, []string{"action", "result_code"}),
		auditUnavailable: prometheus.NewCounter(prometheus.CounterOpts{Name: "vault_audit_unavailable_total", Help: "Attempts for which audit durability is unavailable."}),
		races:            prometheus.NewCounterVec(prometheus.CounterOpts{Name: "vault_duplicate_races_total", Help: "Observed unique-key contention."}, []string{"action"}),
	}
	m.registry.MustRegister(m.requests, m.requestTime, m.dbTime, m.keyTime, m.auditUnavailable, m.races, collectors.NewGoCollector(), collectors.NewProcessCollector(collectors.ProcessCollectorOpts{}))
	return m
}
func (m *metrics) request(p biz.AuditProjection, status int, duration time.Duration) {
	values := []string{string(p.Action), string(p.Outcome), p.ResultCode, strconv.Itoa(status)}
	m.requests.WithLabelValues(values...).Inc()
	m.requestTime.WithLabelValues(values...).Observe(duration.Seconds())
	if p.ResultCode == string(biz.AuditUnavailable) {
		m.auditUnavailable.Inc()
	}
}
func (r *Runtime) MetricsHandler() http.Handler {
	return promhttp.HandlerFor(r.metrics.registry, promhttp.HandlerOpts{ErrorHandling: promhttp.ContinueOnError})
}

func (r *Runtime) observe(ctx context.Context, action string, key bool) (context.Context, func(error)) {
	start := time.Now()
	prefix := "vault.mysql."
	hist := r.metrics.dbTime
	if key {
		prefix = "vault.key."
		hist = r.metrics.keyTime
	}
	ctx, end := r.dependency(ctx, prefix+action)
	return ctx, func(err error) {
		result := "SUCCESS"
		if err != nil {
			result = string(biz.CodeOf(err))
		}
		hist.WithLabelValues(action, result).Observe(time.Since(start).Seconds())
		end(err)
		var duplicate *biz.DuplicateError
		if (action == "INSERT_RECORD" || action == "INSERT_PROTECT_REQUEST") && errors.As(err, &duplicate) && duplicate != nil {
			switch duplicate.Kind {
			case biz.DuplicateStable:
				r.metrics.races.WithLabelValues("STABLE").Inc()
			case biz.DuplicateProtectRequest:
				r.metrics.races.WithLabelValues("IDEMPOTENCY").Inc()
			}
		}
	}
}

// Observed ports preserve results/errors and transaction ownership. They never
// inspect payloads or pass SQL, tokens, key references or causes to telemetry.
type observedStore struct {
	store biz.Store
	r     *Runtime
}
type observedTx struct {
	tx biz.TxStore
	r  *Runtime
}

func (r *Runtime) ObserveStore(store biz.Store) biz.Store {
	return &observedStore{store, r}
}
func (s *observedStore) Ping(ctx context.Context) (err error) {
	ctx, end := s.r.observe(ctx, "PING", false)
	defer func() { end(err) }()
	return s.store.Ping(ctx)
}
func (s *observedStore) FindRecordByToken(ctx context.Context, token string) (record biz.Record, found bool, err error) {
	ctx, end := s.r.observe(ctx, "FIND_RECORD", false)
	defer func() { end(err) }()
	return s.store.FindRecordByToken(ctx, token)
}
func (s *observedStore) InTransaction(ctx context.Context, fn func(biz.TxStore) error) (err error) {
	ctx, end := s.r.observe(ctx, "TRANSACTION", false)
	defer func() { end(err) }()
	return s.store.InTransaction(ctx, func(tx biz.TxStore) error { return fn(&observedTx{tx, s.r}) })
}
func (s *observedTx) FindProtectRequestForUpdate(ctx context.Context, caller, key string) (record biz.ProtectRequest, found bool, err error) {
	ctx, end := s.r.observe(ctx, "FIND_PROTECT_REQUEST", false)
	defer func() { end(err) }()
	return s.tx.FindProtectRequestForUpdate(ctx, caller, key)
}
func (s *observedTx) FindRecordByID(ctx context.Context, id int64) (record biz.Record, found bool, err error) {
	ctx, end := s.r.observe(ctx, "FIND_RECORD_ID", false)
	defer func() { end(err) }()
	return s.tx.FindRecordByID(ctx, id)
}
func (s *observedTx) FindRecordByStableIdentityForShare(ctx context.Context, dataType string, hash [32]byte) (record biz.Record, found bool, err error) {
	ctx, end := s.r.observe(ctx, "FIND_STABLE_RECORD", false)
	defer func() { end(err) }()
	return s.tx.FindRecordByStableIdentityForShare(ctx, dataType, hash)
}
func (s *observedTx) InsertRecord(ctx context.Context, record biz.Record) (err error) {
	ctx, end := s.r.observe(ctx, "INSERT_RECORD", false)
	defer func() { end(err) }()
	return s.tx.InsertRecord(ctx, record)
}
func (s *observedTx) InsertProtectRequest(ctx context.Context, request biz.ProtectRequest) (err error) {
	ctx, end := s.r.observe(ctx, "INSERT_PROTECT_REQUEST", false)
	defer func() { end(err) }()
	return s.tx.InsertProtectRequest(ctx, request)
}
func (s *observedTx) InsertAccessEvent(ctx context.Context, event biz.AccessEvent) (err error) {
	ctx, end := s.r.observe(ctx, "INSERT_ACCESS_EVENT", false)
	defer func() { end(err) }()
	return s.tx.InsertAccessEvent(ctx, event)
}

type observedKeys struct {
	keys biz.KeyService
	r    *Runtime
}

func (r *Runtime) ObserveKeys(keys biz.KeyService) biz.KeyService {
	return &observedKeys{keys, r}
}
func (k *observedKeys) Ready(ctx context.Context) (err error) {
	ctx, end := k.r.observe(ctx, "READY", true)
	defer func() { end(err) }()
	return k.keys.Ready(ctx)
}
func (k *observedKeys) Encrypt(ctx context.Context, aad, value []byte) (encrypted biz.EncryptedValue, err error) {
	ctx, end := k.r.observe(ctx, "ENCRYPT", true)
	defer func() { end(err) }()
	return k.keys.Encrypt(ctx, aad, value)
}
func (k *observedKeys) Decrypt(ctx context.Context, aad []byte, encrypted biz.EncryptedValue) (value []byte, err error) {
	ctx, end := k.r.observe(ctx, "DECRYPT", true)
	defer func() { end(err) }()
	return k.keys.Decrypt(ctx, aad, encrypted)
}
func (k *observedKeys) MAC(ctx context.Context, purpose biz.MACPurpose, material []byte) (mac [32]byte, err error) {
	ctx, end := k.r.observe(ctx, "MAC", true)
	defer func() { end(err) }()
	return k.keys.MAC(ctx, purpose, material)
}
