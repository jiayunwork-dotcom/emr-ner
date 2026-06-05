<template>
  <div class="document-list">
    <el-card class="header-card">
      <div class="header-content">
        <h2>文档管理</h2>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          新建文档
        </el-button>
      </div>
    </el-card>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
            <el-option label="待处理" value="pending" />
            <el-option label="处理中" value="processing" />
            <el-option label="已完成" value="completed" />
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

    <el-card>
      <el-table :data="documents" v-loading="loading" stripe>
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
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { documentApi } from '@/api'

const router = useRouter()
const documents = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)

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
  } catch (error) {
    ElMessage.error('创建失败')
  }
}

const processDocument = async (row) => {
  try {
    await documentApi.processDocument(row.id)
    ElMessage.success('已开始处理，请稍候刷新查看结果')
    setTimeout(loadDocuments, 2000)
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

const getDocTypeName = (type) => {
  const map = { admission: '入院记录', progress: '病程记录', discharge: '出院小结' }
  return map[type] || type
}

const getDocTypeTag = (type) => {
  const map = { admission: '', progress: 'success', discharge: 'warning' }
  return map[type] || ''
}

const getStatusName = (status) => {
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', failed: '失败' }
  return map[status] || status
}

const getStatusTag = (status) => {
  const map = { pending: 'info', processing: 'warning', completed: 'success', failed: 'danger' }
  return map[status] || ''
}

onMounted(loadDocuments)
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
  }
}

.filter-card {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
