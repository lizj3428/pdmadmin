package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.pdmadmin.entity.Message;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.PushTask;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.mapper.MessageMapper;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.PushTaskMapper;
import com.example.pdmadmin.mapper.TagMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.service.AuditPushService;
import com.example.pdmadmin.service.NewsPinService;
import com.example.pdmadmin.service.PushService;
import com.example.pdmadmin.util.LoginUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 资讯管理
 * 【作用】平台的内容主体：发布、修改、审核、下架、阅读。
 *        论文第5章 5.2.1（NewsController.publish）和 5.2.2（AuditController.audit）
 *        对应的就是这里的 /news/add 与 /news/audit。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "资讯管理", description = "资讯的增删改查、审核、热点列表")
@RestController
@SaCheckRole("admin")
public class NewsController {

    /** 热点资讯的 Redis 缓存 key。所有写操作之后都要删掉它，否则会读到旧数据 */
    private static final String HOT_CACHE_KEY = "news:hot:top10";

    @Resource
    private NewsMapper newsMapper;
    @Resource
    private NewsTagMapper newsTagMapper;
    @Resource
    private TagMapper tagMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private AuditPushService auditPushService;
    @Resource
    private NewsPinService newsPinService;
    @Resource
    private MessageMapper messageMapper;
    @Resource
    private PushTaskMapper pushTaskMapper;
    @Resource
    private PushService pushService;

    @Operation(summary = "资讯分页列表", description = "支持标题模糊搜索、按分类/审核状态/发布状态筛选，按创建时间倒序")
    @PostMapping("/news/list")
    @SaCheckLogin
    public R<PageInfo<News>> list(@RequestBody News news,
                                  @RequestParam Integer pageNum,
                                  @RequestParam Integer pageSize) {
        LambdaQueryWrapper<News> wrapper = new LambdaQueryWrapper<>();
        // 标题模糊搜：没传就不加这个条件
        wrapper.like(news.getTitle() != null && !news.getTitle().trim().isEmpty(),
                News::getTitle, news.getTitle());
        wrapper.eq(news.getCategoryId() != null, News::getCategoryId, news.getCategoryId());
        wrapper.eq(news.getAuditStatus() != null, News::getAuditStatus, news.getAuditStatus());
        wrapper.eq(news.getPublishStatus() != null, News::getPublishStatus, news.getPublishStatus());
        wrapper.eq(news.getAuthorId() != null, News::getAuthorId, news.getAuthorId());
        wrapper.orderByDesc(News::getCreateTime);

        PageHelper.startPage(pageNum, pageSize);
        List<News> list = newsMapper.selectList(wrapper);
        return R.data(new PageInfo<>(list));
    }

    @Operation(summary = "热点资讯", description = "取已发布资讯中阅读量最高的 10 条，结果缓存在 Redis 5 分钟")
    @PostMapping("/news/hot")
    @SaCheckLogin
    @SuppressWarnings("unchecked")
    public R<List<News>> hot() {
        // 先读缓存。命中就直接返回，不去查数据库——这就是论文里说的「Redis 缓存热点资讯」
        Object cached = redisTemplate.opsForValue().get(HOT_CACHE_KEY);
        if (cached != null) {
            return R.data((List<News>) cached);
        }
        // 缓存没有（首次访问 / 刚被写操作删掉）：查库
        LambdaQueryWrapper<News> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(News::getPublishStatus, 1);   // 只算已发布的
        wrapper.orderByDesc(News::getViewCount); // 阅读量高的排前面
        wrapper.last("limit 10");
        List<News> list = newsMapper.selectList(wrapper);
        // 写回缓存，5 分钟后自动失效；不设永久是为了防止数据长期不更新
        redisTemplate.opsForValue().set(HOT_CACHE_KEY, list, 5, TimeUnit.MINUTES);
        return R.data(list);
    }

