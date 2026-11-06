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
              (SELECT COUNT(*) FROM message)                                AS msgTotal,
              (SELECT COUNT(*) FROM message WHERE read_flag = 0)            AS msgUnread
            """)
    Map<String, Object> overview();

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
