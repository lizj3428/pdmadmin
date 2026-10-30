-- ============================================================
-- 校园资讯推送平台（pdmdb）建表脚本
-- 严格对应论文 表4-1 ~ 表4-14，共 14 张业务表
--
-- 说明：本脚本会重建全部 14 张表，对「全新空库」和「已有库」都能直接执行，
--       不依赖任何老表存在，也不再迁移老数据。
--       执行完成后管理员账号只有一个种子账号：admin / 123456
--       如需保留旧账号，请先在旧库执行 select * from admin 自行备份。
--
-- 执行：mysql -uroot -p<你的密码> --default-character-set=utf8mb4 pdmdb < pdm_schema.sql
-- ============================================================

SET NAMES utf8mb4;
USE pdmdb;

-- ========== 表4-1 管理员表 admin ==========
-- 直接重建，不迁移老表：老结构为 userpwd/name/tel，新结构按论文为 password/real_name/phone
DROP TABLE IF EXISTS admin;

CREATE TABLE admin (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
    username        VARCHAR(30)  NOT NULL                COMMENT '登录账号',
    password        VARCHAR(64)  NOT NULL                COMMENT '登录密码',
    real_name       VARCHAR(20)  DEFAULT NULL            COMMENT '姓名',
    phone           CHAR(11)     DEFAULT NULL            COMMENT '联系电话',
    role_type       TINYINT      NOT NULL DEFAULT 1      COMMENT '角色类型 1超级管理员 2普通管理员',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '账号状态 1启用 0禁用',
    last_login_time DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员表';

-- 种子账号：admin / 123456
-- 密码存 BCrypt 密文（$2b$ 前缀、60 字符），明文永远不入库；
-- 登录时由 PasswordUtil 用 BCrypt 校验，库里没有明文可比对
INSERT INTO admin (username, password, real_name, role_type, status, create_time)
VALUES ('admin', '$2b$10$5xhne/zwObyIQulbNT2XG.XRjnM6jvF28IhCY6r9L9ewBs2rWdzIW', '超级管理员', 1, 1, NOW());

-- ========== 表4-2 教师表 teacher ==========
DROP TABLE IF EXISTS teacher;
CREATE TABLE teacher (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '教师ID',
    teacher_no  VARCHAR(20) NOT NULL                COMMENT '教工号',
    password    VARCHAR(64) NOT NULL                COMMENT '登录密码',
    real_name   VARCHAR(20) NOT NULL                COMMENT '姓名',
    gender      CHAR(1)     DEFAULT NULL            COMMENT '性别',
    phone       CHAR(11)    DEFAULT NULL            COMMENT '联系电话',
    dept_name   VARCHAR(50) DEFAULT NULL            COMMENT '所属院系',
    title       VARCHAR(20) DEFAULT NULL            COMMENT '职称',
    status      TINYINT     NOT NULL DEFAULT 1      COMMENT '账号状态 1启用 0禁用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_no (teacher_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教师表';

-- ========== 表4-3 学生表 student ==========
DROP TABLE IF EXISTS student;
CREATE TABLE student (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '学生ID',
    student_no   VARCHAR(20) NOT NULL                COMMENT '学号',
    password     VARCHAR(64) NOT NULL                COMMENT '登录密码',
    real_name    VARCHAR(20) NOT NULL                COMMENT '姓名',
    gender       CHAR(1)     DEFAULT NULL            COMMENT '性别',
    grade        VARCHAR(10) DEFAULT NULL            COMMENT '年级',
    major        VARCHAR(50) DEFAULT NULL            COMMENT '专业',
    class_name   VARCHAR(30) DEFAULT NULL            COMMENT '班级',
    phone        CHAR(11)    DEFAULT NULL            COMMENT '联系电话',
    push_channel TINYINT     NOT NULL DEFAULT 1      COMMENT '消息接收渠道 1站内信 2微信 3两者',
    disturb_flag TINYINT     NOT NULL DEFAULT 0      COMMENT '免打扰标志 1开启 0关闭',
    status       TINYINT     NOT NULL DEFAULT 1      COMMENT '账号状态 1启用 0禁用',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生表';

-- ========== 表4-4 资讯分类表 category ==========
DROP TABLE IF EXISTS category;
CREATE TABLE category (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    name        VARCHAR(30) NOT NULL                COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT 0      COMMENT '显示排序，值越小越靠前',
    remark      VARCHAR(100) DEFAULT NULL           COMMENT '分类说明',
    status      TINYINT     NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资讯分类表';

-- ========== 表4-5 资讯表 news ==========
DROP TABLE IF EXISTS news;
CREATE TABLE news (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '资讯ID',
    title          VARCHAR(100) NOT NULL                COMMENT '资讯标题',
    summary        VARCHAR(200) DEFAULT NULL            COMMENT '资讯摘要',
    content        TEXT         NOT NULL                COMMENT '资讯正文',
    cover_url      VARCHAR(200) DEFAULT NULL            COMMENT '封面图片',
    category_id    BIGINT       NOT NULL                COMMENT '所属分类',
    author_id      BIGINT       NOT NULL                COMMENT '创建人',
    author_type    TINYINT      NOT NULL DEFAULT 1      COMMENT '创建人类型 1管理员 2教师',
    level          TINYINT      NOT NULL DEFAULT 1      COMMENT '重要程度 1普通 2重要 3紧急',
    audit_status   TINYINT      NOT NULL DEFAULT 0      COMMENT '审核状态 0待审核 1通过 2驳回',
    audit_opinion  VARCHAR(100) DEFAULT NULL            COMMENT '审核意见',
    publish_status TINYINT      NOT NULL DEFAULT 0      COMMENT '发布状态 0未发布 1已发布 2已下线',
    publish_time   DATETIME     DEFAULT NULL            COMMENT '发布时间',
    view_count     INT          NOT NULL DEFAULT 0      COMMENT '阅读量',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_news_category (category_id),
    KEY idx_news_audit (audit_status),
    KEY idx_news_publish (publish_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资讯表';

-- ========== 表4-6 标签表 tag ==========
DROP TABLE IF EXISTS tag;
CREATE TABLE tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    name        VARCHAR(20) NOT NULL                COMMENT '标签名称',
    use_count   INT         NOT NULL DEFAULT 0      COMMENT '引用次数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标签表';

-- ========== 表4-7 资讯标签关联表 news_tag ==========
DROP TABLE IF EXISTS news_tag;
CREATE TABLE news_tag (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    news_id     BIGINT   NOT NULL                COMMENT '资讯ID',
    tag_id      BIGINT   NOT NULL                COMMENT '标签ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_news_tag (news_id, tag_id),
    KEY idx_nt_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资讯标签关联表';

-- ========== 表4-8 订阅关系表 subscribe ==========
DROP TABLE IF EXISTS subscribe;
CREATE TABLE subscribe (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    student_id  BIGINT   NOT NULL                COMMENT '学生ID',
    target_type TINYINT  NOT NULL                COMMENT '订阅对象类型 1分类 2标签',
    target_id   BIGINT   NOT NULL                COMMENT '分类ID或标签ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscribe (student_id, target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订阅关系表';

-- ========== 表4-9 推送任务表 push_task ==========
DROP TABLE IF EXISTS push_task;
CREATE TABLE push_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    news_id      BIGINT       NOT NULL                COMMENT '关联资讯',
    task_name    VARCHAR(60)  NOT NULL                COMMENT '任务名称',
    target_type  TINYINT      NOT NULL                COMMENT '目标人群类型 1全部 2按年级 3按专业 4按标签',
    target_value VARCHAR(200) DEFAULT NULL            COMMENT '目标人群取值，多个以逗号分隔',
    channel      TINYINT      NOT NULL                COMMENT '推送渠道 1站内信 2微信 3企业微信',
    push_type    TINYINT      NOT NULL                COMMENT '推送方式 1立即 2定时',
    plan_time    DATETIME     DEFAULT NULL            COMMENT '计划推送时间',
    status       TINYINT      NOT NULL DEFAULT 0      COMMENT '任务状态 0待执行 1执行中 2已完成 3失败',
    target_count INT          NOT NULL DEFAULT 0      COMMENT '目标人数',
    create_by    BIGINT       NOT NULL                COMMENT '创建人',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_pt_news (news_id),
    KEY idx_pt_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推送任务表';

-- ========== 表4-10 推送记录表 push_record ==========
DROP TABLE IF EXISTS push_record;
CREATE TABLE push_record (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    task_id     BIGINT      NOT NULL                COMMENT '任务ID',
    student_id  BIGINT      NOT NULL                COMMENT '接收学生',
    channel     TINYINT     NOT NULL                COMMENT '推送渠道 1站内信 2微信 3企业微信',
    send_status TINYINT     NOT NULL DEFAULT 0      COMMENT '发送状态 0待发送 1已提交 2已送达 3发送失败',
    fail_reason VARCHAR(100) DEFAULT NULL           COMMENT '失败原因',
    retry_count TINYINT     NOT NULL DEFAULT 0      COMMENT '重试次数，上限3次',
    cost_ms     INT         DEFAULT NULL            COMMENT '耗时毫秒',
    send_time   DATETIME    DEFAULT NULL            COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_pr_task (task_id),
    KEY idx_pr_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推送记录表';

-- ========== 表4-11 站内消息表 message ==========
DROP TABLE IF EXISTS message;
CREATE TABLE message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    student_id  BIGINT       NOT NULL                COMMENT '接收学生',
    title       VARCHAR(100) NOT NULL                COMMENT '消息标题',
    content     VARCHAR(500) NOT NULL                COMMENT '消息内容',
    news_id     BIGINT       DEFAULT NULL            COMMENT '关联资讯',
    msg_type    TINYINT      NOT NULL DEFAULT 1      COMMENT '消息类型 1资讯通知 2系统公告',
    read_flag   TINYINT      NOT NULL DEFAULT 0      COMMENT '已读标志 0未读 1已读',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_msg_student (student_id, read_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息表';

-- ========== 表4-12 收藏表 collect ==========
DROP TABLE IF EXISTS collect;
CREATE TABLE collect (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    student_id  BIGINT   NOT NULL                COMMENT '学生ID',
    news_id     BIGINT   NOT NULL                COMMENT '资讯ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_collect (student_id, news_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏表';

-- ========== 表4-13 评论表 comment ==========
DROP TABLE IF EXISTS comment;
CREATE TABLE comment (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    news_id      BIGINT       NOT NULL                COMMENT '资讯ID',
    student_id   BIGINT       NOT NULL                COMMENT '评论学生',
    content      VARCHAR(300) NOT NULL                COMMENT '评论内容',
    audit_status TINYINT      NOT NULL DEFAULT 0      COMMENT '审核状态 0待审核 1通过 2驳回',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (id),
    KEY idx_comment_news (news_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论表';

-- ========== 表4-14 操作日志表 operation_log ==========
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id     BIGINT       NOT NULL                COMMENT '操作人ID',
    user_type   TINYINT      NOT NULL                COMMENT '操作人类型 1管理员 2教师 3学生',
    module      VARCHAR(30)  NOT NULL                COMMENT '操作模块',
    operation   VARCHAR(100) NOT NULL                COMMENT '操作描述',
    request_url VARCHAR(150) DEFAULT NULL            COMMENT '请求地址',
    ip_address  VARCHAR(40)  DEFAULT NULL            COMMENT '操作IP',
    cost_ms     INT          DEFAULT NULL            COMMENT '耗时毫秒',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- ========== 清理论文中没有的旧表 ==========
DROP TABLE IF EXISTS `user`;
