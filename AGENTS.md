# AGENTS.md — pdmadmin 后端

本文件是本项目的开发约定。修改代码前必读，与本文件冲突时以本文件为准。

## 项目是什么

校园资讯推送平台的后端服务。管理端、教师端、学生端三套接口共用一套数据表。
功能域：用户（管理员/教师/学生）、资讯（投稿→审核→发布）、分类与标签、订阅、消息推送（立即可推 + 定时扫表 + 定向人群）、评论收藏、操作日志、统计。

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Java | 17 | |
| Spring Boot | 4.1.0 | |
| MyBatis-Plus | 3.5.17 | 单表 CRUD |
| PageHelper | 2.1.0 | 分页（**唯一**分页方案） |
| Sa-Token | 1.38.0 | 鉴权 |
| MySQL | 8 | utf8mb4 |
| Redis | - | 缓存 |
| Knife4j | 5.7.6 | 接口文档 |
| Lombok | - | |

## 包结构

```
com.example.pdmadmin
├── controller   13 个。只做：接参数 → 调 service/mapper → 返回 R
├── service      业务逻辑。以直接类为主（AuditPushService / PushService /
│                PushScheduler / StudentNewsService）；仅 AdminService 走接口+impl
├── mapper       16 个。绝大多数 extends BaseMapper<T>
├── entity       14 个。对应表，用 Lombok
├── config       7 个
├── util         LoginUtil 等
├── response     R / ResponseCode
└── exception    BusinessException / ControllerExceptionHandler
```

## 硬性规范

### 依赖注入
统一 `@Resource` 字段注入。不要用构造器注入，不要用 `@Autowired`。

### Mapper
必须 `extends BaseMapper<Xxx>`，单表操作走 Wrapper，复杂查询用注解 SQL。
不要新建 XML 映射文件。

### 返回值
Controller 一律返回 `R<T>`：
- 无数据返回：`R.success()`
- 有数据返回：`R.data(x)`
- 需要自定义提示：`R.success("...")`

### 异常
业务错误直接 `throw`，由 `ControllerExceptionHandler` 统一转成 `R.fail`：
- 业务可预期错误：`throw new BusinessException("...")`
- 其余：`throw new RuntimeException("...")`

**不要** try-catch 后自己拼 `R.fail`，也不要把异常吞掉返回成功。
`@SaCheckRole` 校验失败已由 `NotRoleException` 单独处理，不要重复兜底。

### 鉴权
- 类级角色：`@SaCheckRole("admin")` / `@SaCheckRole("teacher")` / `@SaCheckRole("student")`
- 只要求登录、不限角色：`@SaCheckLogin`
- 登录接口本身：`@SaIgnore`（跳过类上的角色校验）
- 取当前用户：`LoginUtil.currentId()` / `LoginUtil.currentRole()`
- 角色常量：`LoginUtil.ROLE_ADMIN` / `ROLE_TEACHER` / `ROLE_STUDENT`

**不要**自己从 token 或请求头解析用户身份。

### 分页

```java
PageHelper.startPage(pageNum, pageSize);
List<Xxx> list = xxxMapper.selectList(wrapper);
return R.data(new PageInfo<>(list));
```

不要引入 MyBatis-Plus 的分页插件，两套混用会出问题。

### 事务
涉及多表写入的方法加 `@Transactional`，放在 service 方法上，不要放在 controller。

## 数据库约定

表清单：`admin` `category` `collect` `comment` `message` `news` `news_read_record` `news_tag` `operation_log` `push_record` `push_task` `student` `subscribe` `tag` `teacher`

### 最容易埋雷的地方：三张用户表主键都从 1 开始

`admin` / `teacher` / `student` 三张表的主键各自从 1 开始，**id 相同不代表同一个人**。
跨表比较用户 id 时必须同时比较角色类型：

- `news.authorId` + `news.authorType`（**1=管理员，2=教师**）
  判断「是不是我发的」必须两个字段一起比。只比 id 会把管理员 1 号误判成教师 1 号。
- `message.msgType`：1=资讯通知，2=系统公告
- `message.readFlag`：0=未读，1=已读

新增任何跨表用户比对逻辑前，先确认角色类型。

### view_count 的语义

`news.view_count` 表示「有多少个**不同学生**看过」，不是「被打开过多少次」。
只允许 `StudentNewsService.readPublishedNews` 修改它。

新增功能不要直接对 view_count 做 +1，也不要绕过去重记录表。

### SQL 脚本

全部放 `sql/` 目录：
- `pdm_schema.sql` — 全量初始化脚本，含 `DROP TABLE`，执行会清库
- `upgrade_xxx.sql` — 增量升级脚本，用 `CREATE TABLE IF NOT EXISTS` / `ALTER TABLE`

新增表或字段时，**两个文件都要改**：schema 里加完整定义，另建一份 upgrade 脚本。

## 修改规则

1. 只改与当前任务相关的文件。不顺手重构、不顺手优化、不顺手改无关文件。
2. 不引入新技术、新依赖、新框架。确有需要时先说明用途和利弊，等确认后再加。
3. 保持现有命名和代码风格。即使有更喜欢的写法，也不要破坏一致性。
4. 不改 `pdm_schema.sql` 里与本任务无关的表结构。
5. 发现无关的死代码或坏味道，只说明，不动手。

## 完成前自检

- `./mvnw -DskipTests compile` 通过
- 涉及业务逻辑的，在 `src/test/java/.../service/` 下加单元测试（Mockito，不连库）
- 涉及表结构的，schema 和 upgrade 脚本都更新了
- 新增接口在 Knife4j 注解里写了 `@Operation(summary=..., description=...)`
