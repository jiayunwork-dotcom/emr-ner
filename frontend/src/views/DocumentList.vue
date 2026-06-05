<template>
  <div class="document-list">
    <el-card class="header-card">
      <div class="header-content">
        <h2>文档管理</h2>
        <div class="header-actions">
          <el-button type="success" @click="handleExport" :disabled="selectedDocs.length === 0">
            <el-icon><Download /></el-icon>
            导出选中
          </el-button>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            新建文档
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card pending">
          <div class="stat-content">
            <div class="stat-label">待处理</div>
            <div class="stat-value">{{ statusCounts.pending }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card completed">
          <div class="stat-content">
            <div class="stat-label">已完成</div>
            <div class="stat-value">{{ statusCounts.completed }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card annotated">
          <div class="stat-content">
            <div class="stat-label">已标注</div>
            <div class="stat-value">{{ statusCounts.annotated }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card processing">
          <div class="stat-content">
            <div class="stat-label">处理中</div>
            <div class="stat-value">{{ statusCounts.processing }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="待处理" value="pending" />
            <el-option label="处理中" value="processing" />
            <el-option label="已完成" value="completed" />
            <el-option label="已标注" value="annotated" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="filters.documentType" placeholder="全部类型" clearable style="width: 150px">
            <el-option label="入院记录" value="admission" />
            <el-option label="病程记录" value="progress" />
            <el-option label="出院小结" value="discharge" />
          </el-select>
        </el-form-item>
        <el-form-item label="患者ID">
          <el-input v-model="filters.patientId" placeholder="输入患者ID" style="width: 200px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadDocuments">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="selectedDocs.length > 0" class="batch-actions-card">
      <div class="batch-actions">
        <span>已选择 {{ selectedDocs.length }} 个文档</span>
        <el-button size="small" type="primary" @click="batchMarkStatus('annotated')">
          <el-icon><Check /></el-icon>
          标记为已标注
        </el-button>
        <el-button size="small" type="warning" @click="batchMarkStatus('pending')">
          <el-icon><Clock /></el-icon>
          标记为待复审
        </el-button>
        <el-button size="small" @click="clearSelection">取消选择</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table 
        :data="documents" 
        v-loading="loading" 
        stripe
        @selection-change="handleSelectionChange"
        ref="tableRef"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="documentType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getDocTypeTag(row.documentType)">
              {{ getDocTypeName(row.documentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="patientId" label="患者ID" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelVersion" label="模型版本" width="140" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDocument(row)">
              查看
            </el-button>
            <el-button 
              size="small" 
              type="success" 
              link 
              @click="processDocument(row)"
              :disabled="row.status === 'processing'"
            >
              处理
            </el-button>
            <el-button size="small" type="warning" link @click="annotateDocument(row)">
              标注
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadDocuments"
        @current-change="loadDocuments"
      />
    </el-card>

    <el-dialog v-model="showCreateDialog" title="新建文档" width="600px">
      <el-form :model="newDocument" label-width="100px">
        <el-form-item label="文档类型" required>
          <el-select v-model="newDocument.documentType" style="width: 100%">
            <el-option label="入院记录" value="admission" />
            <el-option label="病程记录" value="progress" />
            <el-option label="出院小结" value="discharge" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="newDocument.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="患者ID">
          <el-input v-model="newDocument.patientId" placeholder="请输入患者ID" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="newDocument.content"
            type="textarea"
            :rows="8"
            placeholder="请输入病历文本内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createDocument">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Download, Check, Clock } from '@element-plus/icons-vue'
import { documentApi } from '@/api'

const router = useRouter()
const tableRef = ref(null)
const documents = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const selectedDocs = ref([])
const statusCounts = ref({
  pending: 0,
  processing: 0,
  completed: 0,
  annotated: 0,
  failed: 0
})

const filters = ref({
  status: '',
  documentType: '',
  patientId: ''
})

const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

const newDocument = ref({
  documentType: 'admission',
  title: '',
  patientId: '',
  content: ''
})

const loadStatusCounts = async () => {
  try {
    statusCounts.value = await documentApi.getStatusCounts()
  } catch (error) {
    console.error('加载状态统计失败', error)
  }
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.value.page - 1,
      size: pagination.value.size,
      ...filters.value
    }
    const res = await documentApi.getDocuments(params)
    documents.value = res.content
    pagination.value.total = res.totalElements
  } catch (error) {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

const createDocument = async () => {
  if (!newDocument.value.documentType || !newDocument.value.content) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    await documentApi.createDocument(newDocument.value)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    newDocument.value = { documentType: 'admission', title: '', patientId: '', content: '' }
    loadDocuments()
    loadStatusCounts()
  } catch (error) {
    ElMessage.error('创建失败')
  }
}

const processDocument = async (row) => {
  try {
    await documentApi.processDocument(row.id)
    ElMessage.success('已开始处理，请稍候刷新查看结果')
    setTimeout(() => {
      loadDocuments()
      loadStatusCounts()
    }, 2000)
  } catch (error) {
    ElMessage.error('处理失败')
  }
}

const viewDocument = (row) => {
  router.push(`/documents/${row.id}`)
}

const annotateDocument = (row) => {
  router.push(`/annotator/${row.id}`)
}

const handleSelectionChange = (selection) => {
  selectedDocs.value = selection
}

const clearSelection = () => {
  tableRef.value?.clearSelection()
}

const batchMarkStatus = async (status) => {
  if (selectedDocs.value.length === 0) {
    ElMessage.warning('请先选择文档')
    return
  }
  const statusText = status === 'annotated' ? '已标注' : '待复审'
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${selectedDocs.value.length} 个文档标记为"${statusText}"吗？`,
      '确认操作',
      { type: 'warning' }
    )
    const ids = selectedDocs.value.map(d => d.id)
    await documentApi.batchUpdateStatus(ids, status)
    ElMessage.success(`已成功标记 ${ids.length} 个文档`)
    clearSelection()
    loadDocuments()
    loadStatusCounts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量操作失败')
    }
  }
}

const handleExport = async () => {
  if (selectedDocs.value.length === 0) {
    ElMessage.warning('请先选择要导出的文档')
    return
  }
  try {
    const ids = selectedDocs.value.map(d => d.id)
    const data = await documentApi.exportAnnotations(ids)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `annotations_${Date.now()}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const getDocTypeName = (type) => {
  const map = { admission: '入院记录', progress: '病程记录', discharge: '出院小结' }
  return map[type] || type
}

const getDocTypeTag = (type) => {
  const map = { admission: '', progress: 'success', discharge: 'warning' }
  return map[type] || ''
}

const getStatusName = (status) => {
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', annotated: '已标注', failed: '失败' }
  return map[status] || status
}

const getStatusTag = (status) => {
  const map = { pending: 'info', processing: 'warning', completed: 'success', annotated: 'primary', failed: 'danger' }
  return map[status] || ''
}

onMounted(() => {
  loadDocuments()
  loadStatusCounts()
})
</script>

<style scoped lang="scss">
.document-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-card {
  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;

    h2 {
      margin: 0;
      font-size: 20px;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }
}

.stats-row {
  .stat-card {
    text-align: center;
    border: none;
    
    &.pending {
      background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
    }
    &.completed {
      background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
    }
    &.annotated {
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
    }
    &.processing {
      background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
    }

    .stat-content {
      .stat-label {
        font-size: 14px;
        color: #6b7280;
        margin-bottom: 8px;
      }
      .stat-value {
        font-size: 28px;
        font-weight: 600;
        color: #1f2937;
      }
    }
  }
}

.filter-card {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.batch-actions-card {
  background: #eff6ff;
  border-color: #bfdbfe;
  
  .batch-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    
    span {
      color: #1e40af;
      font-weight: 500;
    }
  }
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
