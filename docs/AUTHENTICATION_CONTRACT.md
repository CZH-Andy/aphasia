# 认证接口契约与迁移

本文档记录密码登录、Token 认证、错误语义和旧接口退役边界。认证响应包含可直接访问账号数据的 Token，生产环境必须使用 HTTPS。

## 当前接口

| 接口 | 用途 | 凭据位置 | 成功响应 |
|---|---|---|---|
| `POST /api/auth/login` | 密码登录 | JSON body | 用户信息和新 Token |
| `POST /api/auth/token` | 使用已有 Token 恢复会话 | `Token` header | 用户信息和新 Token |
| `POST /api/auth` | 旧客户端兼容入口 | 仅 `Token` header | 用户信息和新 Token |
| `POST /api/register` | 公开注册患者账号 | JSON body | 用户信息和新 Token |

密码登录请求体：

```json
{
  "identity": "patient@example.com",
  "password": "用户输入的密码"
}
```

`identity` 最长 254 个字符，`password` 最长 128 个字符，二者都不能为空。客户端登录时必须关闭自动 Token 附加，避免把旧会话与密码登录混在同一个请求中。

成功响应中的 `password` 始终为空且不会序列化。登录、Token 认证和注册成功响应均包含：

```http
Cache-Control: no-store
Pragma: no-cache
```

## 错误语义

- JSON 缺字段、字段超长或格式错误：HTTP 400。
- 未知账号和错误密码：统一返回 HTTP 401，消息为 `用户名或密码错误`，不向调用方暴露账号是否存在。
- Token 缺失：HTTP 401，消息为 `缺少Token`。
- Token 无效、过期或对应账号已删除：统一返回 HTTP 401，消息为 `Token无效或已过期`。
- 其他未处理异常：HTTP 500，响应不包含内部异常细节。

服务端对未知账号也执行一次 BCrypt 校验，以降低通过响应耗时枚举账号的风险。该措施不能替代登录限流和异常登录告警。

## 旧接口兼容边界

旧 `POST /api/auth` 已收窄为 token-only：

- 继续支持已安装旧客户端使用保存的 Token 恢复会话；
- 不再读取 `identity` 或 `password` 请求头；
- 缺少 Token 时返回 401；
- 响应带 RFC 9745 Structured Date 格式的 `Deprecation: @1784217600`，以及指向 `/api/auth/token` 的 `Link` header。

推荐退役顺序：

1. 先部署同时支持新旧 Token 端点的后端。
2. 再发布使用 `/api/auth/login` 和 `/api/auth/token` 的客户端。
3. 在网关或访问日志中确认至少一个客户端升级周期内不再调用 `/api/auth`。
4. 删除旧路由及对应兼容测试。

本次变更不修改 MongoDB、Redis 或种子数据结构，不需要数据迁移或恢复操作。回滚应用版本也不会改变现有账号和 Token 数据；但旧客户端的密码请求头登录只在旧后端版本中有效。

## 日志与运维要求

- 禁止记录请求体、`password`、`Token`、完整认证响应或 SharedPreferences 内容。
- 反向代理和 APM 必须关闭认证路径的 body/header 采集，或配置字段级脱敏。
- 生产环境必须对登录和注册增加按 IP、账号及设备维度的限流。
- 当前 Token 仍使用自定义 `Token` header，客户端也仍保存在 SharedPreferences。后续应迁移到 `Authorization: Bearer`、系统安全存储和可主动吊销的会话机制。
