// Package xraymobile 给 Android 提供一个最小的 Xray 内核外壳：
// 传入 Xray JSON 配置即启动，返回错误字符串（空串表示成功）。
package xraymobile

import (
	"strings"
	"sync"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	_ "github.com/xtls/xray-core/main/distro/all"
)

var (
	mu   sync.Mutex
	inst *core.Instance
)

// Start 启动内核；configJSON 为 Xray 标准 JSON 配置（含 inbounds/outbounds）。
// 返回空字符串表示成功，否则是错误信息。
func Start(configJSON string) string {
	mu.Lock()
	defer mu.Unlock()
	if inst != nil {
		return "already running"
	}
	cfg, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return "config: " + err.Error()
	}
	srv, err := core.New(cfg)
	if err != nil {
		return "new: " + err.Error()
	}
	if err := srv.Start(); err != nil {
		return "start: " + err.Error()
	}
	inst = srv
	return ""
}

// Stop 停止内核。
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	if inst != nil {
		_ = inst.Close()
		inst = nil
	}
}

// Running 是否在运行。
func Running() bool {
	mu.Lock()
	defer mu.Unlock()
	return inst != nil
}

// Version 返回内核版本号。
func Version() string {
	return core.Version()
}
