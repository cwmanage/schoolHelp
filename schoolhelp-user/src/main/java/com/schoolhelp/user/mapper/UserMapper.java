package com.schoolhelp.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.schoolhelp.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
