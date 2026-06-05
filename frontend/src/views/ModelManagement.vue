<template>
  <div class="model-management">
    <el-card class="header-card">
      <div class="header-content">
        <h2>模型管理</h2>
        <el-button v-if="activeTab === 'models'" type="primary" @click="showUploadDialog = true">
          <el-icon><Plus /></el-icon>
          上传模型
        </el-button>
        <el-button v-if="activeTab === 'consistency'" type="primary" @click="runConsistencyCheck">
          <el-icon><Refresh /></el-icon>
          开始检验
        </el-button>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="main-tabs">
      <el-tab-pane label="模型列表" name="models">
        <el-row :gutter="16">
          <el-col :span="8" v-for="model in models" :key="model.id">
            <el-card shadow="hover" class="model-card">
              <template #header>
                <div class="card-header">
                  <span class="model-name">{{ model.versionName }}</span>
                  <el-tag v-if="model.isActive" type="success">当前激活</el-tag>
                </div>
              </template>
              
              <div class="model-info">
                <el-descriptions :column="1" size="small" border>
                  <el-descriptions-item label="模型类型">
                    {{ model.modelType }}
                  </el-descriptions-item>
                  <el-descriptions-item label="描述">
                    {{ model.description || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="创建时间">
                    {{ model.createdAt }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>

              <div v-if="model.metrics" class="model-metrics">
                <h4>性能指标</h4>
                <div class="metrics-grid">
                  <div class="metric-item">
                    <div class="metric-label">Precision</div>
                    <div class="metric-value">{{ (model.metrics.overall?.precision * 100).toFixed(1) }}%</div>
                  </div>
                  <div class="metric-item">
                    <div class="metric-label">Recall</div>
                    <div class="metric-value">{{ (model.metrics.overall?.recall * 100).toFixed(1) }}%</div>
                  </div>
                  <div class="metric-item">
                    <div class="metric-label">F1 Score</div>
                    <div class="metric-value">{{ (model.metrics.overall?.f1 * 100).toFixed(1) }}%</div>
                  </div>
                </div>
              </div>

              <div class="model-actions">
                <el-button 
                  size="small" 
                  type="success" 
                  :disabled="model.isActive"
                  @click="activateModel(model.id)"
                >
                  激活
                </el-button>
                <el-button size="small" @click="evaluateModel(model.id)">
                  评估
                </el-button>
                <el-button size="small" type="danger" @click="deleteModel(model.id)">
                  删除
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="一致性检验" name="consistency">
        <el-card>
          <template #header>
            <div class="consistency-header">
              <span>标注一致性检验</span>
              <span v-if="conflicts.length > 0" class="conflict-count">
                发现 {{ conflicts.length }} 个冲突
              </span>
            </div>
          </template>

          <div v-if="checking" class="loading-container">
            <el-icon class="is-loading" :size="48"><Loading /></el-icon>
            <p>正在扫描所有标注文档，请稍候...</p>
          </div>

          <div v-else-if="conflicts.length === 0" class="empty-container">
            <el-empty description="暂无冲突数据，点击右上角开始检验">
              <el-button type="primary" @click="runConsistencyCheck">开始检验</el-button>
            </el-empty>
          </div>

          <div v-else class="conflict-list">
            <div 
              v-for="(conflict, index) in conflicts" 
              :key="index" 
              class="conflict-item"
            >
              <div class="conflict-header">
                <span class="conflict-text">"{{ conflict.entityText }}"</span>
                <span class="conflict-type-count">
                  存在 {{ new Set(conflict.annotations.map(a => a.entityType)).size }} 种不同标注类型
                </span>
              </div>
              
              <div class="conflict-annotations">
                <div 
                  v-for="(ann, annIndex) in conflict.annotations" 
                  :key="annIndex"
                  class="annotation-item"
                >
                  <el-tag :style="getEntityTagStyle(ann.entityType)" size="small">
                    {{ getEntityTypeName(ann.entityType) }}
                  </el-tag>
                  <span class="annotation-doc">文档: {{ ann.documentTitle || `#${ann.documentId}` }}</span>
                  <span class="annotation-pos">位置: [{{ ann.startPos }}, {{ ann.endPos }})</span>
                  <el-tag v-if="ann.source === 'human'" size="small" type="success">人工</el-tag>
                  <el-tag v-else size="small" type="info">模型</el-tag>
                </div>
              </div>

              <div class="conflict-actions">
                <span class="resolve-label">统一修改为:</span>
                <el-select 
                  v-model="resolveType[index]" 
                  placeholder="选择类型" 
                  size="small"
                  style="width: 140px"
                >
                  <el-option 
                    v-for="config in entityTypeConfig" 
                    :key="config.type" 
                    :label="config.label" 
                    :value="config.type" 
                  />
                </el-select>
                <el-button 
                  size="small" 
                  type="primary" 
                  :disabled="!resolveType[index]"
                  @click="resolveConflict(conflict.entityText, resolveType[index])"
                >
                  应用
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showUploadDialog" title="上传模型" width="500px">
      <el-form :model="newModel" label-width="100px">
        <el-form-item label="版本名称" required>
          <el-input v-model="newModel.versionName" placeholder="如: bert-crf-v1.1" />
        </el-form-item>
        <el-form-item label="模型类型" required>
          <el-select v-model="newModel.modelType" style="width: 100%">
            <el-option label="BERT-CRF" value="BERT-CRF" />
            <el-option label="BiLSTM-CRF" value="BiLSTM-CRF" />
            <el-option label="联合抽取" value="JointExtraction" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newModel.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="模型文件">
          <el-upload
            :show-file-list="false"
            :before-upload="(file) => { newModel.file = file; return false }"
          >
            <el-button>选择文件</el-button>
            <span v-if="newModel.file" style="margin-left: 8px">{{ newModel.file.name }}</span>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="uploadModel">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Loading } from '@element-plus/icons-vue'
import { modelApi, documentApi } from '@/api'

const activeTab = ref('models')
const models = ref([])
const showUploadDialog = ref(false)
const newModel = ref({
  versionName: '',
  modelType: 'BERT-CRF',
  description: '',
  file: null
})

const checking = ref(false)
const conflicts = ref([])
const resolveType = reactive({})

const entityTypeConfig = [
  { type: 'disease', label: '疾病', color: '#f56c6c' },
  { type: 'symptom', label: '症状', color: '#e6a23c' },
  { type: 'drug', label: '药物', color: '#67c23a' },
  { type: 'test', label: '检查', color: '#409eff' },
  { type: 'operation', label: '手术', color: '#909399' },
  { type: 'anatomy', label: '解剖', color: '#8e44ad' },
  { type: 'time', label: '时间', color: '#16a085' }
]

const getEntityTypeName = (type) => {
  const config = entityTypeConfig.find(c => c.type === type)
  return config ? config.label : type
}

const getEntityTagStyle = (type) => {
  const config = entityTypeConfig.find(c => c.type === type)
  const color = config ? config.color : '#409eff'
  return {
    backgroundColor: color + '20',
    color: color,
    border: `1px solid ${color}40`
  }
}

const loadModels = async () => {
  try {
    models.value = await modelApi.getModels()
  } catch (error) {
    ElMessage.error('加载模型列表失败')
  }
}

const activateModel = async (id) => {
  try {
    await modelApi.activateModel(id)
    ElMessage.success('模型已激活')
    loadModels()
  } catch (error) {
    ElMessage.error('激活失败')
  }
}

const evaluateModel = async (id) => {
  ElMessageBox.prompt('请输入评估数据集路径', '模型评估', {
    confirmButtonText: '开始评估',
    cancelButtonText: '取消',
    inputPlaceholder: '数据集文件路径'
  }).then(async ({ value }) => {
    try {
      ElMessage.success('评估任务已提交')
      loadModels()
    } catch (error) {
      ElMessage.error('评估失败')
    }
  }).catch(() => {})
}

const deleteModel = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该模型吗？', '提示', {
      type: 'warning'
    })
    await modelApi.deleteModel(id)
    ElMessage.success('删除成功')
    loadModels()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const uploadModel = async () => {
  if (!newModel.value.versionName || !newModel.value.modelType) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    const formData = new FormData()
    formData.append('versionName', newModel.value.versionName)
    formData.append('modelType', newModel.value.modelType)
    formData.append('description', newModel.value.description)
    if (newModel.value.file) {
      formData.append('file', newModel.value.file)
    }
    
    await modelApi.uploadModel(formData)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    newModel.value = { versionName: '', modelType: 'BERT-CRF', description: '', file: null }
    loadModels()
  } catch (error) {
    ElMessage.error('上传失败')
  }
}