    @Operation(summary = "新增资讯", description = "新增后状态为待审核（audit_status=0），需管理员审核通过才会发布")
    @PostMapping("/news/add")
    @SaCheckLogin
    public R add(@RequestBody News news) {
        if (news.getTitle() == null || news.getTitle().trim().isEmpty()) {
            throw new RuntimeException("资讯标题不能为空！");
        }
        // 创建人取当前登录账号的 id；authorType 按登录角色区分：1管理员 2教师
        // 当前登录账号的 id。登录标识现在是 "admin:1" 这种带前缀的字符串，
        // 不能再用 StpUtil.getLoginIdAsLong()（会抛 NumberFormatException），
        // 统一走 LoginUtil.currentId() 剥前缀取数字
        news.setAuthorId(LoginUtil.currentId());
        news.setAuthorType(LoginUtil.ROLE_TEACHER.equals(LoginUtil.currentRole()) ? 2 : 1);
        // 状态字段一律由后端决定，不信任前端传来的值，否则谁都能直接发未审核的内容
        news.setAuditStatus(0);
        news.setPublishStatus(0);
        news.setTopFlag(0);
        news.setPublishTime(null);
        news.setViewCount(0);
        news.setCreateTime(LocalDateTime.now());
        news.setUpdateTime(LocalDateTime.now());
        newsMapper.insert(news);
        // insert 之后 news.getId() 才有值，才能建标签关联
        saveNewsTags(news.getId(), news.getTagNames());
        // 内容变了，热点缓存必须清掉，不然用户看到的还是旧列表
        redisTemplate.delete(HOT_CACHE_KEY);
        return R.success();
    }

    @Operation(summary = "修改资讯", description = "只改内容字段，不改变审核与发布状态（改状态走 /news/audit）")
    @PostMapping("/news/update")
    @SaCheckLogin
    public R update(@RequestBody News news) {
        if (news.getId() == null) {
            throw new RuntimeException("资讯ID不能为空！");
        }
        // 状态相关的字段在这里一律置 null，MyBatis-Plus 不会更新 null 字段，
        // 于是它们保持原值——防止通过「修改」接口绕过审核直接发布
        news.setAuditStatus(null);
        news.setPublishStatus(null);
        news.setTopFlag(null);
        news.setPublishTime(null);
        news.setViewCount(null);
        news.setAuthorId(null);
        news.setUpdateTime(LocalDateTime.now());
        newsMapper.updateById(news);
        // 有效期允许被「清空」：updateById 会跳过 null 字段，清空就写不进去，
        // 所以单独用 update wrapper 无条件覆盖，null 即长期有效
        newsMapper.update(null, new LambdaUpdateWrapper<News>()
                .eq(News::getId, news.getId())
                .set(News::getExpireTime, news.getExpireTime()));
        // tagNames 为 null 表示「这次不碰标签」（比如只改标题）；
        // 传空数组 [] 才表示「把这条资讯的标签清空」。两种语义必须区分开
        if (news.getTagNames() != null) {
            saveNewsTags(news.getId(), news.getTagNames());
        }
        redisTemplate.delete(HOT_CACHE_KEY);
        return R.success();
    }

    @Operation(summary = "设置资讯置顶", description = "仅已审核且已发布的资讯可以设置；topFlag=1 置顶，0 取消")
    @PostMapping("/news/top")
    @SaCheckLogin
    public R setTop(@RequestParam Long id, @RequestParam Integer topFlag) {
        newsPinService.setTopFlag(id, topFlag);
        redisTemplate.delete(HOT_CACHE_KEY);
        return R.success();
    }

    @Operation(summary = "删除资讯", description = "支持批量，同时清除这批资讯的标签关联记录")
    @PostMapping("/news/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids) {
        // 先删标签关联，再删资讯本身：不先删会留下指向不存在资讯的孤儿记录
        newsTagMapper.delete(new LambdaQueryWrapper<NewsTag>().in(NewsTag::getNewsId, ids));
        newsMapper.deleteByIds(ids);
        redisTemplate.delete(HOT_CACHE_KEY);
        return R.success();
    }

