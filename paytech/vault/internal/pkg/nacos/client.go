package nacos

import (
	"net"
	"os"
	"path/filepath"
	"strconv"

	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/common/logger"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"

	"github.com/paytech/vault-service/internal/conf"
)

// NamingClient 是用到的官方 SDK 能力；调用（含 CloseClient）不可取消。
type NamingClient interface {
	RegisterInstance(vo.RegisterInstanceParam) (bool, error)
	DeregisterInstance(vo.DeregisterInstanceParam) (bool, error)
	CloseClient()
}

// officialClient 构造官方 SDK 命名客户端。
func officialClient(cfg conf.NacosConfig, root string) (NamingClient, error) {
	return newClient(cfg, root, func(p vo.NacosClientParam) (NamingClient, error) {
		return clients.NewNamingClient(p)
	})
}

// newClient 校验地址、拒绝环境中的云凭据、安装静默日志后构造 SDK 客户端；缓存与日志写入私有目录。
func newClient(cfg conf.NacosConfig, root string, construct func(vo.NacosClientParam) (NamingClient, error)) (NamingClient, error) {
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
	if err != nil || client == nil {
		if client != nil {
			client.CloseClient()
		}
		return nil, unavailable()
	}
	return client, nil
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
