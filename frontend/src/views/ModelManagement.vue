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

      <el-tab-pane label="性能对比" name="comparison">
        <div class="comparison-container">
          <el-card class="dataset-section">
            <template #header>
              <div class="section-header">
                <span>评估数据集管理</span>
                <el-button type="primary" @click="showUploadDatasetDialog = true">
                  <el-icon><Upload /></el-icon>
                  上传数据集
                </el-button>
              </div>
            </template>

            <el-table :data="datasets" v-loading="loadingDatasets" stripe>
              <el-table-column prop="datasetName" label="数据集名称" width="200" />
              <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
              <el-table-column prop="recordCount" label="记录数" width="100" align="center" />
              <el-table-column prop="createdAt" label="上传时间" width="180" />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" @click="selectDataset(row)">
                    {{ selectedDataset?.id === row.id ? '已选择' : '选择此数据集' }}
                  </el-button>
                  <el-button size="small" type="danger" @click="deleteDataset(row.id)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card class="compare-section" v-if="selectedDataset">
            <template #header>
              <div class="section-header">
                <span>对比视图 - {{ selectedDataset.datasetName }}</span>
                <div>
                  <el-select 
                    v-model="selectedModelForEval" 
                    placeholder="选择模型版本进行评估"
                    style="width: 200px; margin-right: 8px;"
                    size="small"
                  >
                    <el-option 
                      v-for="model in models" 
                      :key="model.id" 
                      :label="model.versionName" 
                      :value="model.id" 
                    />
                  </el-select>
                  <el-button 
                    type="primary" 
                    size="small" 
                    :disabled="!selectedModelForEval || evaluating"
                    @click="startEvaluation"
                  >
                    <el-icon v-if="evaluating" class="is-loading"><Loading /></el-icon>
                    {{ evaluating ? '评估中...' : '开始评估' }}
                  </el-button>
                </div>
              </div>
            </template>

            <div v-if="comparisonResults.length === 0 && !loadingComparison" class="empty-container">
              <el-empty description="暂无评估数据，请选择模型版本开始评估">
                <el-select 
                  v-model="selectedModelForEval" 
                  placeholder="选择模型版本"
                  style="width: 200px; margin-bottom: 12px;"
                >
                  <el-option 
                    v-for="model in models" 
                    :key="model.id" 
                    :label="model.versionName" 
                    :value="model.id" 
                  />
                </el-select>
                <div>
                  <el-button type="primary" :disabled="!selectedModelForEval" @click="startEvaluation">
                    开始评估
                  </el-button>
                </div>
              </el-empty>
            </div>

            <div v-else v-loading="loadingComparison">
              <div class="evaluation-progress" v-if="evaluating">
                <el-progress 
                  :percentage="evaluationProgressPercent" 
                  :status="evaluationProgressStatus"
                />
                <span class="progress-text">
                  已完成 {{ currentProgress?.processedCount || 0 }} / {{ currentProgress?.totalCount || 0 }}
                  <span v-if="currentProgress?.failedCount > 0">
                    (失败: {{ currentProgress.failedCount }})
                  </span>
                </span>
              </div>

              <div class="compare-table-wrapper">
                <el-table :data="compareTableData" border stripe class="compare-table">
                  <el-table-column 
                    prop="entityType" 
                    label="实体类型" 
                    width="120" 
                    fixed="left"
                    align="center"
                  >
                    <template #default="{ row }">
                      <strong>{{ row.entityTypeLabel }}</strong>
                    </template>
                  </el-table-column>
                  <el-table-column 
                    v-for="result in comparisonResults" 
                    :key="result.modelVersionId"
                    :label="result.modelVersionName"
                    align="center"
                    min-width="130"
                  >
                    <template #default="{ row }">
                      <div 
                        class="f1-cell" 
                        :style="getF1CellStyle(row.f1Values[result.modelVersionId])"
                      >
                        {{ formatF1(row.f1Values[result.modelVersionId]) }}
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="radar-chart-wrapper">
                <h4>各实体类型F1雷达图</h4>
                <div ref="radarChartRef" class="radar-chart"></div>
              </div>
            </div>
          </el-card>
        </div>
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

    <el-dialog v-model="showUploadDatasetDialog" title="上传评估数据集" width="500px">
      <el-form :model="newDataset" label-width="100px">
        <el-form-item label="数据集名称" required>
          <el-input v-model="newDataset.datasetName" placeholder="输入数据集名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newDataset.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="JSON文件" required>
          <el-upload
            drag
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleDatasetFileChange"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
            accept=".json"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将JSON文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                请上传JSON格式的评估数据集，每条记录包含text和entities字段
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDatasetDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!newDataset.file" @click="uploadDataset">
          上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, nextTick, computed, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Loading, Upload, UploadFilled } from '@element-plus/icons-vue'
