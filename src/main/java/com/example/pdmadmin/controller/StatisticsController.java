package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.pdmadmin.mapper.StatMapper;
import com.example.pdmadmin.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据统计
 * 【作用】给管理端「数据看板」提供聚合数据：资讯、用户、推送三块。
 *        全部实时查库，不落统计表、不做定时汇总 —— 数据量小，实时最准。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "数据统计", description = "资讯、用户、推送的实时统计")
@RestController
@SaCheckRole("admin")
public class StatisticsController {

    @Resource
    private StatMapper statMapper;

    @Operation(summary = "总览看板", description = "返回资讯数、阅读总量、学生教师数、推送人次、站内信未读数等 12 项指标")
    @PostMapping("/stat/overview")
    @SaCheckLogin
    public R<Map<String, Object>> overview() {
        return R.data(statMapper.overview());
    }

    @Operation(summary = "分类资讯分布", description = "按分类统计资讯数量，没有资讯的分类也会返回（数量 0）")
    @PostMapping("/stat/newsByCategory")
    @SaCheckLogin
    public R<List<Map<String, Object>>> newsByCategory() {
        return R.data(statMapper.newsByCategory());
    }

    @Operation(summary = "阅读量排行", description = "按 view_count 倒序取前 N 条，默认 10 条")
    @PostMapping("/stat/newsTop")
    @SaCheckLogin
    public R<List<Map<String, Object>>> newsTop(@RequestParam(defaultValue = "10") int limit) {
        return R.data(statMapper.newsTop(limit));
    }

    @Operation(summary = "推送趋势", description = "近 N 天每天的成功/失败推送条数，默认 7 天")
    @PostMapping("/stat/pushDaily")
    @SaCheckLogin
    public R<List<Map<String, Object>>> pushDaily(@RequestParam(defaultValue = "7") int days) {
        return R.data(statMapper.pushDaily(startDate(days)));
    }

    @Operation(summary = "推送状态分布", description = "0待发送 1已提交 2已送达 3发送失败，各有多少条")
    @PostMapping("/stat/pushStatus")
    @SaCheckLogin
    public R<List<Map<String, Object>>> pushStatus() {
        return R.data(statMapper.pushStatus());
    }

    @Operation(summary = "学生增长趋势", description = "近 N 天每天新增的学生数，默认 7 天")
    @PostMapping("/stat/studentDaily")
    @SaCheckLogin
    public R<List<Map<String, Object>>> studentDaily(@RequestParam(defaultValue = "7") int days) {
        return R.data(statMapper.studentDaily(startDate(days)));
    }

    /** 起始日期在 Java 里算好再传 SQL：不用 INTERVAL ? DAY，避开 MySQL 预处理参数的方言坑 */
    private LocalDate startDate(int days) {
        return LocalDate.now().minusDays(Math.max(days, 1) - 1L);
    }
}
