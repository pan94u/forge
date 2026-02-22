-- 为 user_model_configs 添加 custom_models 列
-- 存储用户自定义添加的模型 ID 列表（JSON 数组格式）
ALTER TABLE user_model_configs ADD COLUMN IF NOT EXISTS custom_models TEXT DEFAULT '[]';
