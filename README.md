# pdmadmin

校园资讯推送平台后端服务。一套接口同时服务管理端、教师端、学生端，三端共用一套数据表。

主流程：教师投稿 → 管理员审核 → 通过后自动推送 → 学生在站内信 / 实时弹窗收到。

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Java | 17 | |
| Spring Boot | 4.1.0 | Web、调度、WebSocket |
| MyBatis-Plus | 3.5.17 | 单表 CRUD |
| PageHelper | 2.1.0 | 分页（项目内唯一分页方案） |
| Sa-Token | 1.38.0 | 登录与角色鉴权 |
| MySQL | 8 | 存储，utf8mb4 |
| Redis | 6+ | 验证码、热点资讯缓存 |
| Druid | 1.2.28 | 连接池 |
| Knife4j | 5.7.6 | 接口文档 |
| Kaptcha | 2.3.2 | 图形验证码 |
| spring-security-crypto | — | BCrypt 密码加密（只引 crypto，不引 Starter） |
| Lombok | 1.18.48 | 实体类简化 |

## 功能模块

- **用户**：管理员 / 教师 / 学生三套账号各自登录；教师、学生可被授予管理端权限（`admin_flag`）
- **资讯**：投稿、编辑、审核（通过即发布）、下线、置顶、热点（Redis 缓存 5 分钟）、阅读去重计数
- **分类与标签**：分类带启用状态与排序；标签带实时引用统计
- **订阅**：学生订阅分类 / 标签，是定向推送的选人依据
- **推送**：立即推送与定时推送（30 秒扫表）、按全体 / 年级 / 专业 / 标签定向、逐人写站内信与推送记录、在线用户 WebSocket 实时通知
- **消息**：未读数、按条或按资讯标记已读
- **统计**：总览 12 项指标、分类分布、阅读排行、推送趋势与状态分布、学生增长
- **其他**：图形验证码、操作日志、收藏、评论

## 目录结构

```
src/main/java/com/example/pdmadmin
├── controller    13 个，只做接参 → 调 service/mapper → 返回 R
├── service       业务逻辑（PushService / PushScheduler / AuditPushService / StudentNewsService 等）
├── mapper        16 个，绝大多数 extends BaseMapper<T>，复杂查询用注解 SQL
├── entity        14 个，对应数据表
├── config        Sa-Token、Redis、WebSocket、跨域、角色数据源
├── websocket     握手鉴权、连接管理、消息推送
├── util          LoginUtil / PasswordUtil
├── response      R / ResponseCode
└── exception     BusinessException / ControllerExceptionHandler
```

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+（或直接用仓库自带的 `./mvnw`）
- MySQL 8.0+
- **Redis 6.0+** —— 登录验证码和热点缓存都走 Redis，Redis 没起登录直接失败

### 1. 初始化数据库

```bash
mysql -u root -p < sql/pdm_schema.sql
```

脚本重建 `pdmdb` 下 15 张表并写入种子数据。**注意：含 `DROP TABLE`，会清空同名表。**

### 2. 修改配置

`src/main/resources/application.yml`：数据库账号密码、Redis 地址与密码。

默认值：库 `pdmdb`、端口 `8080`、`context-path: /pdmadmin`。

### 3. 启动

```bash
./mvnw spring-boot:run
```

或在 IDE 里运行 `PdmadminApplication`。

### 4. 访问

| 用途 | 地址 |
|---|---|
| 接口根地址 | http://localhost:8080/pdmadmin |
| 接口文档（Knife4j） | http://localhost:8080/pdmadmin/doc.html |
| WebSocket | ws://localhost:8080/pdmadmin/ws?pdmtoken=xxx |

## 初始账号

密码统一 `123456`（库内是 BCrypt 密文）。

| 角色 | 账号 | 来源脚本 |
|---|---|---|
| 管理员 | `admin` | `pdm_schema.sql` |
| 学生 | `20230101` / `20230102` / `20220103` | `pdm_schema.sql` |
| 教师 | `T001`（普通）、`T002`（有管理端权限） | `upgrade_admin_flag.sql` |

