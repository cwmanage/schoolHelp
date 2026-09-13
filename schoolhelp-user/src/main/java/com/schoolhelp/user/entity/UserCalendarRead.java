package com.schoolhelp.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户日历已读记录（登录后"有更新"提示判断）
 */
@Data
@TableName("user_calendar_read")
public class UserCalendarRead {

    @TableId
    private Long userId;

    private LocalDateTime lastReadAt;
}
