// Package nacos provides Local/Test-only cached registration readiness. The
// official SDK may retain UDP/reconnect work even after CloseClient returns;
// process teardown, not adapter Close, is the final resource-reclamation boundary.
package nacos

import (
	"context"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"github.com/paytech/vault-service/internal/config"
	"github.com/paytech/vault-service/internal/domain"
)

const (
	probeInterval = 5 * time.Second
	operationWait = 10 * time.Second
	startupWait   = 15 * time.Second
)

// Registrar owns exactly one construction attempt and serial SDK call stream.
// A timeout bounds callers/admission, never the non-cancelable SDK operation.
type Registrar struct {
	cfg        config.NacosConfig
	construct  func(config.NacosConfig, string) (NamingClient, error)
	ctx        context.Context
	cancel     context.CancelFunc
	once       sync.Once
	mu         sync.Mutex
	registered atomic.Bool
	initial    chan struct{}
	done       chan struct{}
	initialErr error
	closeErr   error
}

// New takes validated enabled configuration; it performs no SDK/network work.
func New(cfg config.NacosConfig) *Registrar { return newRegistrar(cfg, officialClient) }

func newRegistrar(cfg config.NacosConfig, construct func(config.NacosConfig, string) (NamingClient, error)) *Registrar {
	ctx, cancel := context.WithCancel(context.Background())
	return &Registrar{cfg: cfg, construct: construct, ctx: ctx, cancel: cancel, initial: make(chan struct{}), done: make(chan struct{})}
}

// Start's context bounds only initial registration. Successful registration
// lives until explicit Close, independently of the assembly startup deadline.
func (r *Registrar) Start(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, startupWait)
	defer cancel()
	if ctx.Err() != nil || r.ctx.Err() != nil {
		r.stop()
		return unavailable()
	}
	r.once.Do(func() { go r.own() })
	select {
	case <-ctx.Done():
		r.stop()
		return unavailable()
	case <-r.ctx.Done():
		return unavailable()
	case <-r.initial:
		if ctx.Err() != nil || r.ctx.Err() != nil {
			r.stop()
			return unavailable()
		}
		return r.initialErr
	}
}

// Registered reflects the SDK's cached/eventually-consistent instance view,
// not a fresh server confirmation or guaranteed five-second loss detection.
func (r *Registrar) Registered() bool { return r.registered.Load() }

func (r *Registrar) stop() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.cancel()
	r.registered.Store(false)
}

func (r *Registrar) publish(ready bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.ctx.Err() == nil {
		r.registered.Store(ready)
	}
}

// Close stops admission immediately and joins only the adapter owner, within
// the caller's budget. Released late SDK work performs ordered cleanup once.
func (r *Registrar) Close(ctx context.Context) error {
	r.stop()
	r.once.Do(func() { close(r.done) })
	ctx, cancel := context.WithTimeout(ctx, operationWait)
	defer cancel()
	select {
	case <-r.done:
		return r.closeErr
	case <-ctx.Done():
		return unavailable()
	}
}

func (r *Registrar) own() {
	defer close(r.done)
	defer r.stop()
	root, err := os.MkdirTemp("", "vault-nacos-")
	if err != nil {
		r.initialErr = unavailable()
		close(r.initial)
		return
	}
	var client NamingClient
	attempted := false
	defer func() { r.cleanup(client, attempted, root) }()
	if r.ctx.Err() != nil {
		return
	}
	client, err = r.construct(r.cfg, root)
	if err == nil && client != nil && r.ctx.Err() == nil {
		attempted = true
		if !r.register(client) {
			err = unavailable()
		}
	} else {
		err = unavailable()
	}
	if err != nil {
		r.initialErr = unavailable()
	}
	close(r.initial)
	if err == nil {
		r.maintain(client)
	}
}

func (r *Registrar) register(client NamingClient) bool {
	if r.ctx.Err() != nil {
		return false
	}
	deadline := time.AfterFunc(operationWait, r.stop)
	ok, err := client.RegisterInstance(vo.RegisterInstanceParam{
		ServiceName: "vault-service", GroupName: r.cfg.Group, Ip: r.cfg.InstanceIP, Port: uint64(r.cfg.InstancePort),
		ClusterName: "DEFAULT", Weight: 1, Enable: true, Healthy: true, Ephemeral: true,
	})
	deadline.Stop()
	r.publish(ok && err == nil)
	return r.Registered()
}

func (r *Registrar) observe(client NamingClient) {
	deadline := time.AfterFunc(operationWait, r.stop)
	instances, err := client.SelectHealthyInstances(vo.SelectInstancesParam{ServiceName: "vault-service", GroupName: r.cfg.Group, Clusters: []string{"DEFAULT"}, HealthyOnly: true})
	deadline.Stop()
	ready := false
	for _, instance := range instances {
		if err == nil && instance.Ip == r.cfg.InstanceIP && instance.Port == uint64(r.cfg.InstancePort) && instance.ClusterName == "DEFAULT" && instance.Ephemeral && instance.Healthy && instance.Enable {
			ready = true
			break
		}
	}
	r.publish(ready)
}

func (r *Registrar) maintain(client NamingClient) {
	delay, retry := probeInterval, time.Second
	for r.wait(delay) {
		if r.Registered() {
			r.observe(client)
			if !r.Registered() {
				delay = time.Second
			}
			continue
		}
		if r.register(client) {
			delay, retry = probeInterval, time.Second
			continue
		}
		retry = min(retry*2, 30*time.Second)
		delay = retry
	}
}

func (r *Registrar) wait(delay time.Duration) bool {
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-r.ctx.Done():
		return false
	case <-timer.C:
		return r.ctx.Err() == nil
	}
}

func (r *Registrar) cleanup(client NamingClient, attempted bool, root string) {
	r.stop()
	if client != nil {
		if attempted {
			ok, err := client.DeregisterInstance(vo.DeregisterInstanceParam{ServiceName: "vault-service", GroupName: r.cfg.Group, Ip: r.cfg.InstanceIP, Port: uint64(r.cfg.InstancePort), Cluster: "DEFAULT", Ephemeral: true})
			if !ok || err != nil {
				r.closeErr = unavailable()
			}
		}
		client.CloseClient()
	}
	// Exact MkdirTemp-owned root only, after ordered SDK calls. Upstream residual
	// work can outlive Close: removal is best effort, not proof of disk quiescence.
	if os.RemoveAll(root) != nil {
		r.closeErr = unavailable()
	}
}

func unavailable() error { return domain.NewError(domain.DependencyUnavailable) }
