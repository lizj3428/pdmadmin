-- ============================================================
-- 升级脚本：给「学生 / 教师」增加管理端权限标记
--
-- 【什么时候执行】
--   数据库是在 2026-11-06 之前建的（student、teacher 表里还没有 admin_flag 列）。
--   新库直接跑 pdm_schema.sql 就已经带上了，不用执行本脚本。
--
-- 【执行方式】
--   在 Navicat 里打开本文件，选中两条 ALTER 一起运行，执行一次即可。
--   如果报 "Duplicate column name 'admin_flag'"，说明已经加过了，忽略即可。
--
-- 【语义】
--   admin_flag = 1：该教师/学生被授权进入管理端，登录后角色为 [teacher, admin]，
--                   管理端所有接口都能用，权限和原管理员完全一样。
--   admin_flag = 0：普通教师只能走 /tch/* 投稿线；普通学生只能走 /stu/* 学生端。
-- ============================================================

ALTER TABLE student
    ADD COLUMN admin_flag TINYINT NOT NULL DEFAULT 0 COMMENT '管理端权限 1有(学生会等) 0无';

ALTER TABLE teacher
    ADD COLUMN admin_flag TINYINT NOT NULL DEFAULT 0 COMMENT '管理端权限 1有 0无(普通教师只能投稿)';


-- ------------------------------------------------------------
-- 两个教师测试账号（密码都是 123456）
--   T001 普通教师：登录后进不了管理端，只能投稿
--   T002 宣传部老师：已授权，登录后直接进管理端
-- 用 ON DUPLICATE KEY 保证重复执行不会报「教工号已存在」
-- ------------------------------------------------------------
INSERT INTO teacher (teacher_no, password, real_name, gender, phone, dept_name, title, admin_flag, status)
VALUES ('T001', '$2a$10$D99U46SmioRNIc0oIL3m0uZUMrel7IUeAtCDfqKGHTB8alixiGHf2',
        '王建国', '男', '13800000001', '计算机学院', '讲师', 0, 1),
       ('T002', '$2a$10$D99U46SmioRNIc0oIL3m0uZUMrel7IUeAtCDfqKGHTB8alixiGHf2',
        '李红梅', '女', '13800000002', '党委宣传部', '副教授', 1, 1)
ON DUPLICATE KEY UPDATE teacher_no = VALUES(teacher_no);


-- ------------------------------------------------------------
-- 想把某个学生设成学生会的（有管理端权限），把 id 换成真实学生 id 后执行；
-- 也可以登录管理端，在学生列表里点「设为管理员」按钮，效果一样。
-- ------------------------------------------------------------
-- UPDATE student SET admin_flag = 1 WHERE student_no = '23103428';
