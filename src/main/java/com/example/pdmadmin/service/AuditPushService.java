package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.PushTask;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.PushTaskMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核通过后的自动推送
 *
 * 【解决什么问题】
 * 没有这个服务时，/news/audit 通过审核只是把 publish_status 改成 1，
 * 学生必须自己打开资讯列表去翻才看得到，消息中心也不会多一条。
 * 加上之后，管理员点「通过」的同一秒，系统自动把这条资讯推给该看的学生。
 *
 * 【推给谁】
 *   有标签且有人订阅  -> 只推给订阅了这些标签的学生（target_type=4）
 *   有标签但没人订阅  -> 全体学生（target_type=1，兜底，否则等于没发）
 *   没有标签          -> 全体学生
 *
 * 【为什么不重复推】
 * 任务名固定是「审核通过自动推送#资讯ID」，推送前先按这个名字查一次：
 * 已经推过就直接返回。否则「通过 -> 驳回 -> 教师改稿 -> 再通过」会推两遍，
 * 学生会收到重复消息。另外 status=3（失败）的允许重试，不然一次失败就永远补不上了。
 *
 * 【为什么异步】
 * 给 N 个学生要插 N 条 message + N 条 push_record，同步做会把审核接口拖到几百毫秒，
 * 而任务书要求接口响应 <200ms。放到 @Async 线程里执行，审核接口立刻返回。
 */
@Slf4j
@Service
public class AuditPushService {

    /** 自动任务的名字前缀，后面拼资讯 id，用于幂等判断 */
    private static final String AUTO_TASK_PREFIX = "审核通过自动推送#";

    private static final int TARGET_ALL = 1;
    private static final int TARGET_TAG = 4;
    private static final int CHANNEL_INBOX = 1;
    private static final int PUSH_NOW = 1;
    private static final int STATUS_FAILED = 3;

    @Resource
    private PushService pushService;
    @Resource
    private PushTaskMapper pushTaskMapper;
    @Resource
    private NewsTagMapper newsTagMapper;
    @Resource
    private NewsMapper newsMapper;

    /**
     * 资讯审核通过后调用，异步把这条资讯推给目标学生
     * 异常一律在这里吞掉并打日志：推送失败不该让审核操作报错
     */
    @Async
    public void pushOnAudit(Long newsId, Long operatorId) {
        try {
            doPush(newsId, operatorId);
        } catch (Exception e) {
            log.error("资讯 {} 审核通过自动推送失败", newsId, e);
        }
    }

    private void doPush(Long newsId, Long operatorId) {
        News news = newsMapper.selectById(newsId);
        if (news == null) {
            log.warn("自动推送跳过：资讯 {} 不存在", newsId);
            return;
        }

        String taskName = AUTO_TASK_PREFIX + newsId;
        PushTask exist = pushTaskMapper.selectOne(new LambdaQueryWrapper<PushTask>()
                .eq(PushTask::getNewsId, newsId)
                .eq(PushTask::getTaskName, taskName));
        if (exist != null && exist.getStatus() != STATUS_FAILED) {
            // 已经推过或正在推，直接结束
            log.info("资讯 {} 已推送过，跳过自动推送", newsId);
            return;
        }

        // 取这条资讯打的标签
        List<Long> tagIds = newsTagMapper.selectList(
                        new LambdaQueryWrapper<NewsTag>().eq(NewsTag::getNewsId, newsId))
                .stream().map(NewsTag::getTagId).toList();

        int targetType = TARGET_ALL;
        String targetValue = null;
        if (!tagIds.isEmpty()) {
            String tagValue = joinIds(tagIds);
            // 先试算一下：这些标签有没有人订阅。没人订阅就退化成全体
            if (!pushService.resolveTargets(TARGET_TAG, tagValue).isEmpty()) {
                targetType = TARGET_TAG;
                targetValue = tagValue;
            }
        }

        PushTask task = exist == null ? new PushTask() : exist;
        task.setNewsId(newsId);
        task.setTaskName(taskName);
        task.setTargetType(targetType);
        task.setTargetValue(targetValue);
        task.setChannel(CHANNEL_INBOX);
        task.setPushType(PUSH_NOW);
        task.setPlanTime(null);
        task.setStatus(0);
        task.setTargetCount(0);
        task.setCreateBy(operatorId == null ? 0L : operatorId);
        if (exist == null) {
            task.setCreateTime(LocalDateTime.now());
            pushTaskMapper.insert(task);
        } else {
            // 上次失败的，重置状态后重跑
            pushTaskMapper.updateById(task);
        }

        // 复用推送模块已有的执行逻辑：写 message、写 push_record、在线学生走 WebSocket 弹窗
        pushService.execute(task);
        log.info("资讯 {} 审核通过，自动推送完成：targetType={}, 目标人数={}",
                newsId, targetType, task.getTargetCount());
    }

    /** [1,4] -> "1,4"，PushService.resolveTargets 认这种逗号分隔的格式 */
    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