import { modelApi, documentApi, evaluationApi } from '@/api'
import * as echarts from 'echarts'

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

const entityTypeOrder = ['disease', 'symptom', 'drug', 'test', 'operation', 'anatomy', 'time']

const datasets = ref([])
const loadingDatasets = ref(false)
const showUploadDatasetDialog = ref(false)
const newDataset = ref({
  datasetName: '',
  description: '',
  file: null
})
const selectedDataset = ref(null)
const selectedModelForEval = ref(null)
const comparisonResults = ref([])
const loadingComparison = ref(false)
const evaluating = ref(false)
const currentTaskId = ref(null)
const currentProgress = ref(null)
let progressPollingTimer = null

const radarChartRef = ref(null)
let radarChart = null

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

const loadDatasets = async () => {
  loadingDatasets.value = true
  try {
    datasets.value = await evaluationApi.getDatasets()
  } catch (error) {
    ElMessage.error('加载数据集列表失败')
  } finally {
    loadingDatasets.value = false
  }
}

const handleDatasetFileChange = (file) => {
  newDataset.value.file = file.raw
}

const uploadDataset = async () => {
  if (!newDataset.value.datasetName || !newDataset.value.file) {
    ElMessage.warning('请填写数据集名称并选择文件')
    return
  }
  try {
    const formData = new FormData()
    formData.append('datasetName', newDataset.value.datasetName)
    formData.append('description', newDataset.value.description || '')
    formData.append('file', newDataset.value.file)
    
    await evaluationApi.uploadDataset(formData)
    ElMessage.success('数据集上传成功')
    showUploadDatasetDialog.value = false
    newDataset.value = { datasetName: '', description: '', file: null }
    loadDatasets()
  } catch (error) {
    const msg = error.response?.data?.error || error.message || '上传失败'
    ElMessage.error(msg)
  }
}

const selectDataset = async (dataset) => {
  selectedDataset.value = dataset
  selectedModelForEval.value = null
  await loadComparisonResults()
}

