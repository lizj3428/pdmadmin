package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.response.ResponseCode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class NewsPinService {
    @Resource
    private NewsMapper newsMapper;

    public void setTopFlag(Long newsId, Integer topFlag) {
        if (topFlag == null || (topFlag != 0 && topFlag != 1)) {
            throw new BusinessException("置顶状态只能为 0 或 1！", ResponseCode.ERROR.getCode());
        }

        int changed = newsMapper.update(null, new LambdaUpdateWrapper<News>()
                .eq(News::getId, newsId)
                .eq(News::getAuditStatus, 1)
                .eq(News::getPublishStatus, 1)
                .set(News::getTopFlag, topFlag));
        if (changed == 0 && !newsMapper.exists(new LambdaQueryWrapper<News>()
                .eq(News::getId, newsId)
                .eq(News::getAuditStatus, 1)
                .eq(News::getPublishStatus, 1)
                .eq(News::getTopFlag, topFlag))) {
            throw new BusinessException("只有已通过审核且已发布的资讯可以设置置顶！",
                    ResponseCode.ERROR.getCode());
        }
    }
}
