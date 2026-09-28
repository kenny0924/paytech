package nacos

import (
	"net"
	"os"
	"path/filepath"
	"strconv"

	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/common/logger"
	"github.com/nacos-group/nacos-sdk-go/v2/model"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"github.com/paytech/vault-service/internal/config"
)

// NamingClient is the used official SDK boundary. Its calls, including Close,
// are not context-cancelable and must have a single sequential owner.
type NamingClient interface {
	RegisterInstance(vo.RegisterInstanceParam) (bool, error)
	DeregisterInstance(vo.DeregisterInstanceParam) (bool, error)
	SelectHealthyInstances(vo.SelectInstancesParam) ([]model.Instance, error)
	CloseClient()
}

type sdkNamingClient interface {
	RegisterInstance(vo.RegisterInstanceParam) (bool, error)
	DeregisterInstance(vo.DeregisterInstanceParam) (bool, error)
	SelectInstances(vo.SelectInstancesParam) ([]model.Instance, error)
	CloseClient()
}

type sdkClient struct{ sdkNamingClient }

func (c sdkClient) SelectHealthyInstances(p vo.SelectInstancesParam) ([]model.Instance, error) {
	p.HealthyOnly = true
	return c.SelectInstances(p)
}

func officialClient(cfg config.NacosConfig, root string) (NamingClient, error) {
	return newClient(cfg, root, func(p vo.NacosClientParam) (sdkNamingClient, error) {
		return clients.NewNamingClient(p)
	})
}

func newClient(cfg config.NacosConfig, root string, construct func(vo.NacosClientParam) (sdkNamingClient, error)) (NamingClient, error) {
	// SDK v2.3.5 appends custom RAM providers after its ambient-key fallback;
	// it cannot opt out. Refuse that credential source before any SDK setup.
	if os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_ID") != "" || os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET") != "" {
		return nil, unavailable()
	}
	host, portText, err := net.SplitHostPort(cfg.ServerAddress)
	if err != nil {
		return nil, unavailable()
	}
	port, err := strconv.ParseUint(portText, 10, 16)
	if err != nil || port == 0 {
		return nil, unavailable()
	}
	// Vault owns the only SDK use in this process. LogLevel alone is unsafe:
	// SDK registration logs contain entire instance/config/request objects.
	logger.SetLogger(noopLogger{})
	client, err := construct(vo.NacosClientParam{
		ServerConfigs: []constant.ServerConfig{{IpAddr: host, Port: port, Scheme: "http"}},
		ClientConfig: &constant.ClientConfig{
			NamespaceId: cfg.Namespace, Username: cfg.Username(), Password: cfg.Password(),
			TimeoutMs: 5000, CacheDir: filepath.Join(root, "cache"), LogDir: filepath.Join(root, "log"),
			NotLoadCacheAtStart: true, UpdateCacheWhenEmpty: true,
			TLSCfg: constant.TLSConfig{Appointed: true, Enable: false},
		},
	})
	if err != nil {
		if client != nil {
			return sdkClient{client}, unavailable()
		}
		return nil, unavailable()
	}
	if client == nil {
		return nil, unavailable()
	}
	return sdkClient{client}, nil
}

type noopLogger struct{}

func (noopLogger) Info(...interface{})           {}
func (noopLogger) Warn(...interface{})           {}
func (noopLogger) Error(...interface{})          {}
func (noopLogger) Debug(...interface{})          {}
func (noopLogger) Infof(string, ...interface{})  {}
func (noopLogger) Warnf(string, ...interface{})  {}
func (noopLogger) Errorf(string, ...interface{}) {}
func (noopLogger) Debugf(string, ...interface{}) {}
func (noopLogger) Close() error                  { return nil }
