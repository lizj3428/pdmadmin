package com.example.pdmadmin.service;

import com.example.pdmadmin.entity.Message;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.PushRecord;
import com.example.pdmadmin.entity.PushTask;
import com.example.pdmadmin.entity.Student;
import com.example.pdmadmin.mapper.MessageMapper;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.PushRecordMapper;
import com.example.pdmadmin.mapper.PushTaskMapper;
import com.example.pdmadmin.mapper.StudentMapper;
import com.example.pdmadmin.websocket.MessagePushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushServiceTests {
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private NewsMapper newsMapper;
    @Mock
    private PushRecordMapper pushRecordMapper;
    @Mock
    private PushTaskMapper pushTaskMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private MessagePushService messagePushService;
    @InjectMocks
    private PushService pushService;

    @Test
    void existingMessageDoesNotPreventRecordingOrNotifying() {
        PushTask task = new PushTask();
        task.setId(1L);
        task.setNewsId(2L);
        task.setTargetType(1);
        task.setChannel(1);
        News news = new News();
        news.setId(task.getNewsId());
        news.setAuditStatus(1);
        news.setPublishStatus(1);
        when(pushTaskMapper.claim(anyLong())).thenReturn(1);
        news.setTitle("校园通知");
        Student student = new Student();
        student.setId(3L);
        when(newsMapper.selectById(task.getNewsId())).thenReturn(news);
        when(studentMapper.selectList(any())).thenReturn(List.of(student));
        when(messageMapper.exists(any())).thenReturn(true);

        pushService.execute(task);

        verify(messageMapper, never()).insert(any(Message.class));
        verify(pushRecordMapper).insert(any(PushRecord.class));
        verify(messagePushService).sendToUser(eq("student:3"), anyString());
    }

    @Test
    void unpublishedNewsIsRejected() {
        PushTask task = new PushTask();
        task.setId(1L);
        task.setNewsId(2L);
        News news = new News();
        news.setAuditStatus(1);
        news.setPublishStatus(0);
        when(pushTaskMapper.claim(anyLong())).thenReturn(1);
        when(newsMapper.selectById(task.getNewsId())).thenReturn(news);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> pushService.execute(task));

        assertEquals("资讯未通过审核或已下线，不能推送", exception.getMessage());
        assertEquals(3, task.getStatus());
        verify(pushTaskMapper).updateById(task);
        verify(pushRecordMapper, never()).insert(any(PushRecord.class));
    }

    @Test
    void completedTaskCannotRunAgain() {
        PushTask task = new PushTask();
        task.setId(1L);
        task.setStatus(2);
        when(pushTaskMapper.claim(anyLong())).thenReturn(0);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> pushService.execute(task));

        assertEquals("推送任务已完成或正在执行，不能重复推送！", exception.getMessage());
        verify(pushRecordMapper, never()).insert(any(PushRecord.class));
    }
}
