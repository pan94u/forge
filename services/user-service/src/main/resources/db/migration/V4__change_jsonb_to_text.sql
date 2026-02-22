-- 更改 JSONB 列为 TEXT 类型以解决 Hibernate 兼容性问题
ALTER TABLE users ALTER COLUMN settings TYPE TEXT;
ALTER TABLE users ALTER COLUMN settings SET DEFAULT '{}';
ALTER TABLE organizations ALTER COLUMN settings TYPE TEXT;
ALTER TABLE organizations ALTER COLUMN settings SET DEFAULT '{}';
ALTER TABLE organizations ALTER COLUMN metadata TYPE TEXT;
ALTER TABLE organizations ALTER COLUMN metadata SET DEFAULT '{}';
ALTER TABLE user_identities ALTER COLUMN metadata TYPE TEXT;
ALTER TABLE user_identities ALTER COLUMN metadata SET DEFAULT '{}';