const deleteDataset = async (datasetId) => {
  try {
    await ElMessageBox.confirm('确定要删除该数据集吗？相关评估结果也会被删除', '提示', {
      type: 'warning'
    })
    await evaluationApi.deleteDataset(datasetId)
    ElMessage.success('删除成功')
    if (selectedDataset.value?.id === datasetId) {
      selectedDataset.value = null
      comparisonResults.value = []
    }
    loadDatasets()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const loadComparisonResults = async () => {
  if (!selectedDataset.value) return
  loadingComparison.value = true
  try {
    comparisonResults.value = await evaluationApi.getComparisonResults(selectedDataset.value.id)
    await nextTick()
    renderRadarChart()
  } catch (error) {
    ElMessage.error('加载对比结果失败')
  } finally {
    loadingComparison.value = false
  }
}

const startEvaluation = async () => {
  if (!selectedDataset.value || !selectedModelForEval.value) {
    ElMessage.warning('请选择数据集和模型版本')
    return
  }
  try {
    const result = await evaluationApi.submitEvaluation(
      selectedDataset.value.id,
      selectedModelForEval.value
    )
    currentTaskId.value = result.taskId
    evaluating.value = true
    ElMessage.success('评估任务已提交')
    startProgressPolling()
  } catch (error) {
    if (error.response?.status === 409) {
      ElMessage.warning(error.response.data.error)
    } else {
      const msg = error.response?.data?.error || error.message || '提交失败'
      ElMessage.error(msg)
    }
  }
}

const startProgressPolling = () => {
  stopProgressPolling()
  progressPollingTimer = setInterval(async () => {
    try {
      currentProgress.value = await evaluationApi.getTaskProgress(currentTaskId.value)
      if (currentProgress.value.status === 'completed' || currentProgress.value.status === 'failed') {
        stopProgressPolling()
        evaluating.value = false
        if (currentProgress.value.status === 'completed') {
          ElMessage.success('评估完成')
          await loadComparisonResults()
        } else {
          ElMessage.error('评估失败: ' + (currentProgress.value.errorMessage || '未知错误'))
        }
      }
    } catch (error) {
      console.error('查询进度失败', error)
    }
  }, 2000)
}

const stopProgressPolling = () => {
  if (progressPollingTimer) {
    clearInterval(progressPollingTimer)
    progressPollingTimer = null
  }
}

const evaluationProgressPercent = computed(() => {
  if (!currentProgress.value || currentProgress.value.totalCount === 0) return 0
  return Math.round((currentProgress.value.processedCount / currentProgress.value.totalCount) * 100)
})

const evaluationProgressStatus = computed(() => {
  if (!currentProgress.value) return ''
  if (currentProgress.value.status === 'failed') return 'exception'
  if (currentProgress.value.status === 'completed') return 'success'
  return ''
})

const compareTableData = computed(() => {
  if (comparisonResults.value.length === 0) return []
  
  const rows = []
  
  for (const entityType of entityTypeOrder) {
    const row = {
      entityType,
      entityTypeLabel: getEntityTypeName(entityType),
      f1Values: {}
    }
    for (const result of comparisonResults.value) {
      const metrics = result.metricsByType[entityType]
      row.f1Values[result.modelVersionId] = metrics?.f1Score ?? null
    }
    rows.push(row)
  }
  
  const macroRow = {
    entityType: 'overall-macro',
    entityTypeLabel: 'Overall (Macro-F1)',
    f1Values: {}
  }
  for (const result of comparisonResults.value) {
    macroRow.f1Values[result.modelVersionId] = result.overallMacro?.f1Score ?? null
  }
  rows.push(macroRow)

  const microRow = {
    entityType: 'overall-micro',
    entityTypeLabel: 'Overall (Micro-F1)',
    f1Values: {}
  }
  for (const result of comparisonResults.value) {
    microRow.f1Values[result.modelVersionId] = result.overallMicro?.f1Score ?? null
  }
  rows.push(microRow)
  
  return rows
})

const getF1CellStyle = (f1) => {
  if (f1 === null || f1 === undefined) {
    return { backgroundColor: '#f5f7fa', color: '#909399' }
  }
  const value = Math.max(0, Math.min(1, f1))
  const r = Math.round(245 * (1 - value) + 103 * value)
  const g = Math.round(108 * (1 - value) + 194 * value)
  const b = Math.round(108 * (1 - value) + 58 * value)
  return {
    backgroundColor: `rgb(${r}, ${g}, ${b})`,
    color: value > 0.6 ? '#fff' : '#303133',
    fontWeight: 600
  }
}

const formatF1 = (f1) => {
  if (f1 === null || f1 === undefined) return '-'
  return (f1 * 100).toFixed(1) + '%'
}

const renderRadarChart = () => {
  if (!radarChartRef.value || comparisonResults.value.length === 0) return
  
  if (!radarChart) {
    radarChart = echarts.init(radarChartRef.value)
  }
  
  const indicator = entityTypeOrder.map(type => ({
    name: getEntityTypeName(type),
    max: 1
  }))
  
  const series = comparisonResults.value.map((result, index) => ({
    value: entityTypeOrder.map(type => {
      const metrics = result.metricsByType[type]
      return metrics?.f1Score ?? 0
    }),
    name: result.modelVersionName,
    lineStyle: {
      width: 2
    },
    areaStyle: {
      opacity: 0.1
    }
  }))
  
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#8e44ad', '#16a085']
  
  radarChart.setOption({
    color: colors,
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        let html = `<strong>${params.name}</strong><br/>`
        entityTypeOrder.forEach((type, i) => {
          const value = params.value[i]
          html += `${getEntityTypeName(type)}: ${(value * 100).toFixed(1)}%<br/>`
        })
        return html
      }
    },
    legend: {
      data: comparisonResults.value.map(r => r.modelVersionName),
      bottom: 0
    },
    radar: {
      indicator,
      center: ['50%', '50%'],
      radius: '65%',
      axisName: {
        color: '#606266',
        fontSize: 12
      },
      splitArea: {
        areaStyle: {
          color: ['#fafafa', '#f5f7fa', '#ebeef5', '#e4e7ed']
        }
      }
    },
    series: [{
      type: 'radar',
      data: series
    }]
  })
}

const handleResize = () => {
  radarChart?.resize()
}

onMounted(() => {
  loadModels()
  loadDatasets()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  stopProgressPolling()
  window.removeEventListener('resize', handleResize)
  radarChart?.dispose()
})

watch(activeTab, (newTab) => {
  if (newTab === 'comparison') {
    loadDatasets()
  }
})
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

.comparison-container {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .dataset-section {
    .el-table {
      margin-top: 0;
    }
  }

  .compare-section {
    .evaluation-progress {
      margin-bottom: 20px;
      padding: 16px;
      background: #f5f7fa;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 16px;

      .el-progress {
        flex: 1;
      }

      .progress-text {
        font-size: 13px;
        color: #606266;
        white-space: nowrap;
      }
    }

    .compare-table-wrapper {
      margin-bottom: 24px;
      overflow-x: auto;

      .compare-table {
        .f1-cell {
          padding: 8px 12px;
          border-radius: 4px;
          text-align: center;
          font-size: 13px;
        }
      }
    }

    .radar-chart-wrapper {
      h4 {
        margin: 0 0 12px 0;
        font-size: 14px;
        font-weight: 600;
        color: #303133;
      }

      .radar-chart {
        width: 100%;
        height: 450px;
      }
    }
  }
}

:deep(.el-upload-dragger) {
  padding: 20px;
}
</style>
