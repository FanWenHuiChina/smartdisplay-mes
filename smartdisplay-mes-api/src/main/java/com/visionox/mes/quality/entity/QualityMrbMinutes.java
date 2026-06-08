package com.visionox.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MRB会议纪要正文版本。
 */
@Data
@TableName("quality_mrb_minutes")
public class QualityMrbMinutes implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String minutesNo;
    private String mrbNo;
    private String eventNo;
    private String lotNo;
    private Integer versionNo;
    private String minutesContent;
    private String summary;
    private String actionItems;
    private String riskNote;
    private String editor;
    private LocalDateTime editTime;
    private String changeReason;
    private String sourceAction;
    private String requestSnapshot;
    private LocalDateTime createdTime;
}
