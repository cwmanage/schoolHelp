package com.schoolhelp.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 课表条目（新增/编辑）
 */
@Data
public class ScheduleDTO {

    private Long courseId;

    @NotBlank(message = "课程名不能为空")
    private String courseName;

    @NotNull(message = "请选择星期")
    @Min(value = 1, message = "星期范围1-7")
    @Max(value = 7, message = "星期范围1-7")
    private Integer weekDay;

    @NotNull(message = "请选择开始节次")
    @Min(value = 1, message = "节次范围1-12")
    @Max(value = 12, message = "节次范围1-12")
    private Integer startSection;

    @NotNull(message = "请选择结束节次")
    @Min(value = 1, message = "节次范围1-12")
    @Max(value = 12, message = "节次范围1-12")
    private Integer endSection;

    /** 0每周 1单周 2双周 */
    private Integer weekType = 0;

    /** 具体周次 1-16 */
    private String weeks;

    private String room;

    private String teacher;

    @NotBlank(message = "请选择学期")
    private String semester;
}
