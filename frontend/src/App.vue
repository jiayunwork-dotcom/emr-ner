<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="header-left">
        <el-icon class="logo-icon"><Document /></el-icon>
        <h1>电子病历结构化抽取系统</h1>
      </div>
      <div class="header-right">
        <el-button-group>
          <el-button 
            v-for="item in menuItems" 
            :key="item.path"
            :type="isActive(item.path) ? 'primary' : 'default'"
            @click="navigateTo(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            {{ item.label }}
          </el-button>
        </el-button-group>
      </div>
    </el-header>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Document, Edit, Files, DataAnalysis, MagicStick } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const menuItems = ref([
  { path: '/documents', label: '文档管理', icon: 'Document' },
  { path: '/batch-tasks', label: '批量处理', icon: 'Files' },
  { path: '/models', label: '模型管理', icon: 'DataAnalysis' },
  { path: '/inference', label: '推理演示', icon: 'MagicStick' }
])

const navigateTo = (path) => {
  router.push(path)
}

const isActive = (path) => {
  return route.path.startsWith(path)
}
</script>

<style scoped lang="scss">
.app-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 64px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo-icon {
      font-size: 28px;
      color: #409eff;
    }

    h1 {
      font-size: 20px;
      font-weight: 600;
      margin: 0;
      color: #303133;
    }
  }
}

.app-main {
  flex: 1;
  padding: 24px;
  background: #f5f7fa;
  overflow-y: auto;
}
</style>
