package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BOM变更/ECO附件元数据。
 */
@Data
@TableName("md_bom_change_attachment")
public class BomChangeAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String attachmentNo;
    private String changeNo;
    private String productCode;
    private String targetBomCode;
    private String fileName;
    private String fileUrl;
    private String fileHash;
    private String fileType;
    private String attachmentRole;
    private String uploadedBy;
    private LocalDateTime uploadedTime;
    private LocalDateTime createdTime;
}
