# 本地联调环境

这套环境用于 Phase 1 的双账号联调，不包含 Agent 或 RAG 服务。

## 启动

1. 复制环境变量模板：`cp .env.example .env`
2. 将 `.env` 中的四个密码替换为本地随机值。
3. 执行：`docker compose up --build -d`
4. 等待服务健康后访问：`http://localhost:8088`

查看状态与日志：

```bash
docker compose ps
docker compose logs -f backend frontend
```

停止服务但保留数据：

```bash
docker compose down
```

仅当确认需要清空本地联调数据时，执行 `docker compose down -v`。

## 服务关系

- 前端 Nginx 是唯一浏览器入口，默认映射到宿主机 `8088`。
- `/api/*`、`/ws/notification` 和 `/ws/typing` 由 Nginx 转发到后端。
- Redis 只在 Compose 内部网络开放，不映射宿主机端口。
- MySQL 同时仅绑定到宿主机 `127.0.0.1:3307`，供 IntelliJ Database 等本地工具访问；可通过 `MYSQL_PORT` 修改。
- 后端健康检查为 `/actuator/health`；前端健康检查为 `/health`。

可以通过 `APP_PORT` 修改入口端口，通过 `LETTER_COOLDOWN_SECONDS` 修改联调冷静期。

部署到正式域名时，需将 `WS_ALLOWED_ORIGIN_PATTERNS` 设置为浏览器访问的 HTTPS 来源（例如 `https://example.com`）。本地默认值仅允许 localhost 和 127.0.0.1。
