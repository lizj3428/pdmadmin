package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Message;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.PushRecord;
import com.example.pdmadmin.entity.PushTask;
import com.example.pdmadmin.entity.Student;
import com.example.pdmadmin.entity.Subscribe;
import com.example.pdmadmin.mapper.MessageMapper;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.PushRecordMapper;
import com.example.pdmadmin.mapper.PushTaskMapper;
import com.example.pdmadmin.mapper.StudentMapper;
import com.example.pdmadmin.mapper.SubscribeMapper;
import com.example.pdmadmin.util.LoginUtil;
import com.example.pdmadmin.websocket.MessagePushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送执行服务
 * 【作用】推送模块的全部核心逻辑都在这里，Controller 只负责参数校验和调用。
 *
 * 【一次推送做四件事】
 *   1. 按 target_type 算出目标学生（全部 / 年级 / 专业 / 标签 / 班级）
 *   2. 给每个学生写一条站内信（message 表）
 *   3. 给每个学生写一条推送记录（push_record 表，记录成功/失败与耗时）
 *   4. 学生当前在线的话，通过 WebSocket 实时弹一条通知
 *
 * 【为什么不加事务】
 * 推送是"尽力而为"语义：给 100 个人发，第 37 个人失败，不该把前 36 条已发的消息回滚掉。
 * 所以每条记录单独落库、单独记 send_status，批量失败与否看记录表，比整批回滚更贴近真实推送系统。
 */
@Slf4j
@Service
public class PushService {

    /** 推送渠道：站内信（唯一真正实现的渠道） */
    private static final int CHANNEL_INBOX = 1;
    /** 目标人群类型：按标签 */
    private static final int TARGET_TAG = 4;
    /** 目标人群类型：按班级 */
    private static final int TARGET_CLASS = 5;
    /** 订阅对象类型：标签 */
    private static final int SUBSCRIBE_TAG = 2;
    /**
     * 序列化 WebSocket 通知用。
     * 不注入 Spring 的 ObjectMapper：本项目没有引 spring-boot-starter-json 的自动配置，
     * 容器里没有 ObjectMapper bean，注入会直接导致应用启动失败（实测过）。
     * 本地建一个静态实例就够，反正只序列化几个字符串字段。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private PushTaskMapper pushTaskMapper;
    @Resource
    private PushRecordMapper pushRecordMapper;
    @Resource
    private MessageMapper messageMapper;
    @Resource
    private SubscribeMapper subscribeMapper;
    @Resource
    private StudentMapper studentMapper;
    @Resource
    private NewsMapper newsMapper;
    @Resource
    private MessagePushService messagePushService;

    /**
     * 按任务条件算出目标学生（只算 status=1 启用的账号）
     * 预估人数和真正执行都走这一个方法，保证"显示多少人"和"实际发多少人"不会对不上
     */
    public List<Student> resolveTargets(Integer targetType, String targetValue) {
        if (targetType == null) {
            throw new RuntimeException("目标人群类型不能为空！");
        }
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getStatus, 1);

