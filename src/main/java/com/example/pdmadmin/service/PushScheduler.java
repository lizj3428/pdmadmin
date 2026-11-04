package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.PushTask;
import com.example.pdmadmin.mapper.PushTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时推送调度器
 * 【作用】每 30 秒扫一次库，把「定时 + 待执行 + 计划时间已到」的任务捞出来执行。
 *
 * 【为什么不用 Quartz】
 * 本项目只需要"到点扫表执行"这一种能力，Spring 自带的 @Scheduled 就够，
 * 多引一个 Quartz 只会多一套配置和依赖，收益为零。
 *
 * 【重复执行的风险】
 * @Scheduled 默认单线程串行执行，同一时刻只会有一次扫描在跑，
 * 所以同一个任务不会被同一次扫描执行两遍。以后要部署多台服务器，
 * 才需要加分布式锁，那是另一个话题。
 */
@Slf4j
@Component
public class PushScheduler {

    @Resource
    private PushTaskMapper pushTaskMapper;
    @Resource
    private PushService pushService;

    /** fixedDelay：上一次执行结束后再等 30 秒，避免任务堆积 */
    @Scheduled(fixedDelay = 30000)
    public void scanDueTasks() {
        List<PushTask> dueList = pushTaskMapper.selectList(new LambdaQueryWrapper<PushTask>()
                .eq(PushTask::getPushType, 2)          // 定时推送
                .eq(PushTask::getStatus, 0)            // 待执行
                .le(PushTask::getPlanTime, LocalDateTime.now())); // 计划时间已到

        if (dueList.isEmpty()) {
            return;
        }
        log.info("定时推送扫描：发现 {} 个到点任务", dueList.size());
        for (PushTask task : dueList) {
            try {
                pushService.execute(task);
            } catch (Exception e) {
                // 单个任务失败不能拖垮整轮扫描
                log.error("定时推送任务 {} 执行异常", task.getId(), e);
            }
        }
    }
}
