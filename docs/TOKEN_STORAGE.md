# 客户端 Token 安全存储与迁移

Flutter 客户端使用 `flutter_secure_storage 10.3.1` 保存 JWT。SharedPreferences 不再作为正式凭据存储，只保留旧版本明文 Token 的一次性迁移读取。

## 存储边界

| 平台 | 存储后端与配置 |
|---|---|
| Android | `flutter_secure_storage` 默认加密实现；应用关闭 `allowBackup`，避免备份恢复后密钥与密文不匹配 |
| iOS | Keychain；Debug/Profile 与 Release 都配置 Keychain entitlement，最低 iOS 13 |
| macOS | Keychain；Release 使用 Data Protection Keychain 并要求正式签名，Debug/Profile 使用普通 Keychain 以保留无证书本地构建 |
| Windows | 插件平台安全存储；构建机需要安装 Visual Studio C++ ATL 组件 |
| Linux | Secret Service/libsecret；构建需要 `libsecret-1-dev`，运行需要 `libsecret-1-0` 和可用 keyring |
| Web | 仅支持 HTTPS 或 localhost；不能抵御同源 XSS，仍必须执行 CSP、依赖审计和输入输出转义 |

平台配置要求来自 [flutter_secure_storage 官方说明](https://pub.dev/packages/flutter_secure_storage)。

## 升级迁移

新安全存储 key 为 `auth_token_v2`，旧 SharedPreferences key 为 `Token`。

读取顺序：

1. 优先读取系统安全存储。
2. 如果安全存储有值，清理可能残留的旧 SharedPreferences 值。
3. 如果安全存储没有值，再读取旧 `Token`。
4. 先把旧值写入安全存储；只有写入成功后才删除旧值。
5. 安全写入失败时向上抛错并保留旧值，避免升级中断造成不可恢复的登录态丢失。

登录和注册只写安全存储。登出会尝试同时删除安全存储与旧 SharedPreferences；即使安全删除失败，也会继续清理旧明文值并报告错误。

## 发布与回滚

- 本次不修改服务端 MongoDB、Redis、JWT 格式或用户数据。
- 客户端首次读取后会删除旧 SharedPreferences Token，因此回滚到不支持安全存储的旧客户端版本时，用户可能需要重新登录。
- 不要为了无感回滚继续双写 SharedPreferences；那会重新引入明文凭据。
- 发布前至少验证 Android、iOS、Web 三个实际交付平台的登录、重启恢复会话和登出。
- Web 生产环境必须使用 HTTPS；本机 `http://localhost` 可用于开发。

## 备份与故障恢复

客户端 Token 是可重新签发的短期凭据，不属于业务数据或灾难恢复备份：

- Android 已禁止把应用数据自动备份到云端。
- Keychain、Secret Service 或 Windows 本地安全存储不得导出到项目备份。
- 换机、清理应用数据、Keychain/Keystore 损坏或恢复失败时，用户重新登录即可。
- 不得把安全存储内容、旧 SharedPreferences Token 或完整 Authorization header 写入日志、崩溃报告或遥测。

## 测试

Flutter 测试通过 `test/flutter_test_config.dart` 全局注入内存安全存储，不连接开发机的真实 Keychain/Keystore。迁移测试覆盖：

- 安全存储优先；
- 旧 Token 一次性迁移；
- 写入失败时保留旧值；
- 保存时清理旧值；
- 登出清理两处；
- 删除失败时仍清理旧明文并向上抛错。
