package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Category;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.mapper.CategoryMapper;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.TagMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
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

/**
 * 教师投稿
 *
 * 【和 NewsController 的区别】
 * NewsController 是管理端的资讯管理，能看全部、能审核、能删除任何人发的；
 * 这里是普通教师自己的投稿箱，四个接口全部带「只能操作自己」的限制，
 * 查不到归属就直接拒绝。两套接口分开，比在一个类里塞一堆 if 判断角色更清楚。
 *
 * 【流转】
 * 教师新增 -> audit_status=0（待审核）、publish_status=0（未发布）
 *        -> 管理员在资讯审核页点通过 -> audit_status=1 且 publish_status=1
 *        -> 学生端才能看到。
 * 被驳回（audit_status=2）的稿子，教师改完会重新回到待审核。
 *
 * 【角色】
 * 类上锁 @SaCheckRole("teacher")：
 *   普通教师      角色 = [teacher]            -> 放行
 *   被授权进管理端 角色 = [teacher, admin]     -> 也放行（他照样能投自己的稿）
 *   学生          角色 = [student]            -> 403
 *
 * 【为什么不直接让普通教师调 /news/add】
 * 那个接口在管理端的 NewsController 上，类级 @SaCheckRole("admin")，
 * 而且 /news/list 会返回所有人的资讯。教师只需要自己的投稿箱，权限越小越安全。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "教师投稿", description = "普通教师发布资讯，管理员审核后发布")
@RestController
@SaCheckRole("teacher")
public class TchNewsController {

    /** 和管理端共用同一个热点缓存 key，教师投稿后也要清，否则热点列表是旧的 */
    private static final String HOT_CACHE_KEY = "news:hot:top10";

    @Resource
    private NewsMapper newsMapper;
    @Resource
    private NewsTagMapper newsTagMapper;
    @Resource
    private TagMapper tagMapper;
    @Resource
    private CategoryMapper categoryMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /* ---------- 下面两个只读接口：教师投稿时要用分类和标签下拉 ---------- */

    /*
     * 为什么不在前端直接调 /category/list 和 /tag/list：
     * 那两个接口在管理端的 CategoryController、TagController 上，
     * 类级 @SaCheckRole("admin")，普通教师是 403。
     * 与其放宽管理端权限（那会让教师也能看到不该看的配置），
     * 不如在教师端这边单独开两个只读接口，权限边界更清楚。
     */

    @Operation(summary = "分类下拉", description = "教师投稿时选择所属分类，只读")
    @PostMapping("/tch/category/list")
    @SaCheckLogin
    public R<List<Category>> categories() {
        return R.data(categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getId)));
    }

    @Operation(summary = "标签下拉", description = "教师投稿时选择已有标签，只读")
    @PostMapping("/tch/tag/list")
    @SaCheckLogin
    public R<List<Tag>> tags() {
        return R.data(tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().orderByDesc(Tag::getUseCount)));
    }

    @Operation(summary = "我的投稿列表", description = "只返回当前教师自己发的资讯，可按审核状态、分类筛选")
    @PostMapping("/tch/news/list")
    @SaCheckLogin
    public R<PageInfo<News>> list(@RequestBody News news,
                                  @RequestParam Integer pageNum,
                                  @RequestParam Integer pageSize) {
        LambdaQueryWrapper<News> wrapper = new LambdaQueryWrapper<>();
        // 这两个条件是硬性的，不接收前端传值：越权看别人的稿子全靠它们挡住
        wrapper.eq(News::getAuthorId, LoginUtil.currentId());
        wrapper.eq(News::getAuthorType, 2);

        wrapper.like(news.getTitle() != null && !news.getTitle().trim().isEmpty(),
                News::getTitle, news.getTitle());
        wrapper.eq(news.getCategoryId() != null, News::getCategoryId, news.getCategoryId());
        wrapper.eq(news.getAuditStatus() != null, News::getAuditStatus, news.getAuditStatus());
        wrapper.orderByDesc(News::getCreateTime);

        PageHelper.startPage(pageNum, pageSize);
        List<News> list = newsMapper.selectList(wrapper);
        return R.data(new PageInfo<>(list));
    }

    @Operation(summary = "新增投稿", description = "创建人和类型由后端按登录身份写死；状态固定为待审核，不能由前端指定")
    @PostMapping("/tch/news/add")
    @SaCheckLogin
    public R add(@RequestBody News news) {
        news.setTitle(validateUniqueTitle(news.getTitle(), news.getCategoryId(), null));
        // 作者一律取当前登录教师，前端传什么都不认
        news.setAuthorId(LoginUtil.currentId());
        news.setAuthorType(2);
        news.setAuditStatus(0);
        news.setPublishStatus(0);
        news.setTopFlag(0);
        news.setPublishTime(null);
        news.setAuditOpinion(null);
        news.setViewCount(0);
        news.setCreateTime(LocalDateTime.now());
        news.setUpdateTime(LocalDateTime.now());
        newsMapper.insert(news);
        saveNewsTags(news.getId(), news.getTagNames());
        redisTemplate.delete(HOT_CACHE_KEY);
        return R.success();
    }

    @Operation(summary = "修改投稿",
            description = "只能改自己的、且未通过审核的稿子；改完会重新回到待审核，并清掉上次的驳回意见")
    @PostMapping("/tch/news/update")
    @SaCheckLogin
    public R update(@RequestBody News news) {
        News exist = requireMine(news.getId());
        // 已通过的稿子如果还能随便改，等于绕过了审核
        if (exist.getAuditStatus() != null && exist.getAuditStatus() == 1) {
            throw new RuntimeException("已审核通过的资讯不能再修改，如需调整请联系管理员！");
        }

        String title = validateUniqueTitle(
                news.getTitle() == null ? exist.getTitle() : news.getTitle(),
                news.getCategoryId() == null ? exist.getCategoryId() : news.getCategoryId(),
                exist.getId());

        // 不用 newsMapper.updateById(news)：那样会把前端传来的 authorId、
        // publishStatus 等字段一起写进去，等于给了越权改状态的入口。
        // 这里只挑允许修改的内容字段，其余一律由后端决定。
        News update = new News();
        update.setId(news.getId());
        update.setTitle(news.getTitle() == null ? null : title);
        update.setSummary(news.getSummary());
        update.setContent(news.getContent());
        update.setCoverUrl(news.getCoverUrl());
        update.setCategoryId(news.getCategoryId());
        update.setLevel(news.getLevel());
        // 改完重新排队审核；驳回意见清空，否则页面还挂着上一次的驳回理由
        update.setAuditStatus(0);
        update.setAuditOpinion("");
        update.setUpdateTime(LocalDateTime.now());
        newsMapper.updateById(update);

        // tagNames 为 null 表示这次不改标签，传空数组才表示清空——和 NewsController 保持一致
        if (news.getTagNames() != null) {
            saveNewsTags(news.getId(), news.getTagNames());
        }
        redisTemplate.delete(HOT_CACHE_KEY);
        return R.success();
    }

    private String validateUniqueTitle(String title, Long categoryId, Long currentId) {
        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("资讯标题不能为空！");
        }
        // 查重条件是「标题 + 分类」，分类为空时下面那个 eq 会变成 category_id = null，
        // 永远查不到重复，等于查重失效，最后只会抛一个数据库非空约束的错。
        // 所以在查之前先拦住。
        if (categoryId == null) {
            throw new RuntimeException("请选择资讯分类！");
        }
        String normalizedTitle = title.trim();
        boolean duplicate = newsMapper.exists(new LambdaQueryWrapper<News>()
                .apply("CAST(TRIM(title) AS BINARY) = CAST({0} AS BINARY)", normalizedTitle)
                .eq(News::getCategoryId, categoryId)
                .ne(News::getPublishStatus, 2)
                .ne(currentId != null, News::getId, currentId));
        if (duplicate) {
            throw new BusinessException(
                    "已存在同名资讯《" + normalizedTitle + "》，请修改标题或先下线旧资讯！",
                    ResponseCode.ERROR.getCode());
        }
        return normalizedTitle;
    }

    @Operation(summary = "投稿详情", description = "只能看自己的稿子；不增加阅读量，教师自己预览不该计入统计")
    @PostMapping("/tch/news/detail")
    @SaCheckLogin
    public R<News> detail(@RequestParam Long id) {
        News news = requireMine(id);
        List<Long> tagIds = newsTagMapper.selectList(
                        new LambdaQueryWrapper<NewsTag>().eq(NewsTag::getNewsId, id))
                .stream().map(NewsTag::getTagId).toList();
        news.setTagNames(tagIds.isEmpty() ? List.of()
                : tagMapper.selectByIds(tagIds).stream().map(Tag::getName).toList());
        return R.data(news);
    }

    /**
     * 查一条必须属于当前教师的资讯
     * 越权时统一提示「只能操作自己投稿的资讯」，不暴露这条资讯是否存在
     */
    private News requireMine(Long id) {
        if (id == null) {
            throw new RuntimeException("资讯ID不能为空！");
        }
        News news = newsMapper.selectById(id);
        if (news == null || news.getAuthorType() == null || news.getAuthorType() != 2
                || !LoginUtil.currentId().equals(news.getAuthorId())) {
            throw new RuntimeException("只能操作自己投稿的资讯！");
        }
        return news;
    }

    /**
     * 保存标签关联（先删后插，标签名不存在时自动创建）
     * 这段逻辑和 NewsController.saveNewsTags 相同。没有抽成公共类，
     * 是因为那份代码已在管理端稳定运行、不想为一个方法去动它；
     * 以后两处都要改的时候再统一抽到 service 层。
     */
    private void saveNewsTags(Long newsId, List<String> tagNames) {
        newsTagMapper.delete(new LambdaQueryWrapper<NewsTag>().eq(NewsTag::getNewsId, newsId));
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }
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
