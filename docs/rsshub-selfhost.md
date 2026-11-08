# RSSHub 自建实例部署指南（Docker Compose）

> 适用场景：公共镜像限流 / 路由 503 / 需要配置 Cookie 的路由（微博、小红书等）。
> UniFeed 默认实例 `https://succh.zone.id` 即按本方案部署。

## 一键部署（Docker Compose，含 Redis 缓存）

建一个目录，放入 `docker-compose.yml`：

```yaml
services:
  rsshub:
    image: diygod/rsshub:chromium-bundled   # 需要 puppeteer 的路由用此镜像；纯抓取可用 diygod/rsshub
    restart: always
    ports:
      - "127.0.0.1:1200:1200"               # 只监听本机回环，由反代对外
    environment:
      NODE_ENV: production
      CACHE_TYPE: redis                     # 走 Redis 缓存，多实例/重启不丢缓存
      REDIS_URL: "redis://redis:6379/"
      PUPPETEER_WS_ENDPOINT: "ws://browserless:3000"
      # 常用可选环境变量（按需打开）：
      # ACCESS_KEY: "换成一个随机长字符串"   # 配置后路由需 ?key=... 或 md5(key+path) 签名，防滥用
      # ALLOWLIST: "/36kr//bilibili//zhihu" # 只放行部分路由
      # WEIBO_COOKIE: "SUB=...; SUBP=..."   # 微博路由必须
      # XIAOHONGSHU_COOKIE: "..."           # 小红书路由
      # BILIBILI_COOKIE: "..."              # B站部分路由（登录态/防风控）
      # TWITTER_AUTH_TOKEN: "..."           # X/Twitter 路由
    depends_on:
      - redis
      - browserless
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1:1200/healthz"]
      interval: 60s
      timeout: 10s

  redis:
    image: redis:7-alpine
    restart: always
    volumes:
      - ./redis-data:/data

  browserless:                                # 给需要无头浏览器的路由用；不需要可整段删掉
    image: ghcr.io/browserless/chromium:latest
    restart: always
    environment:
      TIMEOUT: "60000"
      CONCURRENT: "4"
```

启动与自检：

```bash
docker compose up -d
docker compose logs -f rsshub          # 看启动日志，等 "Listening on http://..." 出现
curl http://127.0.0.1:1200/healthz     # 期望 200
curl "http://127.0.0.1:1200/36kr/hot-list" | head -c 300   # 期望返回 <rss ...> XML
```

## 域名与 HTTPS 要点

1. **域名解析**：把一个子域名（如 `rsshub.example.com`）A/AAAA 记录指到服务器 IP。
2. **反代**：推荐 Caddy（自动签发续期 HTTPS 证书，零配置 TLS）：

   ```Caddyfile
   rsshub.example.com {
       reverse_proxy 127.0.0.1:1200
       # 公开实例建议加基础防护，防被扫流量打爆：
       encode gzip
   }
   ```

   用 Nginx 时反向代理到 `127.0.0.1:1200` 并用 certbot 签 Let's Encrypt 证书；
   注意把 `proxy_read_timeout` 调到 60s 以上（部分路由抓上游很慢）。
3. **建议**：配置 `ACCESS_KEY`，或用 Caddy `basic_auth` / IP 白名单限制，公开裸奔容易被滥用导致上游封 IP。
4. UniFeed 设置页「RSSHub 实例」里填 `https://rsshub.example.com` 保存即可；留空时优先用默认自建实例，失败自动回退公共镜像。

## 常用环境变量速查

| 变量 | 作用 |
| --- | --- |
| `CACHE_TYPE=redis` + `REDIS_URL` | 缓存落地 Redis，重启/扩容不丢 |
| `ACCESS_KEY` | 路由访问密钥，防盗用 |
| `ALLOWLIST` / `DENYLIST` | 路由黑白名单 |
| `WEIBO_COOKIE` 等平台 Cookie | 对应平台路由必需的登录态 |
| `PUPPETEER_WS_ENDPOINT` | 接 browserless，跑需要无头浏览器的路由 |
| `PROXY_URI` | 出站代理，抓被墙上游时用 |

更多变量见 RSSHub 官方文档：https://docs.rsshub.app/install/

## 默认实例 succh.zone.id 自查步骤

UniFeed 默认自建实例为 `https://succh.zone.id`，失效时按顺序排查：

1. **连通性**：浏览器或 `curl -I https://succh.zone.id/healthz`，期望 `HTTP/2 200`。
   - 连不上：先看域名解析是否过期（`dig succh.zone.id`），再看服务器/容器是否在运行。
2. **路由级**：`curl https://succh.zone.id/36kr/hot-list`，期望返回 RSS XML。
   - 503 / Welcome 页：多为上游反爬或路由依赖登录态，属于「该路由在公共实例都难抓」，换镜像竞速即可（UniFeed 抓取层已内置）。
3. **在 UniFeed 里测**：设置 → RSSHub 实例 → 「测试实例」，会实时请求 `/36kr/hot-list` 并报 HTTP 状态与耗时。
4. 实例挂了又不方便马上修：设置页「公共实例快捷选择」临时切到任一可用镜像。

## 镜像实测记录（2026-11-07）

| 实例 | /healthz | /hackernews | /36kr/hot-list | 结论 |
| --- | --- | --- | --- | --- |
| succh.zone.id（默认自建） | 200 | — | 200 FEED | 可用 |
| rsshub.rssforever.com | 200 | 200 | 200（偶发慢/空响应） | 可用，部分路由慢 |
| hub.slarker.me | 200 | 200 | 超时 | 可用，36kr 路由级超时 |
| rsshub.cups.moe | 200 | 200 | 200 FEED | 可用 |
| rsshub.ktachibana.party | 200 | 200 | 200 | 可用（本轮新补入） |
| rsshub.app（官方） | 403* | — | — | 保留；数据中心 IP 被 Cloudflare 拦，手机网络通常正常 |
| rss.owo.nz | DNS 解析失败 | — | — | 已移除 |

> 注：镜像可用性随时变化，UniFeed 抓取层对 RSSHub 源做多镜像竞速，单个镜像失效不影响订阅刷新。
