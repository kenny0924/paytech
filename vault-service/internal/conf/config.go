package conf

import (
	"encoding/base64"
	"fmt"
	"net"
	"regexp"
	"strconv"
	"strings"

	"github.com/caarlos0/env/v11"

	"github.com/paytech/vault-service/internal/biz"
)

var keyIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)

// Config 保存校验后的运行配置；Secret 仅通过显式访问器读取，格式化时始终隐藏。
type Config struct {
	RuntimeMode           string
	HTTPAddress           string
	PolicyFile            string
	SnowflakeDatacenterID int64
	SnowflakeWorkerID     int64
	Nacos                 NacosConfig
	databaseDSN           secret
	localAESRootKey       secret
	localAESKeyID         string
}

// NacosConfig 将服务注册地址与 HTTP 监听地址分开配置。
type NacosConfig struct {
	Enabled       bool
	ServerAddress string
	Namespace     string
	Group         string
	InstanceIP    string
	InstancePort  int
	username      secret
	password      secret
}

// DatabaseDSN 仅供运行时建立 Vault 专属数据库连接，不得写入日志。
func (c Config) DatabaseDSN() string { return string(c.databaseDSN) }

// LocalAESRootKey 返回防御性副本，避免调用方修改配置中的根密钥。
func (c Config) LocalAESRootKey() []byte { return append([]byte(nil), c.localAESRootKey...) }

func (c Config) LocalAESKeyID() string { return c.localAESKeyID }

func (c Config) String() string { return "vault runtime configuration [REDACTED]" }

func (c Config) GoString() string { return c.String() }

func (c NacosConfig) Username() string { return string(c.username) }

func (c NacosConfig) Password() string { return string(c.password) }

func (c NacosConfig) String() string { return "Nacos configuration [REDACTED]" }

func (c NacosConfig) GoString() string { return c.String() }

type secret []byte

func (secret) String() string   { return "[REDACTED]" }
func (secret) GoString() string { return "[REDACTED]" }

// environment 声明 Vault 读取的全部环境变量（≈ @ConfigurationProperties）。
//
// 解析后转入 Config：密钥类值放进私有字段并在格式化时脱敏。连接池参数不从环境读取，见 data.DefaultOptions。
type environment struct {
	RuntimeMode           string           `env:"VAULT_RUNTIME_MODE,required"`
	HTTPAddress           string           `env:"VAULT_HTTP_ADDRESS" envDefault:"127.0.0.1:18107"`
	PolicyFile            string           `env:"VAULT_POLICY_FILE,required"`
	DatabaseDSN           string           `env:"VAULT_DB_DSN,required"`
	LocalAESRootKey       string           `env:"VAULT_LOCAL_AES_ROOT_KEY,required"`
	LocalAESKeyID         string           `env:"VAULT_LOCAL_AES_KEY_ID,required"`
	SnowflakeDatacenterID uint8            `env:"VAULT_SNOWFLAKE_DATACENTER_ID,required"`
	SnowflakeWorkerID     uint8            `env:"VAULT_SNOWFLAKE_WORKER_ID,required"`
	Nacos                 nacosEnvironment `envPrefix:"VAULT_NACOS_"`
}

// nacosEnvironment 声明服务注册参数；除 ENABLED 外只在启用时校验。
type nacosEnvironment struct {
	Enabled       bool   `env:"ENABLED,required"`
	ServerAddress string `env:"SERVER_ADDRESS"`
	Namespace     string `env:"NAMESPACE"`
	Group         string `env:"GROUP" envDefault:"DEFAULT_GROUP"`
	InstanceIP    string `env:"INSTANCE_IP"`
	InstancePort  string `env:"INSTANCE_PORT"`
	Username      string `env:"USERNAME"`
	Password      string `env:"PASSWORD"`
}

// Load 只读取声明过的 VAULT_* 环境变量（空值视为未设置），解析并校验后返回配置；任何错误都不暴露原值。
func Load(getenv func(string) string) (Config, error) {
	if getenv == nil {
		return Config{}, startupError()
	}
	values, err := declaredValues(getenv)
	if err != nil {
		return Config{}, startupError()
	}
	var e environment
	if env.ParseWithOptions(&e, env.Options{Environment: values}) != nil {
		return Config{}, startupError()
	}
	config, err := e.toConfig()
	if err != nil || validateBaseConfig(config) != nil {
		return Config{}, startupError()
	}
	return config, nil
}