教师种子在 `upgrade_admin_flag.sql` 里，**只跑 `pdm_schema.sql` 不会产生教师账号**。

## 接口约定

- 除 `/common/getCaptcha`（GET）和 WebSocket 外，接口全部是 `POST`
- 分页参数 `pageNum` / `pageSize` 走 query，业务参数走 JSON body
- 统一返回 `R`：`R.success()` / `R.data(x)`；异常由 `ControllerExceptionHandler` 转 `R.fail`
- 鉴权：请求头 `pdmtoken`（Sa-Token），前端拦截器自动加

### 接口分组

| 前缀 | 内容 | 鉴权 |
|---|---|---|
| `/admin/*` | 管理员登录、增删改查 | 登录接口 `@SaIgnore`，其余 `admin` |
| `/student/*` `/teacher/*` | 学生 / 教师管理、登录、授予管理端权限 | 类级 `admin`，登录接口 `@SaIgnore` |
| `/news/*` | 资讯列表 / 详情 / 热度 / 新增 / 修改 / 删除 / 审核 / 置顶 | `admin` |
| `/category/*` `/tag/*` | 分类、标签维护 | `admin` |
| `/push/*` | 推送任务与推送记录 | `admin` |
| `/stat/*` | 统计看板 | `admin` |
| `/tch/*` | 教师投稿、我的投稿、分类标签下拉 | `teacher` |
| `/stu/*` | 学生端资讯、消息、订阅、个人中心 | `student` |
| `/common/*` | 验证码等公共能力 | 匿名 |

## 身份与权限

登录身份（loginId）统一为 `角色:主键`，如 `admin:1`、`teacher:2`、`student:3`。

- 前缀表示「在哪张表」，三张用户表主键都从 1 自增，不加前缀会串号（WebSocket 连接也会互相顶掉）
- 角色由 `StpInterfaceImpl` 提供：前缀角色 + 该教师 / 学生 `admin_flag = 1` 时追加 `admin`
- 因此被授权的教师、学生能访问 `@SaCheckRole("admin")` 的接口，权限与管理员相同
- 取当前用户一律用 `LoginUtil.currentId()`，**禁止 `StpUtil.getLoginIdAsLong()`**（遇到 `admin:1` 会抛异常）

## 数据库脚本

| 文件 | 用途 |
|---|---|
| `pdm_schema.sql` | 全量初始化，含 `DROP TABLE`，会清库 |
| `upgrade_admin_flag.sql` | 老库补 `admin_flag` 列 + 教师种子账号 |
| `upgrade_news_read_record.sql` | 补阅读去重记录表 |
| `upgrade_news_top_flag.sql` | 补资讯置顶字段 |
| `upgrade_message_unique.sql` | 清理重复站内信并补唯一键 |

新库直接跑 `pdm_schema.sql`；老库按需跑 `upgrade_*.sql`，**不要**在已建好的库上重跑全量脚本。

## 测试

```bash
./mvnw test
```

单元测试在 `src/test/java`，用 Mockito 打桩，不连数据库。目前覆盖推送任务执行、未发布资讯拒推、任务重复执行拦截、阅读去重计数。

## 已知限制

- 定时推送依赖 `@Scheduled` 单机扫表，多实例部署会重复执行，需要分布式锁
- 推送执行中途抛异常时任务会停在「执行中」，`claim` 只放行待执行 / 失败，只能手工改库恢复
- 微信、企业微信渠道未接入，推送记录里落「渠道未接入」
- `/test/push` 调试接口默认不注册，需启动参数 `--pdm.test-push.enabled=true`
- `admin_flag` 授予后权限等同管理员，没有更细的分级

## 开发约定

见 [AGENTS.md](AGENTS.md)：依赖注入、返回值、异常、鉴权、分页、事务、SQL 脚本等硬性规范都在那里，改代码前先读。
