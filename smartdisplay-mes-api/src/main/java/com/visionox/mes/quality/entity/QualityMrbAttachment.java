package com.visionox.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MRB证据附件元数据。
 */
@Data
@TableName("quality_mrb_attachment")
public class QualityMrbAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String attachmentNo;
    private String mrbNo;
    private String eventNo;
    private String lotNo;
    private String fileName;
    private String fileUrl;
    private String fileHash;
    private String fileType;
    private String uploadedBy;
    private LocalDateTime uploadedTime;
    private LocalDateTime createdTime;
}
