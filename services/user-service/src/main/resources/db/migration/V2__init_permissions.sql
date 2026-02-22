-- 插入基础权限
INSERT INTO permissions (resource, action, description) VALUES
    -- Workspace 权限
    ('workspace', 'read', '读取工作空间'),
    ('workspace', 'write', '创建/修改文件'),
    ('workspace', 'delete', '删除工作空间'),
    ('workspace', 'manage', '管理工作空间设置'),

    -- Chat 权限
    ('chat', 'read', '查看对话'),
    ('chat', 'write', '发送消息'),
    ('chat', 'delete', '删除对话'),

    -- Workflow 权限
    ('workflow', 'read', '查看工作流'),
    ('workflow', 'create', '创建工作流'),
    ('workflow', 'edit', '编辑工作流'),
    ('workflow', 'delete', '删除工作流'),
    ('workflow', 'run', '运行工作流'),

    -- MCP 权限
    ('mcp', 'call', '调用 MCP 工具'),

    -- Admin 权限
    ('admin', 'access', '访问管理后台'),
    ('admin', 'users', '管理用户'),
    ('admin', 'roles', '管理角色'),
    ('admin', 'settings', '管理系统设置'),

    -- Organization 权限
    ('org', 'read', '查看组织信息'),
    ('org', 'manage', '管理组织设置'),
    ('org', 'members', '管理组织成员')
ON CONFLICT (resource, action) DO NOTHING;