/**
 * Jest 配置
 * 继承 Next.js 默认配置，排除 e2e 目录（e2e 测试使用 Playwright）
 */
const nextJest = require('next/jest')

const createJestConfig = nextJest({
  // 提供 Next.js 应用路径以加载 next.config.js 和 .env 文件
  dir: './',
})

// 自定义 Jest 配置
const customJestConfig = {
  // 忽略 e2e 目录（使用 Playwright 进行 E2E 测试）
  testPathIgnorePatterns: ['/node_modules/', '/e2e/'],

  // 模块路径别名（与 tsconfig.json 中的 paths 对应）
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },

  // 测试环境
  testEnvironment: 'jsdom',

  // 转换忽略模式
  transformIgnorePatterns: [
    '/node_modules/(?!(@playwright|playwright-core)/)',
  ],
}

module.exports = createJestConfig(customJestConfig)