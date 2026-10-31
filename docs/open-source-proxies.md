# 真·加速：开源代理方案调研

结论先放前面：**开源的"加速/代理"内核很多，而且都是成熟项目；但"把内核塞进浏览器"这条路不建议走**。
浏览器侧最合理的形态是「自己只当客户端」——用户在手机上跑一个代理 App，浏览器把 WebView 流量指向它的本地端口（我们 v1.4 已经做了这个能力）。

---

## 一、可用的开源内核

| 项目 | 类型 | 语言 | 许可证 | 能否嵌进 App | 说明 |
|---|---|---|---|---|---|
| **Mihomo**（Clash.Meta 分支） | 规则分流代理 | Go | GPL-3.0 | 技术上可以（编 .so） | 最流行的订阅格式，规则/节点生态最全 |
| **Clash**（原版） | 规则分流代理 | Go | GPL-3.0 | 可以 | 作者已归档，社区转向 Mihomo |
| **sing-box** | 通用代理平台 | Go | GPL-3.0-or-later（附名称限制条款） | 可以（有 `libsingbox` 先例） | 协议最全：VLESS/VMess/Trojan/Hysteria/ShadowTLS/WireGuard… |
| **Xray-core** | 抗审查代理 | Go | **MPL-2.0**（文件级 copyleft） | 可以（有 `libXray` 先例） | 商用友好度最高，REALITY/XTLS 都在这里 |
| **v2ray-core** | 代理内核 | Go | MIT | 可以 | 老牌，协议实现稳定 |
| **Hysteria 2** | 基于 QUIC 的加速 | Go | MIT | 可以 | 弱网/高丢包场景提升明显 |
| **gost** | 通用隧道/代理 | Go | MIT | 可以 | 支持 HTTP/SOCKS4/5/SS/转发链 |
| **shadowsocks-rust** | SS 客户端/服务端 | Rust | MIT | 可以 | 轻量 |
| **naiveproxy** | 借用 Chrome 网络栈 | C++ | BSD-3-Clause | 较难（要 Chromium 网络栈） | 抗封锁强，体积大 |
| **WireGuard**（wireguard-go / kernel） | L3 VPN | Go/C | MIT / GPL-2.0 | 可以（需 VpnService） | 全局 VPN，不是"网页加速" |
| **OpenVPN** | L3 VPN | C | GPL-2.0 | 可以（需 VpnService） | 老牌，握手重 |

> 许可证信息以各仓库 LICENSE 为准。sing-box 的 LICENSE 在 GPLv3 之上加了"不得暗示关联"的条款，所以 GitHub 显示为 NOASSERTION；Xray-core 是 MPL-2.0，属于文件级 copyleft，和 GPL 项目混合时义务较轻。

---

## 二、两条可行路线

### 路线 A：浏览器只做客户端（推荐，v1.4 已支持）

```
浏览器 WebView ──► 本地代理端口 127.0.0.1:7890 ──► 用户自己的代理 App（Mihomo / sing-box / Xray / Hysteria）
```

- 浏览器侧只实现「HTTP/SOCKS 代理配置」，不碰协议、不分发节点。
- 用户想加速就装一个开源客户端，不想用就关掉，符合"不要求一定输入"。
- 合规风险最低：浏览器不内置任何节点、订阅或绕过能力。
- 我们的实现：设置 → 网络 → 网络代理（`ProxyController`，只作用于本应用 WebView，不改系统网络）。

### 路线 B：把内核编进 App（不推荐，除非要做成"代理浏览器"）

技术上可行，社区已有先例（用 CGo 把 Xray/sing-box 编成 `libxray.so` / `libsingbox.so`）：

| 代价 | 具体 |
|---|---|
| **许可证** | GPL-3.0 内核（Mihomo/sing-box）会要求整个 App 以 GPL-3.0 发布；Xray 是 MPL-2.0，义务最轻 |
| **体积** | +20~40 MB（多架构 .so） |
| **权限** | 要么 `VpnService`（全局、常驻通知），要么只做本地 SOCKS + `ProxyController`（仅浏览器） |
| **维护** | 订阅解析、节点测速、规则更新、DNS 泄漏、IPv6、分应用代理……等于再造一个客户端 |
| **合规** | 内置节点/订阅在中国大陆有明确法律风险 |

如果哪天真要做，建议顺序：**Xray-core（MPL-2.0）→ 只做本地 SOCKS → 浏览器用 ProxyController 连它**，这样许可证压力最小，也不需要 VpnService。

---

## 三、对当前产品的建议

1. **现在**：保持路线 A。用户已有 Mihomo/sing-box 的，直接把 127.0.0.1:7890 填进来即可；没有的，浏览器不提示、不强制。
2. **"加速"的定位**：浏览器侧只做「超时救援 + 省流重载 + 走代理重试」，不承诺提速——真正的提速来自代理服务器和线路。
3. **以后若要做内置**：优先评估 Xray-core（MPL-2.0），用 `libXray` 的 CGo 桥；同时准备好 GPL 合规审查（如果换 Mihomo/sing-box）。
4. **不要**内置节点、订阅链接或任何"一键翻墙"入口。

---

## 四、参考

- Mihomo：<https://github.com/MetaCubeX/mihomo>
- sing-box：<https://github.com/SagerNet/sing-box>
- Xray-core：<https://github.com/XTLS/Xray-core>
- v2ray-core：<https://github.com/v2fly/v2ray-core>
- Hysteria：<https://github.com/apernet/hysteria>
- gost：<https://github.com/go-gost/gost>
- libXray（Android/HarmonyOS 集成参考）：<https://github.com/XTLS/libXray>
- wireguard-go：<https://github.com/WireGuard/wireguard-go>
