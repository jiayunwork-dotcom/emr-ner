# 电子病历结构化抽取系统 (EMR-NER)

一个基于深度学习的中文电子病历结构化抽取系统，支持医学实体识别、关系抽取、否定与不确定性检测、时间信息归一化等功能，并提供Web标注验证界面供医学专家校正结果。

## 功能特性

### 核心能力
- **临床命名实体识别**: 识别七大类医学实体（疾病、症状、药物、检查、手术、解剖、时间）
- **实体关系抽取**: 自动抽取实体间的语义关系（症状-疾病、药物-适应症等）
- **否定与不确定性检测**: 识别"无发热"、"疑似肺炎"等否定和不确定表达
- **时间信息抽取**: 将相对时间表达转换为标准化绝对日期
- **结构化输出**: 遵循预定义JSON Schema的完整抽取结果

### 系统功能
- **多文档类型支持**: 入院记录、病程记录、出院小结等
- **单条/批量处理**: 支持单条文本推理和批量文件处理（JSON/ZIP）
- **模型管理**: 多版本模型热切换、性能评估、模型微调
- **Web标注面板**: 实体高亮、关系编辑、人工校正、训练数据导出
- **任务调度**: 异步批量任务、进度追踪、Worker并发处理

## 技术架构

### 后端服务 (Java + Spring Boot)
- API层: RESTful API接口
- 任务调度: 异步批量任务处理
- 数据持久化: PostgreSQL
- 与推理服务通信: gRPC客户端

### 推理服务 (Python + gRPC)
- 实体抽取: 基于关键词和规则的NER（可扩展为BERT-CRF/BiLSTM-CRF）
- 关系抽取: 基于距离和类型的关系抽取
- 时间归一化: 相对时间转绝对日期
- 否定检测: 基于上下文窗口的否定词识别

### 前端界面 (Vue 3 + Element Plus)
- 文档管理: 病历文档的增删改查
- 标注面板: 交互式实体/关系标注
- 批量任务: 批量处理任务管理
- 模型管理: 模型版本管理和评估
- 推理演示: 实时抽取效果演示

## 项目结构

```
emr-ner/
├── docker-compose.yml          # Docker Compose配置
├── database/
│   └── init/                   # 数据库初始化脚本
│       ├── 01_schema.sql       # 表结构定义
│       └── 02_seed_data.sql    # 初始数据
├── backend/                    # Java Spring Boot后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/emr/ner/
│       │   ├── controller/     # REST API控制器
│       │   ├── service/        # 业务逻辑层
│       │   ├── repository/     # 数据访问层
│       │   ├── entity/         # JPA实体类
│       │   ├── dto/            # 数据传输对象
│       │   └── config/         # 配置类
│       └── resources/
│           └── application.yml # 应用配置
├── inference/                  # Python推理gRPC服务
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── protos/
│   │   └── inference.proto     # gRPC接口定义
│   └── src/
│       ├── server.py           # gRPC服务端
│       ├── ner_extractor.py    # 实体抽取器
│       ├── relation_extractor.py  # 关系抽取器
│       └── timeline_extractor.py  # 时间抽取器
└── frontend/                   # Vue 3前端
    ├── Dockerfile
    ├── nginx.conf
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── views/              # 页面组件
        ├── router/             # 路由配置
        ├── api/                # API封装
        └── App.vue
```

## 快速开始

### 环境要求
- Docker 20.10+
- Docker Compose 2.0+

### 启动服务

```bash
# 克隆项目
cd emr-ner

# 构建并启动所有服务
docker-compose up -d --build

# 查看服务状态
docker-compose ps
```

### 访问地址
- 前端界面: http://localhost
- 后端API: http://localhost:8080/api
- 推理服务gRPC: localhost:50051
- PostgreSQL: localhost:5432

### 默认账户
- 用户名: admin
- 密码: admin123

## API文档

### 文档管理
- `GET /api/documents` - 获取文档列表
- `POST /api/documents` - 创建文档
- `GET /api/documents/{id}` - 获取文档详情
- `POST /api/documents/{id}/process` - 处理文档
- `GET /api/documents/{id}/result` - 获取抽取结果

