-- ============================================================
-- 升级脚本：2026-11 补 4 个字段 + 修正唯一键（老库用）
--
-- 【对应代码改动】
--   news.expire_time            资讯有效期，过期学生端不可见
--   message.receiver_type       1学生 2教师，教师通知复用 student_id 列存接收人ID
--   push_task.target_role       1学生 2教师 3学生和教师
--   push_record.receiver_type   1学生 2教师
--
-- 【什么时候执行】
--   数据库建于 2026-11-13 之前，或建库时用的是旧版 pdm_schema.sql。
--   全新库直接跑 pdm_schema.sql 即可，不用执行本脚本。
--   脚本全部使用 information_schema 判断，可重复执行，不会因重复跑而报错。
--
-- 【注意事项】
--   本脚本包含原 upgrade_message_unique.sql 的内容，那个脚本可以不再执行。
--   执行前请确认已备份 pdmdb（本脚本只加列、改索引，不删数据）。
--
-- 【执行方式】
--   mysql -uroot -p<你的密码> --default-character-set=utf8mb4 pdmdb < upgrade_2026_11.sql
-- ============================================================

SET NAMES utf8mb4;
USE pdmdb;

-- ---------- 1. news.expire_time ----------
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'news' AND column_name = 'expire_time'),
    'DO 0',
    'ALTER TABLE news ADD COLUMN expire_time DATETIME DEFAULT NULL COMMENT ''有效期截止时间，NULL 表示长期有效'' AFTER publish_time'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2. push_task.target_role（并补 target_type 注释 5=班级）----------
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'push_task' AND column_name = 'target_role'),
    'DO 0',
    'ALTER TABLE push_task ADD COLUMN target_role TINYINT NOT NULL DEFAULT 1 COMMENT ''接收角色 1学生 2教师 3学生和教师'' AFTER target_type'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE push_task
    MODIFY COLUMN target_type TINYINT NOT NULL COMMENT '目标人群类型 1全部 2按年级 3按专业 4按标签 5按班级';

-- ---------- 3. push_record.receiver_type ----------
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'push_record' AND column_name = 'receiver_type'),
    'DO 0',
    'ALTER TABLE push_record ADD COLUMN receiver_type TINYINT NOT NULL DEFAULT 1 COMMENT ''接收人类型 1学生 2教师'' AFTER task_id'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE push_record
    MODIFY COLUMN student_id BIGINT NOT NULL COMMENT '接收人ID，receiver_type=1 关联学生表，=2 关联教师表';

-- ---------- 4. message.receiver_type ----------
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'message' AND column_name = 'receiver_type'),
    'DO 0',
    'ALTER TABLE message ADD COLUMN receiver_type TINYINT NOT NULL DEFAULT 1 COMMENT ''接收人类型 1学生 2教师'' AFTER id'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE message
    MODIFY COLUMN student_id BIGINT NOT NULL COMMENT '接收人ID，receiver_type=1 关联学生表，=2 关联教师表',
    MODIFY COLUMN msg_type   TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型 1资讯通知 2系统公告 3审核结果通知';

-- ---------- 5. message 唯一键：(student_id, news_id) -> (receiver_type, student_id, news_id) ----------
-- 先建新键再删旧键，中途不会出现没有唯一约束的窗口
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'message' AND index_name = 'uk_msg_receiver_news'),
    'DO 0',
    'ALTER TABLE message ADD UNIQUE KEY uk_msg_receiver_news (receiver_type, student_id, news_id)'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'message' AND index_name = 'uk_msg_student_news'),
    'ALTER TABLE message DROP INDEX uk_msg_student_news',
    'DO 0'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 6. 存量数据自检 ----------
-- 新列是 NOT NULL DEFAULT 1，存量消息会自动记为 1（学生），正常情况下不会有 NULL。
-- 万一查出 NULL 行，放开下面这条 UPDATE 后再查一次。
SELECT receiver_type, COUNT(*) AS cnt FROM message GROUP BY receiver_type;
-- UPDATE message SET receiver_type = 1 WHERE receiver_type IS NULL;

-- ---------- 7. 结果核对 ----------
SELECT table_name, column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'news'         AND column_name = 'expire_time')
    OR (table_name = 'message'      AND column_name = 'receiver_type')
    OR (table_name = 'push_task'    AND column_name = 'target_role')
    OR (table_name = 'push_record'  AND column_name = 'receiver_type'))
ORDER BY table_name;
