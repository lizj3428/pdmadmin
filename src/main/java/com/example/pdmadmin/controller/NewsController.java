package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.TagMapper;
import com.example.pdmadmin.response.R;
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
        // 创建人取当前登录账号的 id；authorType 暂时固定 1（管理员），
        // 等教师端登录接口写好后，再根据登录角色区分 1管理员 / 2教师
        // 当前登录账号的 id。登录标识现在是 "admin:1" 这种带前缀的字符串，
        // 不能再用 StpUtil.getLoginIdAsLong()（会抛 NumberFormatException），
        // 统一走 LoginUtil.currentId() 剥前缀取数字
        news.setAuthorId(LoginUtil.currentId());
        news.setAuthorType(1);
        // 状态字段一律由后端决定，不信任前端传来的值，否则谁都能直接发未审核的内容
        news.setAuditStatus(0);
        news.setPublishStatus(0);
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
        news.setPublishTime(null);
        news.setViewCount(null);
        news.setAuthorId(null);
        news.setUpdateTime(LocalDateTime.now());
        newsMapper.updateById(news);
        // tagNames 为 null 表示「这次不碰标签」（比如只改标题）；
        // 传空数组 [] 才表示「把这条资讯的标签清空」。两种语义必须区分开
        if (news.getTagNames() != null) {
            saveNewsTags(news.getId(), news.getTagNames());
        }
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

    @Operation(summary = "资讯详情", description = "返回资讯内容与标签，同时把阅读量 +1")
    @PostMapping("/news/detail")
    @SaCheckLogin
    public R<News> detail(@RequestParam Long id) {
        // 用 SQL 自增而不是「查出来 +1 再写回」：
        // 后者在多人同时打开时会互相覆盖，阅读量会少算
        newsMapper.update(null, new LambdaUpdateWrapper<News>()
                .eq(News::getId, id)
                .setSql("view_count = view_count + 1"));
        // 再查一次，让返回值里的阅读量是自增后的新值
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
                   @RequestParam(required = false) String auditOpinion) {
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            throw new RuntimeException("审核结果只能是 1（通过）或 2（驳回）！");
        }
        News news = newsMapper.selectById(id);
        if (news == null) {
            throw new RuntimeException("资讯不存在！");
        }
        News update = new News();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        update.setAuditOpinion(auditOpinion);
        update.setUpdateTime(LocalDateTime.now());
        if (auditStatus == 1) {
            // 审核通过 = 同时发布，否则「通过」了还是看不见
            update.setPublishStatus(1);
            update.setPublishTime(LocalDateTime.now());
        } else {
            // 驳回：保持未发布，已经发布过的也一并下线
            update.setPublishStatus(0);
        }
        newsMapper.updateById(update);
        redisTemplate.delete(HOT_CACHE_KEY);
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
