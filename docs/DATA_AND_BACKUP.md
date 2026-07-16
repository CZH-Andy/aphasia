# 数据、备份与归档

## 数据位置

Docker Compose 使用两个持久卷：

| 卷 | 内容 | 是否包含敏感数据 |
|---|---|---|
| `mongo-data` | 用户、套题、作答结果、诊断结果 | 是 |
| `media-data` | 医生上传的图片/音频、合成音频 | 可能 |

API 密钥和 JWT 密钥位于本地 `.env`，不得提交到 Git。`seed/mongodump` 只应保存可公开分发的演示数据，禁止混入真实患者记录。

客户端 JWT 保存在系统安全存储，是可重新签发的短期凭据，不属于业务备份。不得导出 Keychain、Keystore、Secret Service、Windows 本地安全存储或 Web 本地加密材料；设备迁移或安全存储损坏时应要求用户重新登录。详细迁移与平台边界见 [客户端 Token 安全存储与迁移](TOKEN_STORAGE.md)。

新作答记录会在 MongoDB 中保存原始识别文本、选择项、动作、点击坐标和题目快照。它们与录音、图片一样属于敏感医疗相关数据，备份、校验、复制和销毁都必须遵循相同的访问控制。

MongoDB 中的内部上传媒体只保存 `/images/{uid}/{file}` 或 `/audio/{uid}/{file}` 形式的稳定相对路径。`expires` 和 `signature` 是响应时临时生成的访问凭证，不属于业务数据，不应写入数据库、种子或备份清单。恢复或轮换媒体签名密钥后，客户端重新请求套题/结果即可获得新链接。

## 建议备份流程

先创建一个仅管理员可读的归档目录：

```bash
mkdir -p backup/$(date +%F)
chmod 700 backup backup/$(date +%F)
```

导出 MongoDB：

```bash
docker compose exec -T mongo mongodump \
  --db LrNew \
  --archive > backup/$(date +%F)/LrNew.archive
```

导出媒体卷：

```bash
docker compose exec -T backend \
  tar -C /app/media -czf - . > backup/$(date +%F)/media.tar.gz
```

生成校验清单：

```bash
shasum -a 256 backup/$(date +%F)/* > backup/$(date +%F)/SHA256SUMS
```

`.env` 应通过密码管理器或受控密钥系统单独备份，不要放入上述普通归档，也不要进入 Git。

## 恢复

恢复前进入维护窗口并停止前端与后端写入：

```bash
docker compose stop frontend backend
```

恢复 MongoDB：

```bash
docker compose exec -T mongo mongorestore \
  --archive \
  --drop < backup/YYYY-MM-DD/LrNew.archive
```

Mongo 恢复完成后先启动后端容器，再立即恢复媒体；此时不要开放前端或允许外部客户端调用：

```bash
docker compose start backend
docker compose exec -T backend \
  tar -C /app/media -xzf - < backup/YYYY-MM-DD/media.tar.gz
```

恢复后重新启动并执行冒烟测试：

```bash
docker compose start frontend
docker compose ps
```

恢复后还应抽查 `examResult` 中的 `examId`、`revision`、`categoryResults.questionResults.sourceQuestionSnapshot` 和各题型原始载荷字段，确认历史记录不是只有汇总分数。

## 归档规则

- 每个归档目录记录日期、Git commit、数据用途、负责人、保留期限和校验值。
- 生产数据与研究数据分库存放；演示种子不得从生产数据库直接导出。
- `MEDIA_SIGNING_SECRET` 与 `JWT_SECRET` 应由密钥系统单独备份；不要与 MongoDB 或媒体归档放在一起。
- 客户端 Token 不进入归档；备份或遥测中发现 Token 应视为凭据泄露并立即处置。
- 删除 Git 分支或重建容器前，先确认 Mongo 与媒体归档都可恢复。
- `docker compose down -v` 会同时删除数据库和媒体卷，只能在确认备份或明确重置演示环境时使用。
- 定期做恢复演练；只有成功恢复过的备份才算有效备份。
