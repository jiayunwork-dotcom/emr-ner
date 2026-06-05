<template>
  <div class="document-detail">
    <el-card>
      <template #header>
        <div class="header-content">
          <div>
            <h2>{{ document?.title || '文档详情' }}</h2>
            <el-tag>{{ getDocTypeName(document?.documentType) }}</el-tag>
            <el-tag :type="getStatusTag(document?.status)" style="margin-left: 8px">
              {{ getStatusName(document?.status) }}
            </el-tag>
          </div>
          <div>
            <el-button @click="goBack">
              <el-icon><Back /></el-icon>
              返回
            </el-button>
            <el-button type="primary" @click="processDocument" :disabled="document?.status === 'processing'">
              <el-icon><MagicStick /></el-icon>
              处理文档
            </el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="文档ID">{{ document?.id }}</el-descriptions-item>
        <el-descriptions-item label="患者ID">{{ document?.patientId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="就诊ID">{{ document?.visitId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模型版本">{{ document?.modelVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入院日期">{{ document?.admissionDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出院日期">{{ document?.dischargeDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ document?.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ document?.updatedAt }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>文档内容</el-divider>
      <div class="document-content">
        {{ document?.content }}
      </div>

      <el-divider>抽取结果</el-divider>
      <el-tabs v-if="result">
        <el-tab-pane label="实体" name="entities">
          <el-table :data="result.entities" stripe>
            <el-table-column prop="entityText" label="实体文本" min-width="150" />
            <el-table-column prop="entityType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag>{{ getEntityTypeName(row.entityType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="位置" width="120">
              <template #default="{ row }">
                [{{ row.startPos }}, {{ row.endPos }})
              </template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" width="100">
              <template #default="{ row }">
                {{ (row.confidence * 100).toFixed(1) }}%
              </template>
            </el-table-column>
            <el-table-column label="标记" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.isNegated" size="small" type="danger">否定</el-tag>
                <el-tag v-if="row.isUncertain" size="small" type="warning" style="margin-left: 4px">不确定</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.source === 'human' ? 'success' : 'info'">
                  {{ row.source === 'human' ? '人工' : '模型' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="关系" name="relations">
          <el-table :data="result.relations" stripe>
            <el-table-column prop="headEntityText" label="头实体" min-width="150" />
            <el-table-column label="关系" width="150">
              <template #default="{ row }">
                <el-tag>{{ getRelationTypeName(row.relationType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tailEntityText" label="尾实体" min-width="150" />
            <el-table-column prop="confidence" label="置信度" width="100">
              <template #default="{ row }">
                {{ (row.confidence * 100).toFixed(1) }}%
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="时间线" name="timelines">
          <el-timeline>
            <el-timeline-item
              v-for="(tl, index) in result.timelines"
              :key="index"
              :timestamp="tl.normalizedDate"
              placement="top"
            >
              <el-card shadow="never">
                <h4>{{ tl.timeExpression }}</h4>
                <p v-if="tl.associatedEvent">关联事件: {{ tl.associatedEvent }}</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="JSON" name="json">
          <pre class="json-output">{{ JSON.stringify(result, null, 2) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, MagicStick } from '@element-plus/icons-vue'
import { documentApi } from '@/api'

const route = useRoute()
const router = useRouter()
const documentId = route.params.id

const document = ref(null)
const result = ref(null)

const loadData = async () => {
  try {
    const [docRes, resultRes] = await Promise.all([
      documentApi.getDocument(documentId),
      documentApi.getDocumentResult(documentId)
    ])
    document.value = docRes
    result.value = resultRes
  } catch (error) {
    ElMessage.error('加载文档失败')
  }
}

const processDocument = async () => {
  try {
    await documentApi.processDocument(documentId)
    ElMessage.success('已开始处理')
    setTimeout(loadData, 2000)
  } catch (error) {
    ElMessage.error('处理失败')
  }
}

const goBack = () => {
  router.back()
}

const getDocTypeName = (type) => {
  const map = { admission: '入院记录', progress: '病程记录', discharge: '出院小结' }
  return map[type] || type
}

const getStatusName = (status) => {
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', failed: '失败' }
  return map[status] || status
}

const getStatusTag = (status) => {
  const map = { pending: 'info', processing: 'warning', completed: 'success', failed: 'danger' }
  return map[status] || ''
}

const getEntityTypeName = (type) => {
  const map = { disease: '疾病', symptom: '症状', drug: '药物', test: '检查', operation: '手术', anatomy: '解剖', time: '时间' }
  return map[type] || type
}

const getRelationTypeName = (type) => {
  const map = {
    'symptom_of_disease': '症状-疾病',
    'drug_for_disease': '药物-适应症',
    'test_for_disease': '检查-疾病',
    'operation_for_disease': '手术-疾病',
    'location_of_disease': '部位-疾病'
  }
  return map[type] || type
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.document-detail {
  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;

    h2 {
      margin: 0 0 8px 0;
    }
  }

  .document-content {
    padding: 16px;
    background: #f5f7fa;
    border-radius: 4px;
    line-height: 2;
    white-space: pre-wrap;
  }

  .json-output {
    padding: 16px;
    background: #1e1e1e;
    color: #d4d4d4;
    border-radius: 4px;
    max-height: 500px;
    overflow: auto;
    font-family: 'Consolas', monospace;
    font-size: 13px;
    line-height: 1.6;
  }
}
</style>