    @Operation(summary = "资讯详情", description = "返回资讯内容与标签")
    @PostMapping("/news/detail")
    @SaCheckLogin
    public R<News> detail(@RequestParam Long id) {
        News news = newsMapper.selectById(id);
        if (news == null) {
            throw new RuntimeException("资讯不存在！");
        }
        // 标签只在详情里带，列表接口不带：列表一页 10 条要是每条都查一次标签，就是 10 次额外查询
        List<Long> tagIds = newsTagMapper.selectList(
                        new LambdaQueryWrapper<NewsTag>().eq(NewsTag::getNewsId, id))
                .stream().map(NewsTag::getTagId).toList();
        news.setTagNames(tagIds.isEmpty() ? List.of()
                : tagMapper.selectByIds(tagIds).stream().map(Tag::getName).toList());
        return R.data(news);
    }

    @Operation(summary = "审核资讯", description = "auditStatus=1 通过并发布；auditStatus=2 驳回并记录审核意见")
    @PostMapping("/news/audit")
    @SaCheckLogin
    public R audit(@RequestParam Long id,
                   @RequestParam Integer auditStatus,
                   @RequestParam(required = false) String auditOpinion,
                   @RequestParam(required = false, defaultValue = "true") Boolean autoPublish) {
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            throw new RuntimeException("审核结果只能是 1（通过）或 2（驳回）！");
        }
        News news = newsMapper.selectById(id);
        if (news == null) {
            throw new RuntimeException("资讯不存在！");
        }
        // 教师不能审自己投的稿，否则「投稿→审核」等于自己批自己。
        // 必须同时判 authorType：admin 和 teacher 是两张表，主键都从 1 开始，
        // 只比 authorId 会把管理员发的稿（authorId=1）误判成教师 1 自己的。
        if (LoginUtil.ROLE_TEACHER.equals(LoginUtil.currentRole())
                && news.getAuthorType() != null && news.getAuthorType() == 2
                && news.getAuthorId() != null && news.getAuthorId().equals(LoginUtil.currentId())) {
            throw new RuntimeException("不能审核自己提交的资讯！");
        }
        News update = new News();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        update.setAuditOpinion(auditOpinion);
        update.setUpdateTime(LocalDateTime.now());
        // 通过时是否顺手发布：默认发布（保持原有行为）。
        // 管理员勾了「通过后暂不发布」就不动 publish_status，等他在资讯列表点「发布」走发布配置页
        boolean publishNow = auditStatus == 1 && (autoPublish == null || autoPublish);
        if (publishNow) {
            // 审核通过 = 同时发布，否则「通过」了还是看不见
            update.setPublishStatus(1);
            update.setPublishTime(LocalDateTime.now());
        } else if (auditStatus == 2) {
            // 驳回：保持未发布，已经发布过的也一并下线
            update.setPublishStatus(0);
            update.setTopFlag(0);
        }
        newsMapper.updateById(update);
        redisTemplate.delete(HOT_CACHE_KEY);
        if (publishNow) {
            // 通过审核 = 发布，同时把这条资讯推给她该到的人：
            // 有标签且有人订阅就按标签定向推，否则兜底推给全体学生。
            // 异步执行，不占用审核接口的响应时间（详见 AuditPushService 注释）
            auditPushService.pushOnAudit(id, LoginUtil.currentId());
        }
        noticeAuthorIfTeacher(news, auditStatus, auditOpinion);
        return R.success();
    }

    /**
     * 审核结果通知教师
     * 【为什么要判 author_type】教师和管理员的主键都从 1 开始，教师投稿是 author_type=2，
     *        只给这类稿子的作者发通知，管理员自己发的稿不用通知自己。
     * 【为什么复用 student_id 列】message 表原本只给学生发消息，加 receiver_type 区分接收人类型，
     *        接收人 ID 仍存在 student_id 列里，避免为了一个字段再开一张表。
     */
    private void noticeAuthorIfTeacher(News news, Integer auditStatus, String auditOpinion) {
        if (news.getAuthorType() == null || news.getAuthorType() != 2 || news.getAuthorId() == null) {
            return;
        }
        Message notice = new Message();
        notice.setReceiverType(2);
        notice.setStudentId(news.getAuthorId());
        notice.setNewsId(news.getId());
        notice.setMsgType(3);   // 3 审核结果通知
        notice.setReadFlag(0);
        notice.setTitle("资讯审核结果通知");
        String opinion = (auditOpinion == null || auditOpinion.trim().isEmpty())
                ? "" : "，审核意见：" + auditOpinion;
        notice.setContent(auditStatus == 1
                ? "您的投稿《" + news.getTitle() + "》已通过审核" + opinion
                : "您的投稿《" + news.getTitle() + "》未通过审核" + opinion);
        notice.setCreateTime(LocalDateTime.now());
        messageMapper.insert(notice);
    }

    @Operation(summary = "发布资讯", description = "审核通过但未发布的资讯在这里配置发布时间、目标人群与推送渠道")
    @PostMapping("/news/publish")
    @SaCheckLogin
    public R publish(@RequestParam Long id,
                     @RequestParam(required = false) String publishTime,
                     @RequestParam(required = false) Integer targetType,
                     @RequestParam(required = false) String targetValue,
                     @RequestParam(required = false, defaultValue = "1") Integer channel) {
        News news = newsMapper.selectById(id);
        if (news == null) {
            throw new RuntimeException("资讯不存在！");
        }
        if (news.getAuditStatus() == null || news.getAuditStatus() != 1) {
            throw new RuntimeException("资讯尚未通过审核，无法发布！");
        }
        if (news.getPublishStatus() != null && news.getPublishStatus() == 1) {
            throw new RuntimeException("该资讯已发布，不可重复发布！");
        }

        // 发布时间：留空或填了过去的时间，都按「立即发布」处理
        LocalDateTime planTime = null;
        if (publishTime != null && !publishTime.trim().isEmpty()) {
            try {
                planTime = LocalDateTime.parse(publishTime.trim());
            } catch (Exception e) {
                throw new RuntimeException("发布时间格式不正确！");
            }
        }
        boolean immediate = planTime == null || planTime.isBefore(LocalDateTime.now());

        News update = new News();
        update.setId(id);
        update.setPublishStatus(1);
        update.setPublishTime(immediate ? LocalDateTime.now() : planTime);
        update.setUpdateTime(LocalDateTime.now());
        newsMapper.updateById(update);
        redisTemplate.delete(HOT_CACHE_KEY);

        // 没选目标人群就只发布，不推送
        if (targetType == null) {
            return R.success();
        }
        if (targetType != 1 && (targetValue == null || targetValue.trim().isEmpty())) {
            throw new RuntimeException("请填写目标人群取值！");
        }
        PushTask task = new PushTask();
        task.setNewsId(id);
        task.setTaskName("发布推送#" + id);
        task.setTargetType(targetType);
        task.setTargetValue(targetType == 1 ? null : targetValue);
        task.setChannel(channel == null ? 1 : channel);
        task.setPushType(immediate ? 1 : 2);
        task.setPlanTime(immediate ? null : planTime);
        task.setStatus(0);
        task.setTargetCount(0);
        task.setCreateBy(LoginUtil.currentId());
        task.setCreateTime(LocalDateTime.now());
        pushTaskMapper.insert(task);
        // 立即发布的当场推；定时发布的落库后由 PushScheduler 到点扫表执行
        if (immediate) {
            pushService.execute(task);
        }
        return R.success();
    }

    /**
     * 保存资讯的标签关联
     * 先删后插：逐个比对增删容易漏，直接清空重建最可靠
     * 标签名不存在时自动创建（老师发资讯时顺手打上的新标签，不该逼他先去标签管理页建一遍）
     */
    private void saveNewsTags(Long newsId, List<String> tagNames) {
        newsTagMapper.delete(new LambdaQueryWrapper<NewsTag>().eq(NewsTag::getNewsId, newsId));
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }
        // 去重：前端可能传两个「考研」，而 news_tag 上有 (news_id, tag_id) 唯一索引，
        // 重复插入会直接报 Duplicate entry
        Set<String> names = new LinkedHashSet<>();
        for (String name : tagNames) {
            if (name != null && !name.trim().isEmpty()) {
                names.add(name.trim());
            }
        }
        for (String name : names) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tag.setUseCount(0);
                tag.setCreateTime(LocalDateTime.now());
                tagMapper.insert(tag);
            }
            NewsTag link = new NewsTag();
            link.setNewsId(newsId);
            link.setTagId(tag.getId());
            link.setCreateTime(LocalDateTime.now());
            newsTagMapper.insert(link);
        }
    }
}
