package config

import (
	"encoding/base64"
	"fmt"
	"net"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/paytech/vault-service/internal/domain"
)

const (
	defaultHTTPAddress = "127.0.0.1:18107"
	defaultNacosGroup  = "DEFAULT_GROUP"

	defaultDatabaseConnectTimeout = 5 * time.Second
	defaultDatabaseQueryTimeout   = 30 * time.Second
	defaultDatabaseMaxOpenConns   = 10
	defaultDatabaseMaxIdleConns   = 5
)

var keyIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)

// Config 保存校验后的运行配置；Secret 仅通过显式访问器读取，格式化时始终隐藏。
type Config struct {
	RuntimeMode            string
	HTTPAddress            string
	PolicyFile             string
	SnowflakeDatacenterID  int64
	SnowflakeWorkerID      int64
	DatabaseConnectTimeout time.Duration
	DatabaseQueryTimeout   time.Duration
	DatabaseMaxOpenConns   int
	DatabaseMaxIdleConns   int
	Nacos                  NacosConfig
	databaseDSN            secret
	localAESRootKey        secret
	localAESKeyID          string
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

// Load 只读取已冻结的 VAULT_* 环境变量，不引入隐式配置来源。
func Load(getenv func(string) string) (Config, error) {
	if getenv == nil {
		return Config{}, startupError()
	}
	config, err := loadBaseConfig(getenv)
	if err != nil {
		return Config{}, err
	}
	if err := validateBaseConfig(config); err != nil {
		return Config{}, err
	}
	if config.SnowflakeDatacenterID, err = snowflakeID(getenv("VAULT_SNOWFLAKE_DATACENTER_ID")); err != nil {
		return Config{}, startupError()
	}
	if config.SnowflakeWorkerID, err = snowflakeID(getenv("VAULT_SNOWFLAKE_WORKER_ID")); err != nil {
		return Config{}, startupError()
	}
	if config.Nacos.Enabled, err = parseBool(getenv("VAULT_NACOS_ENABLED")); err != nil {
		return Config{}, startupError()
	}
	if err := loadNacos(&config.Nacos, getenv); err != nil {
		return Config{}, startupError()
	}
	return config, nil
}

func loadBaseConfig(getenv func(string) string) (Config, error) {
	config := Config{
		RuntimeMode:            getenv("VAULT_RUNTIME_MODE"),
		HTTPAddress:            valueOrDefault(getenv("VAULT_HTTP_ADDRESS"), defaultHTTPAddress),
		PolicyFile:             getenv("VAULT_POLICY_FILE"),
		DatabaseConnectTimeout: defaultDatabaseConnectTimeout,
		DatabaseQueryTimeout:   defaultDatabaseQueryTimeout,
		DatabaseMaxOpenConns:   defaultDatabaseMaxOpenConns,
		DatabaseMaxIdleConns:   defaultDatabaseMaxIdleConns,
		Nacos: NacosConfig{
			Group: valueOrDefault(getenv("VAULT_NACOS_GROUP"), defaultNacosGroup),
		},
		databaseDSN:   secret(getenv("VAULT_DB_DSN")),
		localAESKeyID: getenv("VAULT_LOCAL_AES_KEY_ID"),
	}
	rootKey, err := decodeRootKey(getenv("VAULT_LOCAL_AES_ROOT_KEY"))
	if err != nil {
		return Config{}, startupError()
	}
	config.localAESRootKey = secret(rootKey)
	return config, nil
}

func validateBaseConfig(config Config) error {
	if !validRuntimeMode(config.RuntimeMode) || !validAddress(config.HTTPAddress) ||
		config.PolicyFile == "" || len(config.databaseDSN) == 0 || !keyIDPattern.MatchString(config.localAESKeyID) ||
		!validDatabaseDefaults(config) {
		return startupError()
	}
	if config.RuntimeMode == "production" {
		return startupError()
	}
	return nil
}

func loadNacos(config *NacosConfig, getenv func(string) string) error {
	if !config.Enabled {
		return nil
	}
	config.ServerAddress = getenv("VAULT_NACOS_SERVER_ADDRESS")
	config.Namespace = getenv("VAULT_NACOS_NAMESPACE")
	config.InstanceIP = getenv("VAULT_NACOS_INSTANCE_IP")
	config.username = secret(getenv("VAULT_NACOS_USERNAME"))
	config.password = secret(getenv("VAULT_NACOS_PASSWORD"))
	port, err := positivePort(getenv("VAULT_NACOS_INSTANCE_PORT"))
	if err != nil || !validAddress(config.ServerAddress) || !validNacosIdentifier(config.Namespace) ||
		!validNacosIdentifier(config.Group) || net.ParseIP(config.InstanceIP) == nil || len(config.username) == 0 || len(config.password) == 0 {
		return fmt.Errorf("invalid Nacos configuration")
	}
	config.InstancePort = port
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

func snowflakeID(value string) (int64, error) {
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed < 0 || parsed > 31 {
		return 0, fmt.Errorf("invalid Snowflake ID")
	}
	return parsed, nil
}

func parseBool(value string) (bool, error) {
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return false, fmt.Errorf("invalid boolean")
	}
	return parsed, nil
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

func validDatabaseDefaults(config Config) bool {
	return config.DatabaseConnectTimeout > 0 && config.DatabaseConnectTimeout <= 30*time.Second &&
		config.DatabaseQueryTimeout > 0 && config.DatabaseQueryTimeout <= time.Minute &&
		config.DatabaseMaxOpenConns > 0 && config.DatabaseMaxOpenConns <= 100 &&
		config.DatabaseMaxIdleConns >= 0 && config.DatabaseMaxIdleConns <= config.DatabaseMaxOpenConns
}

func valueOrDefault(value, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func startupError() error { return domain.NewError(domain.InternalError) }