### 实体操作
- `POST /api/documents/{id}/entities` - 添加实体
- `PUT /api/documents/{id}/entities/{entityId}` - 更新实体
- `DELETE /api/documents/{id}/entities/{entityId}` - 删除实体

### 关系操作
- `POST /api/documents/{id}/relations` - 添加关系
- `PUT /api/documents/{id}/relations/{relationId}` - 更新关系
- `DELETE /api/documents/{id}/relations/{relationId}` - 删除关系

### 批量处理
- `POST /api/batch-tasks/upload` - 上传文件批量处理
- `POST /api/batch-tasks/json` - JSON数组批量处理
- `GET /api/batch-tasks` - 获取任务列表
- `GET /api/batch-tasks/{id}` - 获取任务详情

### 模型管理
- `GET /api/models` - 获取模型列表
- `POST /api/models/{id}/activate` - 激活模型
- `POST /api/models/{id}/evaluate` - 评估模型

## 实体类型体系

| 类型编码 | 类型名称 | 示例 |
|---------|---------|------|
| disease | 疾病 | 糖尿病、高血压、肺癌 |
| symptom | 症状 | 头痛、发热、乏力 |
| drug | 药物 | 阿莫西林、二甲双胍 |
| test | 检查 | 血糖、白细胞计数、CT |
| operation | 手术 | 胆囊切除术、冠脉搭桥 |
| anatomy | 解剖 | 肝脏、左下肢、第四腰椎 |
| time | 时间 | 入院第3天、术后两周 |

## 关系类型体系

| 关系类型 | 说明 | 示例 |
|---------|------|------|
| symptom_of_disease | 症状-疾病 | 头痛是偏头痛的症状 |
| drug_for_disease | 药物-适应症 | 二甲双胍用于治疗糖尿病 |
| test_for_disease | 检查-疾病 | CT用于检查肺癌 |
| operation_for_disease | 手术-疾病 | 胆囊切除术治疗胆囊炎 |
| location_of_disease | 部位-疾病 | 股骨头坏死位于左侧股骨头 |

## JSON输出格式

```json
{
  "originalText": "原始病历文本",
  "modelVersion": "bert-crf-v1.0",
  "entities": [
    {
      "id": 1,
      "entityText": "糖尿病",
      "entityType": "disease",
      "startPos": 10,
      "endPos": 13,
      "isNegated": false,
      "isUncertain": false,
      "confidence": 0.95,
      "source": "model"
    }
  ],
  "relations": [
    {
      "id": 1,
      "headEntityId": 2,
      "tailEntityId": 1,
      "headEntityText": "二甲双胍",
      "tailEntityText": "糖尿病",
      "relationType": "drug_for_disease",
      "confidence": 0.88,
      "source": "model"
    }
  ],
  "timelines": [
    {
      "id": 1,
      "timeExpression": "入院第3天",
      "normalizedDate": "2024-01-03",
      "associatedEvent": null,
      "entityId": 3,
      "confidence": 0.92
    }
  ]
}
```

## 开发指南

### 后端开发
```bash
cd backend
mvn spring-boot:run
```

### 推理服务开发
```bash
cd inference
pip install -r requirements.txt
python -m grpc_tools.protoc -I./protos --python_out=./src --grpc_python_out=./src ./protos/inference.proto
python src/server.py
```

### 前端开发
```bash
cd frontend
npm install
npm run dev
```

## 扩展说明

### 替换为真实深度学习模型
1. 在 `inference/src/` 下添加模型加载和推理代码
2. 修改相应的extractor类使用真实模型
3. 更新 `inference/requirements.txt` 添加深度学习框架依赖（如torch、transformers）

### 模型训练与微调
- 标注数据导出为CoNLL格式或JSON格式
- 使用HuggingFace Transformers训练BERT-CRF模型
- 训练完成后通过模型管理界面上传新版本

## 许可证

MIT License
