<template>
  <div class="model-management">
    <el-card class="header-card">
      <div class="header-content">
        <h2>模型管理</h2>
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Plus /></el-icon>
          上传模型
        </el-button>
      </div>
    </el-card>

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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { modelApi } from '@/api'

const models = ref([])
const showUploadDialog = ref(false)
const newModel = ref({
  versionName: '',
  modelType: 'BERT-CRF',
  description: '',
  file: null
})

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
</style>
