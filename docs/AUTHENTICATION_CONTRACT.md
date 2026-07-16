# 认证接口契约与迁移

本文档记录密码登录、Token 认证、错误语义和旧接口退役边界。认证响应包含可直接访问账号数据的 Token，生产环境必须使用 HTTPS。

## 当前接口

| 接口 | 用途 | 凭据位置 | 成功响应 |
|---|---|---|---|
| `POST /api/auth/login` | 密码登录 | JSON body | 用户信息和新 Token |
| `POST /api/auth/token` | 使用已有 Token 恢复会话 | `Authorization: Bearer <token>` | 用户信息和新 Token |
| `POST /api/auth` | 旧客户端兼容入口 | Bearer；旧 `Token` header 也兼容 | 用户信息和新 Token |
| `POST /api/register` | 公开注册患者账号 | JSON body | 用户信息和新 Token |

密码登录请求体：

```json
{
  "identity": "patient@example.com",
  "password": "用户输入的密码"
}
```

`identity` 最长 254 个字符，`password` 最长 128 个字符，二者都不能为空。客户端登录时必须关闭自动 Token 附加，避免把旧会话与密码登录混在同一个请求中。

所有受保护的 `/api/**` 接口统一使用：

```http
Authorization: Bearer <jwt>
```

客户端不得在同一个请求里同时发送 `Authorization` 和旧 `Token` header。后端遇到重复凭据或非 Bearer 的 Authorization 值会返回 400，避免在代理、客户端和服务端之间产生凭据优先级歧义。

Bearer 传输、`WWW-Authenticate`、`invalid_token` 和 `insufficient_scope` 的语义遵循 [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750.html)。

成功响应中的 `password` 始终为空且不会序列化。登录、Token 认证和注册成功响应均包含：

```http
Cache-Control: no-store
Pragma: no-cache
```

业务接口不再通过响应 `Token` header 逐请求滚动 JWT，也不会触发客户端每次请求写 SharedPreferences。会话续期只在登录、注册或显式调用 `/api/auth/token` 时发生，Token 仍通过 JSON 响应体返回。

## 错误语义

- JSON 缺字段、字段超长或格式错误，或认证 header 畸形/重复：HTTP 400。
- 未知账号和错误密码：统一返回 HTTP 401，消息为 `用户名或密码错误`，不向调用方暴露账号是否存在。
- Token 缺失：HTTP 401，消息为 `缺少Token`，并返回 `WWW-Authenticate: Bearer realm="aphasia-api"`。
- Token 无效、过期、声明缺失或对应账号已删除：统一返回 HTTP 401，并在 Bearer challenge 中标记 `invalid_token`。
- Token 有效但角色不足：HTTP 403，并在 Bearer challenge 中标记 `insufficient_scope`。
- 其他未处理异常：HTTP 500，响应不包含内部异常细节。

服务端对未知账号也执行一次 BCrypt 校验，以降低通过响应耗时枚举账号的风险。该措施不能替代登录限流和异常登录告警。

## 旧接口兼容边界

旧 `POST /api/auth` 已收窄为 token-only：

- 继续支持已安装旧客户端使用保存的 Token 恢复会话；
- 不再读取 `identity` 或 `password` 请求头；
- 正式凭据位置为 `Authorization: Bearer`；
- 缺少 Token 时返回 401；
- 响应带 [RFC 9745](https://www.rfc-editor.org/rfc/rfc9745.html) Structured Date 格式的 `Deprecation: @1784217600`，以及指向 `/api/auth/token` 的 `Link` header。

旧 `Token` header 在 `/api/auth/token` 和受保护接口上暂时仍可单独使用。使用时响应会带 `X-Auth-Header-Deprecation: Token`；该 header 不包含凭据内容，只用于客户端升级和网关观测。浏览器跨域客户端可以读取该迁移提示。

推荐退役顺序：

1. 先部署同时支持 Bearer 和旧 `Token` header 的后端。
2. 再发布使用 `/api/auth/login`、`/api/auth/token` 和 Bearer 的客户端。
3. 在网关或客户端遥测中确认至少一个升级周期内不再出现 `X-Auth-Header-Deprecation`，且不再调用 `/api/auth`。
4. 删除旧 `Token` header fallback、旧路由及对应兼容测试。

认证接口变更不修改 MongoDB、Redis 或种子数据结构。客户端会把旧 SharedPreferences Token 一次性迁移到系统安全存储；迁移、发布和回滚边界见 [客户端 Token 安全存储与迁移](TOKEN_STORAGE.md)。

## 日志与运维要求

- 禁止记录请求体、`password`、`Token`、完整认证响应或 SharedPreferences 内容。
- 反向代理和 APM 必须关闭认证路径的 body/header 采集，或配置字段级脱敏。
- 生产环境必须对登录和注册增加按 IP、账号及设备维度的限流。
- 客户端已使用系统安全存储。后续仍需建立服务端可主动吊销的会话机制，并在升级周期结束后删除旧 `Token` header fallback。