const runConsistencyCheck = async () => {
  checking.value = true
  try {
    conflicts.value = await documentApi.checkConsistency()
    ElMessage.success(`检验完成，发现 ${conflicts.value.length} 个冲突`)
  } catch (error) {
    ElMessage.error('一致性检验失败')
  } finally {
    checking.value = false
  }
}

const resolveConflict = async (entityText, targetType) => {
  try {
    await ElMessageBox.confirm(
      `确定将所有"${entityText}"实体统一修改为"${getEntityTypeName(targetType)}"吗？`,
      '确认修改',
      { type: 'warning' }
    )
    await documentApi.resolveConsistency(entityText, targetType)
    ElMessage.success('修改成功')
    runConsistencyCheck()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('修改失败')
    }
  }
}

onMounted(loadModels)
</script>

<style scoped lang="scss">
.model-management {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .header-card {
    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;

      h2 {
        margin: 0;
        font-size: 20px;
      }
    }
  }

  .main-tabs {
    :deep(.el-tabs__header) {
      margin: 0;
    }
  }

  .model-card {
    height: 100%;
    display: flex;
    flex-direction: column;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .model-name {
        font-weight: 600;
        font-size: 16px;
      }
    }

    .model-info {
      margin-bottom: 12px;
    }

    .model-metrics {
      margin: 12px 0;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 4px;

      h4 {
        margin: 0 0 8px 0;
        font-size: 14px;
      }

      .metrics-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 8px;

        .metric-item {
          text-align: center;

          .metric-label {
            font-size: 12px;
            color: #909399;
          }

          .metric-value {
            font-size: 18px;
            font-weight: 600;
            color: #409eff;
          }
        }
      }
    }

    .model-actions {
      margin-top: auto;
      display: flex;
      gap: 8px;
      justify-content: center;
    }
  }
}

.consistency-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .conflict-count {
    color: #f56c6c;
    font-weight: 500;
  }
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #909399;

  p {
    margin-top: 16px;
  }
}

.empty-container {
  padding: 40px 0;
}

.conflict-list {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .conflict-item {
    padding: 16px;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    background: #fafafa;

    .conflict-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .conflict-text {
        font-size: 18px;
        font-weight: 600;
        color: #303133;
      }

      .conflict-type-count {
        font-size: 13px;
        color: #f56c6c;
      }
    }

    .conflict-annotations {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-bottom: 16px;

      .annotation-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px 12px;
        background: white;
        border-radius: 4px;
        font-size: 13px;

        .annotation-doc {
          color: #606266;
        }

        .annotation-pos {
          color: #909399;
          font-size: 12px;
        }
      }
    }

    .conflict-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      padding-top: 12px;
      border-top: 1px solid #ebeef5;

      .resolve-label {
        font-size: 13px;
        color: #606266;
      }
    }
  }
}
</style>
