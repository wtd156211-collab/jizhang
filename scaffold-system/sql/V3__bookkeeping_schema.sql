CREATE TABLE IF NOT EXISTS acct_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类 ID',
    category_type VARCHAR(16) NOT NULL COMMENT '分类类型：INCOME 收入，EXPENSE 支出',
    category_code VARCHAR(64) NOT NULL COMMENT '分类编码',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '上级分类 ID',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acct_category_code (category_code),
    KEY idx_acct_category_type_status (category_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账分类表';

CREATE TABLE IF NOT EXISTS acct_payment_method (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '支付方式 ID',
    method_code VARCHAR(64) NOT NULL COMMENT '支付方式编码',
    method_name VARCHAR(100) NOT NULL COMMENT '支付方式名称',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acct_payment_method_code (method_code),
    KEY idx_acct_payment_method_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账支付方式表';

CREATE TABLE IF NOT EXISTS acct_fund_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '资金账户 ID',
    account_code VARCHAR(64) NOT NULL COMMENT '资金账户编码',
    account_name VARCHAR(100) NOT NULL COMMENT '资金账户名称',
    account_type VARCHAR(32) NOT NULL COMMENT '账户类型',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acct_fund_account_code (account_code),
    KEY idx_acct_fund_account_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账资金账户表';

CREATE TABLE IF NOT EXISTS acct_project (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目 ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    project_name VARCHAR(100) NOT NULL COMMENT '项目名称',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acct_project_code (project_code),
    KEY idx_acct_project_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账项目表';

CREATE TABLE IF NOT EXISTS acct_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '账目 ID',
    record_no VARCHAR(64) NOT NULL COMMENT '账目编号',
    record_type VARCHAR(16) NOT NULL COMMENT '账目类型：INCOME 收入，EXPENSE 支出',
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿，PENDING_REVIEW 待审核，RETURNED 已退回，POSTED 已入账，WITHDRAWN 撤回重审，VOIDED 已作废',
    summary VARCHAR(500) NOT NULL COMMENT '账目摘要',
    occurred_date DATE NOT NULL COMMENT '实际发生日期',
    occurred_time TIME DEFAULT NULL COMMENT '实际发生时间，可为空',
    total_amount DECIMAL(18,2) NOT NULL COMMENT '账目总金额',
    payment_method_id BIGINT DEFAULT NULL COMMENT '支付方式 ID',
    fund_account_id BIGINT DEFAULT NULL COMMENT '资金账户 ID',
    created_by BIGINT NOT NULL COMMENT '创建人 ID',
    dept_id BIGINT DEFAULT NULL COMMENT '所属部门 ID',
    version INT NOT NULL DEFAULT 0 COMMENT '并发编辑版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常；保留明细和历史',
    remark VARCHAR(1000) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人账号',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人账号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acct_record_no (record_no),
    KEY idx_acct_record_timeline (occurred_date, occurred_time, id),
    KEY idx_acct_record_status (status, record_type),
    KEY idx_acct_record_payment_method (payment_method_id),
    KEY idx_acct_record_fund_account (fund_account_id),
    KEY idx_acct_record_creator (created_by, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账主表';

CREATE TABLE IF NOT EXISTS acct_record_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '账目明细 ID',
    record_id BIGINT NOT NULL COMMENT '账目 ID',
    category_id BIGINT NOT NULL COMMENT '分类 ID',
    amount DECIMAL(18,2) NOT NULL COMMENT '明细金额',
    handler_id BIGINT DEFAULT NULL COMMENT '经办人 ID',
    dept_id BIGINT DEFAULT NULL COMMENT '明细部门 ID',
    project_id BIGINT DEFAULT NULL COMMENT '项目 ID',
    remark VARCHAR(500) DEFAULT NULL COMMENT '明细备注',
    sort INT NOT NULL DEFAULT 0 COMMENT '明细顺序',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人账号',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人账号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_acct_record_detail_record (record_id, sort),
    KEY idx_acct_record_detail_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账明细表';

CREATE TABLE IF NOT EXISTS acct_record_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '附件 ID',
    record_id BIGINT NOT NULL COMMENT '账目 ID',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_key VARCHAR(500) NOT NULL COMMENT '存储键，不是公开 URL',
    mime_type VARCHAR(128) NOT NULL COMMENT '文件类型',
    file_size BIGINT NOT NULL COMMENT '文件大小，字节',
    sha256 CHAR(64) DEFAULT NULL COMMENT '文件 SHA-256 校验值',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主票据：1 是，0 否',
    source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手工，OCR 识别原图',
    uploaded_by BIGINT NOT NULL COMMENT '上传人 ID',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人账号',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人账号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_acct_attachment_record (record_id, deleted),
    KEY idx_acct_attachment_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账附件表';

CREATE TABLE IF NOT EXISTS acct_record_review (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核历史 ID',
    record_id BIGINT NOT NULL COMMENT '账目 ID',
    action VARCHAR(24) NOT NULL COMMENT '动作：SUBMIT、APPROVE、RETURN、WITHDRAW、VOID',
    from_status VARCHAR(24) DEFAULT NULL COMMENT '原状态',
    to_status VARCHAR(24) NOT NULL COMMENT '目标状态',
    reason VARCHAR(1000) DEFAULT NULL COMMENT '原因',
    operator_id BIGINT NOT NULL COMMENT '操作人 ID',
    review_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_acct_review_record_time (record_id, review_time, id),
    KEY idx_acct_review_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账审核历史表';

CREATE TABLE IF NOT EXISTS acct_record_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '账目版本 ID',
    record_id BIGINT NOT NULL COMMENT '账目 ID',
    version_no INT NOT NULL COMMENT '版本号',
    action VARCHAR(24) NOT NULL COMMENT '触发动作',
    snapshot_json LONGTEXT NOT NULL COMMENT '账目和明细快照 JSON',
    reason VARCHAR(1000) DEFAULT NULL COMMENT '变更原因',
    operator_id BIGINT NOT NULL COMMENT '操作人 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acct_record_version (record_id, version_no),
    KEY idx_acct_record_version_record (record_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账版本快照表';

CREATE TABLE IF NOT EXISTS acct_ocr_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OCR 任务 ID',
    record_id BIGINT NOT NULL COMMENT '账目 ID',
    attachment_id BIGINT NOT NULL COMMENT '原始票据附件 ID',
    provider VARCHAR(64) NOT NULL COMMENT 'OCR 服务标识',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、SUCCEEDED、FAILED、CONFIRMED',
    raw_result LONGTEXT DEFAULT NULL COMMENT 'OCR 原始响应',
    normalized_result LONGTEXT DEFAULT NULL COMMENT '可编辑的标准化结果 JSON',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败信息',
    requested_by BIGINT NOT NULL COMMENT '发起人 ID',
    started_at DATETIME DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME DEFAULT NULL COMMENT '完成时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_acct_ocr_record (record_id, create_time),
    KEY idx_acct_ocr_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账 OCR 任务表';
