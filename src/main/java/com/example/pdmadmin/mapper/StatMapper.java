package com.example.pdmadmin.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据统计查询
 * 【作用】只做聚合查询，不落统计表、不建定时汇总。
 *        数据量小，实时查库最直接，也不存在"汇总表和明细表对不上账"的问题。
 *        别名统一显式写成 camelCase，避免下划线转驼峰把 Map 的 key 改掉。
 */
public interface StatMapper {

    /** 总览：一行多列，每列一个独立子查询 */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM news)                                  AS newsTotal,
              (SELECT COUNT(*) FROM news WHERE publish_status = 1)          AS newsPublished,
              (SELECT COUNT(*) FROM news WHERE audit_status = 0)            AS newsPending,
              (SELECT COALESCE(SUM(view_count), 0) FROM news)               AS viewTotal,
              (SELECT COUNT(*) FROM student)                                AS studentTotal,
              (SELECT COUNT(*) FROM student WHERE status = 1)               AS studentActive,
              (SELECT COUNT(*) FROM teacher)                                AS teacherTotal,
              (SELECT COUNT(*) FROM push_task)                              AS taskTotal,
              (SELECT COUNT(*) FROM push_record)                            AS pushTotal,
              (SELECT COUNT(*) FROM push_record WHERE send_status IN (1,2)) AS pushSuccess,
              (SELECT COUNT(*) FROM push_record
                WHERE send_status IN (1,2) AND receiver_type = 1)           AS pushStudentTotal,
              (SELECT COUNT(*) FROM push_record p
                 JOIN push_task t ON t.id = p.task_id
                 JOIN news_read_record r ON r.news_id = t.news_id
                                        AND r.student_id = p.student_id
                                        AND r.create_time >= p.send_time
                WHERE p.send_status IN (1,2) AND p.receiver_type = 1)       AS pushClicked,
              (SELECT COUNT(*) FROM message)                                AS msgTotal,
              (SELECT COUNT(*) FROM message WHERE read_flag = 0)            AS msgUnread
            """)
    Map<String, Object> overview();

    /**
     * 各推送任务的点击情况
     * 点击口径：该接收人收到这条资讯后（阅读记录时间 ≥ 发送时间）产生了阅读记录。
     * 教师端不写阅读记录表，所以点击率只统计学生侧，避免把教师算进分母拉低数值。
     */
    @Select("""
            SELECT t.id AS taskId, t.task_name AS taskName, t.news_id AS newsId,
                   COUNT(p.id) AS pushed,
                   COUNT(r.student_id) AS clicked
            FROM push_task t
            LEFT JOIN push_record p ON p.task_id = t.id
                                   AND p.receiver_type = 1
                                   AND p.send_status IN (1,2)
            LEFT JOIN news_read_record r ON r.news_id = t.news_id
                                        AND r.student_id = p.student_id
                                        AND r.create_time >= p.send_time
            WHERE t.status = 2
            GROUP BY t.id, t.task_name, t.news_id
            ORDER BY t.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> pushClickByTask(@Param("limit") int limit);

    /** 各分类下的资讯数量；用 LEFT JOIN 保证没有资讯的分类也能出现（数量为 0） */
    @Select("""
            SELECT c.id AS categoryId, c.name AS categoryName, COUNT(n.id) AS cnt
            FROM category c LEFT JOIN news n ON n.category_id = c.id
            GROUP BY c.id, c.name
            ORDER BY cnt DESC, c.id
            """)
    List<Map<String, Object>> newsByCategory();

    /** 阅读量排行前 N 条资讯 */
    @Select("""
            SELECT id, title, view_count AS viewCount, publish_time AS publishTime
            FROM news
            ORDER BY view_count DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> newsTop(@Param("limit") int limit);

    /** 近 N 天推送趋势：按发送日期分组，统计成功与失败条数 */
    @Select("""
            SELECT DATE_FORMAT(send_time, '%Y-%m-%d')                     AS statDate,
                   SUM(CASE WHEN send_status IN (1,2) THEN 1 ELSE 0 END)  AS successCount,
                   SUM(CASE WHEN send_status = 3 THEN 1 ELSE 0 END)       AS failCount
            FROM push_record
            WHERE send_time >= #{startDate}
            GROUP BY DATE_FORMAT(send_time, '%Y-%m-%d')
            ORDER BY statDate
            """)
    List<Map<String, Object>> pushDaily(@Param("startDate") LocalDate startDate);

    /** 推送发送状态分布：0待发送 1已提交 2已送达 3发送失败 */
    @Select("""
            SELECT send_status AS sendStatus, COUNT(*) AS cnt
            FROM push_record
            GROUP BY send_status
            ORDER BY send_status
            """)
    List<Map<String, Object>> pushStatus();

    /** 近 N 天新增学生数 */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate, COUNT(*) AS cnt
            FROM student
            WHERE create_time >= #{startDate}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY statDate
            """)
    List<Map<String, Object>> studentDaily(@Param("startDate") LocalDate startDate);
}
