package com.schoolhelp.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 作息配置保存请求（全量覆盖）
 */
@Data
public class TimeConfigDTO {

    @NotEmpty(message = "作息配置不能为空")
    @Size(max = 12, message = "每天最多 12 节课")
    @Valid
    private List<SectionTime> sections;

    @Data
    public static class SectionTime {

        @NotNull
        @Min(1)
        @Max(12)
        private Integer section;

        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开始时间格式应为 HH:mm")
        private String startTime;

        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "结束时间格式应为 HH:mm")
        private String endTime;
    }
}
