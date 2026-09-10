package com.schoolhelp.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.schoolhelp.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
