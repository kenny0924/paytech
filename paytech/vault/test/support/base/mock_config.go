package base

import "time"

// PolicyRefreshInterval 是测试中 Vault 从数据库刷新策略的间隔。
const PolicyRefreshInterval = 100 * time.Millisecond

// mockConfig 是自动测试的完整配置清单（对应 Java application-mock.yml）：列出 internal/conf 读取的全部键。
//
// 动态值来源：VAULT_DB_DSN ← 环境变量 VAULT_TEST_DB_DSN；VAULT_ADMIN_CALLERS ← 本次运行的管理调用方；
// VAULT_LOCAL_AES_ROOT_KEY ← 本次随机生成。策略不来自文件，由 TestMapper 在启动前写入数据库。其余为固定值。
func mockConfig(dsn, adminCaller, rootKey string) map[string]string {
	return map[string]string{
		// 运行模式：test 允许 LOCAL AES；production 会拒绝启动。
		"VAULT_RUNTIME_MODE": "test",
		// 必须是合法地址；测试不监听该端口，由 httptest 承载同一 Handler。
		"VAULT_HTTP_ADDRESS": "127.0.0.1:19127",
		"VAULT_DB_DSN":       dsn,
		// 管理接口只接受本次运行的管理调用方；策略每 100 毫秒从数据库刷新一次。
		"VAULT_ADMIN_CALLERS":           adminCaller,
		"VAULT_POLICY_REFRESH_INTERVAL": PolicyRefreshInterval.String(),
		// LOCAL AES 根密钥（base64 的 32 字节）与非敏感密钥引用。
		"VAULT_LOCAL_AES_ROOT_KEY": rootKey,
		"VAULT_LOCAL_AES_KEY_ID":   "auto-test",
		// Snowflake 坐标：31/27 专属 ext/http（31/28 黑盒、31/29～31 内部包测试、31/26 本地 Mocker、
		// 31/22～25 ext/internalauth 的四个环境，由 Options 覆盖）。
		"VAULT_SNOWFLAKE_DATACENTER_ID": "31",
		"VAULT_SNOWFLAKE_WORKER_ID":     "27",
		// 自动测试不连接 Nacos；以下注册参数仅在启用时读取，此处列出完整键名。
		"VAULT_NACOS_ENABLED":        "false",
		"VAULT_NACOS_SERVER_ADDRESS": "",
		"VAULT_NACOS_NAMESPACE":      "",
		"VAULT_NACOS_GROUP":          "",
		"VAULT_NACOS_INSTANCE_IP":    "",
		"VAULT_NACOS_INSTANCE_PORT":  "",
		"VAULT_NACOS_USERNAME":       "",
		"VAULT_NACOS_PASSWORD":       "",
		// 内部调用 Token（D26）：默认 OFF，与生产默认一致；ext/internalauth 用 Options 覆盖为 DUAL / REQUIRED。
		"VAULT_INTERNAL_AUTH_MODE":     "OFF",
		"VAULT_INTERNAL_AUTH_JWKS_URL": "",
		"VAULT_INSTANCE_ID":            "",
	}
}