// declaredValues 只取 environment 声明的键，不读取其他环境变量。
func declaredValues(getenv func(string) string) (map[string]string, error) {
	params, err := env.GetFieldParams(&environment{})
	if err != nil {
		return nil, err
	}
	values := make(map[string]string, len(params))
	for _, param := range params {
		if value := getenv(param.Key); value != "" {
			values[param.Key] = value
		}
	}
	return values, nil
}

// toConfig 转为运行配置：解码根密钥、校验 Snowflake 范围，启用 Nacos 时校验完整注册参数。
func (e environment) toConfig() (Config, error) {
	rootKey, err := decodeRootKey(e.LocalAESRootKey)
	if err != nil || e.SnowflakeDatacenterID > 31 || e.SnowflakeWorkerID > 31 {
		return Config{}, fmt.Errorf("invalid configuration")
	}
	config := Config{
		RuntimeMode: e.RuntimeMode, HTTPAddress: e.HTTPAddress, PolicyFile: e.PolicyFile,
		SnowflakeDatacenterID: int64(e.SnowflakeDatacenterID), SnowflakeWorkerID: int64(e.SnowflakeWorkerID),
		databaseDSN: secret(e.DatabaseDSN), localAESRootKey: secret(rootKey), localAESKeyID: e.LocalAESKeyID,
	}
	config.Nacos, err = e.Nacos.toConfig()
	return config, err
}

// toConfig 转为注册配置；未启用时只保留 Group 默认值。
func (n nacosEnvironment) toConfig() (NacosConfig, error) {
	config := NacosConfig{Enabled: n.Enabled, Group: n.Group}
	if !n.Enabled {
		return config, nil
	}
	port, err := positivePort(n.InstancePort)
	if err != nil || !validAddress(n.ServerAddress) || !validNacosIdentifier(n.Namespace) || !validNacosIdentifier(n.Group) ||
		net.ParseIP(n.InstanceIP) == nil || n.Username == "" || n.Password == "" {
		return NacosConfig{}, fmt.Errorf("invalid Nacos configuration")
	}
	config.ServerAddress, config.Namespace, config.InstanceIP, config.InstancePort = n.ServerAddress, n.Namespace, n.InstanceIP, port
	config.username, config.password = secret(n.Username), secret(n.Password)
	return config, nil
}

// validateBaseConfig 校验运行模式、监听地址、策略文件、DSN 与密钥引用；production 拒绝 LOCAL AES。
func validateBaseConfig(config Config) error {
	if !validRuntimeMode(config.RuntimeMode) || !validAddress(config.HTTPAddress) ||
		config.PolicyFile == "" || len(config.databaseDSN) == 0 || !keyIDPattern.MatchString(config.localAESKeyID) {
		return startupError()
	}
	if config.RuntimeMode == "production" {
		return startupError()
	}
	return nil
}

// Nacos 会把 Namespace 拼接到命名缓存目录；构造 SDK 前拒绝非法标识符，不能将其修正为路径。
func validNacosIdentifier(value string) bool { return keyIDPattern.MatchString(value) }

func decodeRootKey(encoded string) ([]byte, error) {
	rootKey, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil || len(rootKey) != 32 {
		return nil, fmt.Errorf("invalid root key")
	}
	return rootKey, nil
}

func positivePort(value string) (int, error) {
	port, err := strconv.Atoi(value)
	if err != nil || port < 1 || port > 65535 {
		return 0, fmt.Errorf("invalid port")
	}
	return port, nil
}

func validRuntimeMode(mode string) bool {
	return mode == "local" || mode == "test" || mode == "production"
}

func validAddress(address string) bool {
	host, port, err := net.SplitHostPort(address)
	if err != nil || host == "" || strings.TrimSpace(host) != host {
		return false
	}
	_, err = positivePort(port)
	return err == nil
}

func startupError() error { return biz.NewError(biz.InternalError) }
