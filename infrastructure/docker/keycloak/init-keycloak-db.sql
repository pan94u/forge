-- Keycloak 专用数据库初始化脚本
-- PostgreSQL 首次启动时自动执行（PGDATA 为空时）
CREATE DATABASE keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO forge;
