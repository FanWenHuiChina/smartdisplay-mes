package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("material_coa_attachment")
public class MaterialCoaAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String attachmentNo;
    private String inspectionNo;
    private String batchNo;
    private String fileName;
    private String fileUrl;
    private String fileHash;
    private String fileType;
    private String uploadedBy;
    private LocalDateTime uploadedTime;
    private LocalDateTime createdTime;
}
