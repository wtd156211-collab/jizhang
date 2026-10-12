INSERT IGNORE INTO acct_category (id, category_type, category_code, category_name, parent_id, sort, status, create_by, update_by)
VALUES
    (1, 'INCOME', 'SALES', '销售收入', 0, 1, 1, 'system', 'system'),
    (2, 'INCOME', 'SERVICE', '服务收入', 0, 2, 1, 'system', 'system'),
    (3, 'EXPENSE', 'OFFICE', '办公费用', 0, 1, 1, 'system', 'system'),
    (4, 'EXPENSE', 'TRAVEL', '差旅费用', 0, 2, 1, 'system', 'system'),
    (5, 'EXPENSE', 'MARKETING', '市场推广', 0, 3, 1, 'system', 'system');

INSERT IGNORE INTO acct_payment_method (id, method_code, method_name, sort, status, create_by, update_by)
VALUES
    (1, 'BANK', '银行转账', 1, 1, 'system', 'system'),
    (2, 'WECHAT', '微信支付', 2, 1, 'system', 'system'),
    (3, 'ALIPAY', '支付宝', 3, 1, 'system', 'system'),
    (4, 'CASH', '现金', 4, 1, 'system', 'system');

INSERT IGNORE INTO acct_fund_account (id, account_code, account_name, account_type, sort, status, create_by, update_by)
VALUES
    (1, 'MAIN_BANK', '公司基本账户', 'BANK', 1, 1, 'system', 'system'),
    (2, 'CASH', '公司现金账户', 'CASH', 2, 1, 'system', 'system');

INSERT IGNORE INTO acct_project (id, project_code, project_name, sort, status, create_by, update_by)
VALUES
    (1, 'GENERAL', '日常经营', 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (400, 0, '记账管理', 'dir', 'wallet', '/bookkeeping', '', '', 5, 1, 1, 'system', 'system'),
    (401, 400, '财务工作台', 'menu', 'dashboard', '/bookkeeping/dashboard', 'bookkeeping/dashboard/index', 'bookkeeping:dashboard:view', 1, 1, 1, 'system', 'system'),
    (402, 400, '账目列表', 'menu', 'file', '/bookkeeping/records', 'bookkeeping/records/index', 'bookkeeping:record:list', 2, 1, 1, 'system', 'system'),
    (4021, 402, '新增账目', 'btn', '', '', '', 'bookkeeping:record:add', 1, 1, 1, 'system', 'system'),
    (4022, 402, '编辑账目', 'btn', '', '', '', 'bookkeeping:record:edit', 2, 1, 1, 'system', 'system'),
    (4023, 402, '提交审核', 'btn', '', '', '', 'bookkeeping:record:submit', 3, 1, 1, 'system', 'system'),
    (4024, 402, '导出账目', 'btn', '', '', '', 'bookkeeping:record:export', 4, 1, 1, 'system', 'system'),
    (4025, 402, '查看票据', 'btn', '', '', '', 'bookkeeping:attachment:view', 5, 1, 1, 'system', 'system'),
    (4026, 402, '上传及设置主票据', 'btn', '', '', '', 'bookkeeping:attachment:upload', 6, 1, 1, 'system', 'system'),
    (4027, 402, '删除票据', 'btn', '', '', '', 'bookkeeping:attachment:remove', 7, 1, 1, 'system', 'system'),
    (403, 400, '审核中心', 'menu', 'check', '/bookkeeping/reviews', 'bookkeeping/reviews/index', 'bookkeeping:review:list', 3, 1, 1, 'system', 'system'),
    (4031, 403, '审核账目', 'btn', '', '', '', 'bookkeeping:record:review', 1, 1, 1, 'system', 'system'),
    (4032, 403, '退回账目', 'btn', '', '', '', 'bookkeeping:record:return', 2, 1, 1, 'system', 'system'),
    (4033, 403, '撤回重审', 'btn', '', '', '', 'bookkeeping:record:withdraw', 3, 1, 1, 'system', 'system'),
    (4034, 403, '作废账目', 'btn', '', '', '', 'bookkeeping:record:void', 4, 1, 1, 'system', 'system'),
    (404, 400, '报表与导出', 'menu', 'chart', '/bookkeeping/reports', 'bookkeeping/reports/index', 'bookkeeping:report:list', 4, 1, 1, 'system', 'system'),
    (405, 400, '记账配置', 'menu', 'gear', '/bookkeeping/config', 'bookkeeping/config/index', 'bookkeeping:config:list', 5, 1, 1, 'system', 'system'),
    (4051, 405, '编辑记账配置', 'btn', '', '', '', 'bookkeeping:config:edit', 1, 1, 1, 'system', 'system'),
    (406, 400, 'OCR 配置', 'menu', 'scan', '/bookkeeping/ocr-config', 'bookkeeping/ocr-config/index', 'bookkeeping:ocr:config', 6, 1, 1, 'system', 'system'),
    (4061, 406, '识别票据', 'btn', '', '', '', 'bookkeeping:ocr:recognize', 1, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
WHERE id IN (400, 401, 402, 403, 404, 405, 406,
             4021, 4022, 4023, 4024, 4025, 4026, 4027,
             4031, 4032, 4033, 4034, 4051, 4061);