        switch (targetType) {
            case 1:
                // 全体学生：不加额外条件
                break;
            case 2:
                // 按年级：targetValue 形如 "2023,2022"
                wrapper.in(Student::getGrade, splitValues(targetValue));
                break;
            case 3:
                // 按专业：targetValue 形如 "计算机应用工程,软件工程"
                wrapper.in(Student::getMajor, splitValues(targetValue));
                break;
            case TARGET_CLASS:
                // 按班级：targetValue 形如 "23计算机1班,23计算机2班"
                wrapper.in(Student::getClassName, splitValues(targetValue));
                break;
            case TARGET_TAG:
                // 按标签：先找订阅了这些标签的学生 id，再按 id 查人
                List<Long> tagIds = parseIds(targetValue);
                if (tagIds.isEmpty()) {
                    return new ArrayList<>();
                }
                List<Long> studentIds = subscribeMapper.selectList(
                                new LambdaQueryWrapper<Subscribe>()
                                        .eq(Subscribe::getTargetType, SUBSCRIBE_TAG)
                                        .in(Subscribe::getTargetId, tagIds))
                        .stream().map(Subscribe::getStudentId).distinct().toList();
                if (studentIds.isEmpty()) {
                    return new ArrayList<>();
                }
                wrapper.in(Student::getId, studentIds);
                break;
            default:
                throw new RuntimeException("目标人群类型不正确！");
        }
        return studentMapper.selectList(wrapper);
    }

    /**
     * 执行一个推送任务
     * 调用方负责保证任务存在；这里负责把状态、记录、消息、实时通知全部落好
     */
    public void execute(PushTask task) {
        // 1. 抢占任务：把状态从待执行/失败改成执行中，抢不到就不许发
        int claimed = pushTaskMapper.claim(task.getId());
        if (claimed == 0) {
            throw new RuntimeException("推送任务已完成或正在执行，不能重复推送！");
        }
        task.setStatus(1);

        // 2. 关联资讯必须存在，否则推出去的消息没有标题正文
        News news = newsMapper.selectById(task.getNewsId());
        if (news == null) {
            markFailed(task, "关联资讯不存在");
            return;
        }

        // 3. 资讯必须已通过审核且已发布，草稿、驳回、下线的稿子一律不推
        if (news.getAuditStatus() == null || news.getAuditStatus() != 1
                || news.getPublishStatus() == null || news.getPublishStatus() != 1) {
            // 先落"失败"再抛：定时扫到的任务不会卡在待执行，手动点推送的人也能看到原因
            String reason = "资讯未通过审核或已下线，不能推送";
            markFailed(task, reason);
            throw new RuntimeException(reason);
        }

        // 4. 算目标人群
        List<Student> targets;
        try {
            targets = resolveTargets(task.getTargetType(), task.getTargetValue());
        } catch (Exception e) {
            markFailed(task, "目标人群解析失败：" + e.getMessage());
            return;
        }

        // 5. 逐个学生发
        for (Student student : targets) {
            long start = System.currentTimeMillis();
            PushRecord record = new PushRecord();
            record.setTaskId(task.getId());
            record.setStudentId(student.getId());
            record.setChannel(task.getChannel());
            record.setRetryCount(0);
            record.setSendTime(LocalDateTime.now());

            if (task.getChannel() != null && task.getChannel() == CHANNEL_INBOX) {
                // 4.1 站内信：先落库，学生下次打开消息中心能看到
                Message message = new Message();
                message.setStudentId(student.getId());
                message.setTitle(news.getTitle());
                message.setContent(news.getSummary() == null ? "" : news.getSummary());
                message.setNewsId(news.getId());
                message.setMsgType(1);   // 1 资讯通知
                message.setReadFlag(0);  // 未读
                message.setCreateTime(LocalDateTime.now());
                saveMessageIfAbsent(message);

                record.setSendStatus(1); // 1 已提交
                messagePushService.sendToUser(
                        LoginUtil.loginId(LoginUtil.ROLE_STUDENT, student.getId()),
                        buildWsMessage(task, news));
            } else {
                // 4.2 微信 / 企业微信：没有对接实现，如实记为失败，不假装发成功
                record.setSendStatus(3);
                record.setFailReason("渠道未接入");
            }
            record.setCostMs((int) (System.currentTimeMillis() - start));
            pushRecordMapper.insert(record);
        }

        // 6. 回填目标人数与完成状态
        task.setTargetCount(targets.size());
        task.setStatus(2);
        pushTaskMapper.updateById(task);
    }

    /** 执行失败：状态置 3，异常信息写日志（任务表没有失败原因列，不硬塞） */
    private void markFailed(PushTask task, String reason) {
        task.setStatus(3);
        pushTaskMapper.updateById(task);
        log.warn("推送任务 {} 执行失败：{}", task.getId(), reason);
    }

    private void saveMessageIfAbsent(Message message) {
        LambdaQueryWrapper<Message> existing = new LambdaQueryWrapper<Message>()
                .eq(Message::getStudentId, message.getStudentId())
                .eq(Message::getNewsId, message.getNewsId());
        if (messageMapper.exists(existing)) {
            return;
        }
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            if (!messageMapper.exists(existing)) {
                throw exception;
            }
        }
    }

    /**
     * 拼 WebSocket 实时通知的 JSON
     * 前端 Index.vue 只认 title / content 两个字段，其余是给以后扩展用的上下文
     */
    private String buildWsMessage(PushTask task, News news) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "news");
        map.put("taskId", task.getId());
        map.put("newsId", news.getId());
        map.put("title", news.getTitle());
        map.put("content", news.getSummary() == null ? "" : news.getSummary());
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            // 序列化失败也不能让整个推送循环断掉，降级成一条纯文本
            return "{\"title\":\"新资讯\",\"content\":\"您有一条新的校园资讯\"}";
        }
    }

    /** "2023,2022" -> ["2023","2022"]，去空白、去空项 */
    private List<String> splitValues(String targetValue) {
        List<String> result = new ArrayList<>();
        if (targetValue == null || targetValue.trim().isEmpty()) {
            return result;
        }
        for (String item : targetValue.split(",")) {
            String v = item.trim();
            if (!v.isEmpty()) {
                result.add(v);
            }
        }
        return result;
    }

    /** "1,4" -> [1,4]，用于标签 id */
    private List<Long> parseIds(String targetValue) {
        List<Long> result = new ArrayList<>();
        for (String item : splitValues(targetValue)) {
            try {
                result.add(Long.valueOf(item));
            } catch (NumberFormatException e) {
                throw new RuntimeException("标签ID格式不正确：" + item);
            }
        }
        return result;
    }
}
