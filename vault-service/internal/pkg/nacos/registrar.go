// Package nacos 用官方 nacos-sdk-go 注册 vault-service 临时实例。
//
// 启动时注册一次，停机时注销并关闭客户端。临时实例的存活由 SDK 的 gRPC 长连接维持，
// 断线重连后的重新注册由 SDK 负责，本包不另做探测或重试。SDK 调用不可取消，ctx 只限定等待时长。
package nacos

import (
	"context"
	"os"

	"github.com/nacos-group/nacos-sdk-go/v2/vo"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/conf"
)

const (
	serviceName = "vault-service"
	clusterName = "DEFAULT"
)

// Registrar 持有已注册实例的 SDK 客户端与私有缓存目录。
type Registrar struct {
	cfg    conf.NacosConfig
	client NamingClient
	root   string
}

// Register 构造官方 SDK 客户端并注册实例；失败时注销已尝试的实例并清理资源。
func Register(ctx context.Context, cfg conf.NacosConfig) (*Registrar, error) {
	return register(ctx, cfg, officialClient)
}

func register(ctx context.Context, cfg conf.NacosConfig, construct func(conf.NacosConfig, string) (NamingClient, error)) (*Registrar, error) {
	root, err := os.MkdirTemp("", "vault-nacos-")
	if err != nil {
		return nil, unavailable()
	}
	r := &Registrar{cfg: cfg, root: root}
	started := make(chan error, 1)
	go func() { started <- r.start(construct) }()
	select {
	case err := <-started:
		if err != nil {
			return nil, unavailable()
		}
		return r, nil
	case <-ctx.Done():
		// 启动超时：SDK 调用仍在进行，结束后若已注册则注销，保证不遗留实例。
		go func() {
			if <-started == nil {
				_ = r.shutdown()
			}
		}()
		return nil, unavailable()
	}
}

// start 构造客户端并注册；注册失败时按“注销已尝试的身份 → 关闭客户端 → 删除目录”清理。
func (r *Registrar) start(construct func(conf.NacosConfig, string) (NamingClient, error)) error {
	client, err := construct(r.cfg, r.root)
	if err != nil {
		_ = os.RemoveAll(r.root)
		return unavailable()
	}
	r.client = client
	ok, err := client.RegisterInstance(vo.RegisterInstanceParam{
		ServiceName: serviceName, GroupName: r.cfg.Group, ClusterName: clusterName,
		Ip: r.cfg.InstanceIP, Port: uint64(r.cfg.InstancePort), Weight: 1, Enable: true, Healthy: true, Ephemeral: true,
	})
	if !ok || err != nil {
		_ = r.shutdown()
		return unavailable()
	}
	return nil
}

// Close 注销实例、关闭客户端并删除私有目录；ctx 到期即返回，SDK 调用稍后自行结束。
func (r *Registrar) Close(ctx context.Context) error {
	done := make(chan error, 1)
	go func() { done <- r.shutdown() }()
	select {
	case err := <-done:
		return err
	case <-ctx.Done():
		return unavailable()
	}
}

// shutdown 依次注销、关闭客户端、删除目录；任一步失败都继续后续步骤并返回安全错误。
func (r *Registrar) shutdown() error {
	ok, err := r.client.DeregisterInstance(vo.DeregisterInstanceParam{
		ServiceName: serviceName, GroupName: r.cfg.Group, Cluster: clusterName,
		Ip: r.cfg.InstanceIP, Port: uint64(r.cfg.InstancePort), Ephemeral: true,
	})
	r.client.CloseClient()
	removeErr := os.RemoveAll(r.root)
	if !ok || err != nil || removeErr != nil {
		return unavailable()
	}
	return nil
}

func unavailable() error { return biz.NewError(biz.DependencyUnavailable) }
