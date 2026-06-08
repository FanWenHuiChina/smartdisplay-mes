package com.visionox.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MRB复判/关闭履历。
 */
@Data
@TableName("quality_mrb_record")
public class QualityMrbRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mrbNo;
    private String eventNo;
    private String lotNo;
    private String reviewType;
    private String dispositionAction;
    private String opinion;
    private String meetingNo;
    private String participants;
    private String riskLevel;
    private String approvalStatus;
    private String reviewer;
    private LocalDateTime reviewTime;
    private Integer attachmentCount;
    private String requestSnapshot;
    private LocalDateTime createdTime;
}
