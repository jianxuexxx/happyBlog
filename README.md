# HappyBlog - 我的博客系统

参考 [POETIZE - 最美博客](https://poetize.cn/) 的视觉风格，搭建一套个人博客系统。

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus |
| 后端 | Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 对象存储 | RustFS（S3 兼容） |
| 部署 | Docker Compose |

## 目录结构

```
myblog/
├── backend/          # Spring Boot 后端服务
├── frontend/         # Vue3 前端（前台 + /admin 管理端）
├── docker/           # docker-compose 编排及中间件配置
│   ├── mysql/
│   ├── redis/
│   └── rustfs/
├── docs/
│   └── superpowers/specs/   # 设计文档
└── .tmp/             # 参考素材（poetize.cn 还原用，不入库）
```

> 状态：前端骨架（主题/路由/布局/首页 v2）与后端骨架（Spring Boot 3 + 公共基建 + JWT 鉴权 + category 竖切链路）已完成（编译与单测已验证；端到端链路待按 backend/smoke/category-smoke.sh 手工验证）。
> 下一步：其余业务表的 CRUD、前台接口与缓存策略、管理端页面、RustFS 上传、Docker Compose 编排。