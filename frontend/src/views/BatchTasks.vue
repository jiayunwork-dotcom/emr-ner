<template>
  <div class="batch-tasks">
    <el-card class="header-card">
      <div class="header-content">
        <h2>批量处理任务</h2>
        <div>
          <el-upload
            :show-file-list="false"
            :before-upload="handleUpload"
            accept=".json,.zip"
          >
            <el-button type="primary">
              <el-icon><Upload /></el-icon>
              上传文件处理
            </el-button>
          </el-upload>
        </div>
      </div>
    </el-card>

    <el-card>
      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="200">
          <template #default="{ row }">
            <el-progress 
              :percentage="row.totalCount > 0 ? Math.round((row.processedCount + row.failedCount) / row.totalCount * 100) : 0"
              :status="row.status === 'failed' ? 'exception' : row.status === 'completed' ? 'success' : ''"
            />
            <div class="progress-text">
              成功: {{ row.processedCount }} / 失败: {{ row.failedCount }} / 总计: {{ row.totalCount }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="modelVersion" label="模型版本" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="refresh">
              刷新
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadTasks"
        @current-change="loadTasks"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { batchTaskApi } from '@/api'

const tasks = ref([])
const loading = ref(false)
let refreshTimer = null

const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

const loadTasks = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.value.page - 1,
      size: pagination.value.size
    }
    const res = await batchTaskApi.getTasks(params)
    tasks.value = res.content
    pagination.value.total = res.totalElements
  } catch (error) {
    ElMessage.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

const handleUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  
  try {
    await batchTaskApi.uploadAndProcess(formData)
    ElMessage.success('任务已提交，开始处理')
    loadTasks()
  } catch (error) {
    ElMessage.error('上传失败')
  }
  return false
}

const refresh = () => {
  loadTasks()
}

const getStatusName = (status) => {
  const map = { pending: '等待中', running: '处理中', completed: '已完成', failed: '失败' }
  return map[status] || status
}

const getStatusTag = (status) => {
  const map = { pending: 'info', running: 'warning', completed: 'success', failed: 'danger' }
  return map[status] || ''
}

onMounted(() => {
  loadTasks()
  refreshTimer = setInterval(loadTasks, 5000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped lang="scss">
.batch-tasks {
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

  .progress-text {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }

  .pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
