-- ============================================================
-- 升级脚本：message 表补「接收人类型」并重建唯一键
--
-- 【为什么要改】
--   唯一键原来是 (student_id, news_id)。加入教师通知后，教师和学生的
--   主键都从 1 开始，一位教师 id=1 和一位学生 id=1 收到同一篇资讯时，
--   第二条消息会撞键，接口直接报 500。
--
-- 【什么时候执行】
--   数据库是在 2026-11-13 之前建的（message 表还没有 receiver_type 列）。
--   新库直接跑 pdm_schema.sql 就已经是 (receiver_type, student_id, news_id)，
--   不用执行本脚本。脚本可重复执行。
--
-- 【执行方式】
--   mysql -uroot -p<你的密码> --default-character-set=utf8mb4 pdmdb < upgrade_message_unique.sql
-- ============================================================

SET NAMES utf8mb4;
USE pdmdb;

-- 1. 缺 receiver_type 就补上（存量消息按 DEFAULT 1 视为学生消息）
SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND column_name = 'receiver_type'
    ),
    'DO 0',
    'ALTER TABLE message ADD COLUMN receiver_type TINYINT NOT NULL DEFAULT 1 COMMENT ''接收人类型 1学生 2教师'' AFTER id'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 先建新唯一键（含接收人类型），建成功后再删旧的，避免中途无唯一约束
SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'uk_msg_receiver_news'
    ),
    'DO 0',
    'ALTER TABLE message ADD UNIQUE KEY uk_msg_receiver_news (receiver_type, student_id, news_id)'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'uk_msg_student_news'
    ),
    'ALTER TABLE message DROP INDEX uk_msg_student_news',
    'DO 0'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. 修正列注释与论文表4-11 对齐（重复执行无副作用）
ALTER TABLE message
    MODIFY COLUMN student_id BIGINT NOT NULL COMMENT '接收人ID，receiver_type=1 关联学生表，=2 关联教师表',
    MODIFY COLUMN msg_type   TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型 1资讯通知 2系统公告 3审核结果通知';

-- 4. 结果自检
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'message'
GROUP BY index_name;
