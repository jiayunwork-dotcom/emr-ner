<template>
  <div class="annotator-container">
    <el-card class="header-card">
      <div class="header-content">
        <div>
          <h2>文档标注</h2>
          <span class="doc-info">
            文档ID: {{ document?.id }} | 
            类型: {{ getDocTypeName(document?.documentType) }} | 
            状态: {{ getStatusName(document?.status) }}
          </span>
        </div>
        <div class="header-actions">
          <el-button @click="loadResult">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="saveAnnotation">
            <el-icon><Check /></el-icon>
            保存标注
          </el-button>
          <el-button @click="goBack">
            <el-icon><Back /></el-icon>
            返回
          </el-button>
        </div>
      </div>
    </el-card>

    <div class="annotator-content">
      <el-card class="text-panel">
        <template #header>
          <div class="panel-header">
            <span>病历文本</span>
            <div class="legend">
              <span 
                v-for="config in entityTypeConfig" 
                :key="config.type"
                class="legend-item"
                :style="{ backgroundColor: config.color + '20', color: config.color }"
              >
                {{ config.label }}
              </span>
            </div>
          </div>
        </template>
        <div 
          class="text-content"
          ref="textContainer"
          @mouseup="handleTextSelect"
          @mousedown="hideToolbar"
        >
          <span
            v-for="(segment, index) in textSegments"
            :key="index"
            :class="['text-segment', { 
              'entity-highlight': segment.entity,
              'negated': segment.entity?.isNegated,
              'uncertain': segment.entity?.isUncertain
            }]"
            :style="segment.entity ? getEntityStyle(segment.entity) : {}"
            @click="segment.entity && selectEntity(segment.entity)"
          >{{ segment.text }}</span>
        </div>

        <div 
          v-if="showToolbar" 
          class="floating-toolbar"
          :style="{ left: toolbarPosition.x + 'px', top: toolbarPosition.y + 'px' }"
          @mousedown.stop
        >
          <div class="toolbar-inner">
            <el-button
              v-for="config in entityTypeConfig"
              :key="config.type"
              size="small"
              :style="{ 
                backgroundColor: config.color + '20', 
                color: config.color,
                borderColor: config.color + '60'
              }"
              @click="quickAddEntity(config.type)"
            >
              {{ config.label }}
            </el-button>
            <el-divider direction="vertical" />
            <el-button size="small" @click="hideToolbar">取消</el-button>
          </div>
        </div>
      </el-card>

      <div class="side-panels">
        <el-card class="entity-panel">
          <template #header>
            <div class="panel-header">
              <span>实体列表 ({{ entities.length }})</span>
              <el-button size="small" type="primary" link @click="showAddEntity = true">
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
          </template>
          <div class="entity-list">
            <div
              v-for="entity in entities"
              :key="entity.id"
              :class="['entity-item', { 'selected': selectedEntity?.id === entity.id }]"
              @click="selectEntity(entity)"
            >
              <div class="entity-header">
                <el-tag 
                  size="small" 
                  :style="getEntityTagStyle(entity.entityType)"
                >
                  {{ getEntityTypeName(entity.entityType) }}
                </el-tag>
                <div class="entity-flags">
                  <el-tag v-if="entity.isNegated" size="small" type="danger">否定</el-tag>
                  <el-tag v-if="entity.isUncertain" size="small" type="warning">不确定</el-tag>
                </div>
                <el-button 
                  class="quick-delete-btn"
                  size="small" 
                  type="danger" 
                  link 
                  @click.stop="quickDeleteEntity(entity.id)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div class="entity-text">{{ entity.entityText }}</div>
              <div class="entity-meta">
                <span>位置: [{{ entity.startPos }}, {{ entity.endPos }})</span>
                <span>置信度: {{ (entity.confidence * 100).toFixed(1) }}%</span>
                <el-tag v-if="entity.source === 'human'" size="small" type="success">人工</el-tag>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="relation-panel">
          <template #header>
            <div class="panel-header">
              <span>关系列表 ({{ relations.length }})</span>
              <el-button size="small" type="primary" link @click="showAddRelation = true">
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
          </template>
          <div class="relation-list">
            <div
              v-for="relation in relations"
              :key="relation.id"
              class="relation-item"
            >
              <div class="relation-content">
                <el-tag size="small">{{ getRelationTypeName(relation.relationType) }}</el-tag>
                <div class="relation-pair">
                  <span class="relation-entity">{{ relation.headEntityText }}</span>
                  <el-icon class="relation-arrow"><ArrowRight /></el-icon>
                  <span class="relation-entity">{{ relation.tailEntityText }}</span>
                </div>
              </div>
              <el-button 
                size="small" 
                type="danger" 
                link 
                @click="deleteRelation(relation.id)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </el-card>

        <el-card class="timeline-panel">
          <template #header>
            <span>时间线 ({{ timelines.length }})</span>
          </template>
          <div class="timeline-list">
            <el-timeline>
              <el-timeline-item
                v-for="(tl, index) in timelines"
                :key="index"
                :timestamp="tl.normalizedDate"
                placement="top"
              >
                <div class="timeline-item-content">
                  <strong>{{ tl.timeExpression }}</strong>
                  <div v-if="tl.associatedEvent" class="timeline-event">
                    关联事件: {{ tl.associatedEvent }}
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="showAddEntity" title="添加实体" width="500px">
      <el-form :model="newEntity" label-width="100px">
        <el-form-item label="实体文本">
          <el-input v-model="newEntity.entityText" placeholder="请输入或选中文本" />
        </el-form-item>
        <el-form-item label="实体类型" required>
          <el-select v-model="newEntity.entityType" style="width: 100%">
            <el-option 
              v-for="config in entityTypeConfig" 
              :key="config.type" 
              :label="config.label" 
              :value="config.type" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="起始位置">
          <el-input-number v-model="newEntity.startPos" :min="0" />
        </el-form-item>
        <el-form-item label="结束位置">
          <el-input-number v-model="newEntity.endPos" :min="0" />
        </el-form-item>
        <el-form-item label="否定标记">
          <el-switch v-model="newEntity.isNegated" />
        </el-form-item>
        <el-form-item label="不确定标记">
          <el-switch v-model="newEntity.isUncertain" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddEntity = false">取消</el-button>
        <el-button type="primary" @click="addEntity">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditEntity" title="编辑实体" width="500px">
      <el-form :model="editEntity" label-width="100px">
        <el-form-item label="实体文本">
          <el-input v-model="editEntity.entityText" />
        </el-form-item>
        <el-form-item label="实体类型" required>
          <el-select v-model="editEntity.entityType" style="width: 100%">
            <el-option 
              v-for="config in entityTypeConfig" 
              :key="config.type" 
              :label="config.label" 
              :value="config.type" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="起始位置">
          <el-input-number v-model="editEntity.startPos" :min="0" />
        </el-form-item>
        <el-form-item label="结束位置">
          <el-input-number v-model="editEntity.endPos" :min="0" />
        </el-form-item>
        <el-form-item label="否定标记">
          <el-switch v-model="editEntity.isNegated" />
        </el-form-item>
        <el-form-item label="不确定标记">
          <el-switch v-model="editEntity.isUncertain" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditEntity = false">取消</el-button>
        <el-button type="danger" @click="deleteEntity(editEntity.id)">删除</el-button>
        <el-button type="primary" @click="updateEntity">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAddRelation" title="添加关系" width="500px">
      <el-form :model="newRelation" label-width="100px">
        <el-form-item label="头实体" required>
          <el-select v-model="newRelation.headEntityId" style="width: 100%">
            <el-option 
              v-for="e in entities" 
              :key="e.id" 
              :label="`[${getEntityTypeName(e.entityType)}] ${e.entityText}`" 
              :value="e.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型" required>
          <el-select v-model="newRelation.relationType" style="width: 100%">
            <el-option label="症状-疾病" value="symptom_of_disease" />
            <el-option label="药物-适应症" value="drug_for_disease" />
            <el-option label="检查-疾病" value="test_for_disease" />
            <el-option label="手术-疾病" value="operation_for_disease" />
            <el-option label="部位-疾病" value="location_of_disease" />
          </el-select>
        </el-form-item>
        <el-form-item label="尾实体" required>
          <el-select v-model="newRelation.tailEntityId" style="width: 100%">
            <el-option 
              v-for="e in entities" 
              :key="e.id" 
              :label="`[${getEntityTypeName(e.entityType)}] ${e.entityText}`" 
              :value="e.id" 
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddRelation = false">取消</el-button>
        <el-button type="primary" @click="addRelation">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Check, Back, Delete, ArrowRight } from '@element-plus/icons-vue'
import { documentApi } from '@/api'

const route = useRoute()
const router = useRouter()
const documentId = route.params.id

const document = ref(null)
const entities = ref([])
const relations = ref([])
const timelines = ref([])
const selectedEntity = ref(null)
const textContainer = ref(null)

const showAddEntity = ref(false)
const showEditEntity = ref(false)
const showAddRelation = ref(false)

const showToolbar = ref(false)
const toolbarPosition = ref({ x: 0, y: 0 })
const selectedTextInfo = ref(null)

const newEntity = ref({
  entityText: '',
  entityType: 'disease',
  startPos: 0,
  endPos: 0,
  isNegated: false,
  isUncertain: false
})

const editEntity = ref({
  id: null,
  entityText: '',
  entityType: 'disease',
  startPos: 0,
  endPos: 0,
  isNegated: false,
  isUncertain: false
})

const newRelation = ref({
  headEntityId: null,
  tailEntityId: null,
  relationType: 'symptom_of_disease'
})

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

const getEntityStyle = (entity) => {
  const config = entityTypeConfig.find(c => c.type === entity.entityType)
  const color = config ? config.color : '#409eff'
  return {
    backgroundColor: color + '20',
    borderBottom: `2px solid ${color}`
  }
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

const getRelationTypeName = (type) => {
  const map = {
    'symptom_of_disease': '症状-疾病',
    'disease_has_symptom': '疾病-症状',
    'drug_for_disease': '药物-适应症',
    'test_for_disease': '检查-疾病',
    'operation_for_disease': '手术-疾病',
    'location_of_disease': '部位-疾病'
  }
  return map[type] || type
}

const textSegments = computed(() => {
  if (!document.value || !document.value.content) return []
  
  const text = document.value.content
  const segments = []
  let lastEnd = 0
  
  const sortedEntities = [...entities.value].sort((a, b) => a.startPos - b.startPos)
  
  for (const entity of sortedEntities) {
    if (entity.startPos > lastEnd) {
      segments.push({ text: text.slice(lastEnd, entity.startPos), entity: null })
    }
    segments.push({ 
      text: text.slice(entity.startPos, entity.endPos), 
      entity: entity 
    })
    lastEnd = entity.endPos
  }
  
  if (lastEnd < text.length) {
    segments.push({ text: text.slice(lastEnd), entity: null })
  }
  
  return segments
})

const loadResult = async () => {
  try {
    const [docRes, resultRes] = await Promise.all([
      documentApi.getDocument(documentId),
      documentApi.getDocumentResult(documentId)
    ])
    document.value = docRes
    entities.value = resultRes.entities || []
    relations.value = resultRes.relations || []
    timelines.value = resultRes.timelines || []
  } catch (error) {
    ElMessage.error('加载文档失败')
  }
}

const selectEntity = (entity) => {
  selectedEntity.value = entity
  editEntity.value = { ...entity }
  showEditEntity.value = true
}

const handleTextSelect = (e) => {
  const selection = window.getSelection()
  if (selection && selection.toString().trim()) {
    const selectedText = selection.toString()
    const text = document.value.content
    
    let startPos = -1
    let endPos = -1
    
    const range = selection.getRangeAt(0)
    const clonedRange = range.cloneRange()
    clonedRange.selectNodeContents(textContainer.value)
    clonedRange.setEnd(range.startContainer, range.startOffset)
    
    const allText = clonedRange.toString()
    const start = allText.length
    const end = start + selectedText.length
    
    if (start >= 0 && end <= text.length && text.substring(start, end) === selectedText) {
      startPos = start
      endPos = end
    } else {
      const idx = text.indexOf(selectedText)
      if (idx !== -1) {
        startPos = idx
        endPos = idx + selectedText.length
      }
    }
    
    if (startPos !== -1 && endPos !== -1) {
      selectedTextInfo.value = {
        entityText: selectedText,
        startPos: startPos,
        endPos: endPos
      }
      
      const rect = range.getBoundingClientRect()
      const containerRect = textContainer.value.getBoundingClientRect()
      
      toolbarPosition.value = {
        x: rect.left - containerRect.left + rect.width / 2 - 200,
        y: rect.top - containerRect.top - 50
      }
      
      showToolbar.value = true
    }
    
    selection.removeAllRanges()
  }
}

const hideToolbar = () => {
  showToolbar.value = false
  selectedTextInfo.value = null
}

const quickAddEntity = async (entityType) => {
  if (!selectedTextInfo.value) {
    hideToolbar()
    return
  }
  
  try {
    const entityData = {
      ...selectedTextInfo.value,
      entityType: entityType,
      isNegated: false,
      isUncertain: false
    }
    await documentApi.addEntity(documentId, entityData)
    ElMessage.success('添加实体成功')
    hideToolbar()
    loadResult()
  } catch (error) {
    ElMessage.error('添加实体失败')
  }
}

const quickDeleteEntity = async (entityId) => {
  try {
    await ElMessageBox.confirm('确定要删除该实体吗？', '提示', {
      type: 'warning'
    })
    await documentApi.deleteEntity(documentId, entityId)
    ElMessage.success('删除实体成功')
    loadResult()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除实体失败')
    }
  }
}

const addEntity = async () => {
  if (!newEntity.value.entityType) {
    ElMessage.warning('请选择实体类型')
    return
  }
  try {
    await documentApi.addEntity(documentId, newEntity.value)
    ElMessage.success('添加实体成功')
    showAddEntity.value = false
    loadResult()
  } catch (error) {
    ElMessage.error('添加实体失败')
  }
}

const updateEntity = async () => {
  if (!editEntity.value.entityType) {
    ElMessage.warning('请选择实体类型')
    return
  }
  try {
    await documentApi.updateEntity(documentId, editEntity.value.id, editEntity.value)
    ElMessage.success('更新实体成功')
    showEditEntity.value = false
    selectedEntity.value = null
    loadResult()
  } catch (error) {
    ElMessage.error('更新实体失败')
  }
}

const deleteEntity = async (entityId) => {
  try {
    await documentApi.deleteEntity(documentId, entityId)
    ElMessage.success('删除实体成功')
    showEditEntity.value = false
    selectedEntity.value = null
    loadResult()
  } catch (error) {
    ElMessage.error('删除实体失败')
  }
}

const addRelation = async () => {
  if (!newRelation.value.headEntityId || !newRelation.value.tailEntityId) {
    ElMessage.warning('请选择头实体和尾实体')
    return
  }
  try {
    await documentApi.addRelation(documentId, newRelation.value)
    ElMessage.success('添加关系成功')
    showAddRelation.value = false
    loadResult()
  } catch (error) {
    ElMessage.error('添加关系失败')
  }
}

const deleteRelation = async (relationId) => {
  try {
    await documentApi.deleteRelation(documentId, relationId)
    ElMessage.success('删除关系成功')
    loadResult()
  } catch (error) {
    ElMessage.error('删除关系失败')
  }
}

const saveAnnotation = async () => {
  try {
    await documentApi.saveAnnotation(documentId)
    ElMessage.success('标注已保存')
  } catch (error) {
    ElMessage.error('保存标注失败')
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
  const map = { pending: '待处理', processing: '处理中', completed: '已完成', annotated: '已标注', failed: '失败' }
  return map[status] || status
}

const handleClickOutside = (e) => {
  if (showToolbar.value && !e.target.closest('.floating-toolbar')) {
    hideToolbar()
  }
}

onMounted(() => {
  loadResult()
  document.addEventListener('mousedown', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside)
})
</script>

<style scoped lang="scss">
.annotator-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.header-card {
  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;

    h2 {
      margin: 0 0 4px 0;
      font-size: 20px;
    }

    .doc-info {
      font-size: 13px;
      color: #909399;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }
}

.annotator-content {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.text-panel {
  flex: 2;
  display: flex;
  flex-direction: column;
  position: relative;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .legend {
      display: flex;
      gap: 8px;

      .legend-item {
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 12px;
      }
    }
  }

  .text-content {
    line-height: 2;
    font-size: 15px;
    padding: 16px;
    background: #fafafa;
    border-radius: 4px;
    min-height: 400px;
    user-select: text;
    position: relative;

    .text-segment {
      transition: background-color 0.2s;

      &.entity-highlight {
        cursor: pointer;
        padding: 2px 0;
        border-radius: 2px;

        &:hover {
          filter: brightness(0.95);
        }

        &.selected {
          outline: 2px solid #409eff;
        }

        &.negated {
          text-decoration: line-through;
          text-decoration-color: #f56c6c;
        }

        &.uncertain {
          font-style: italic;
        }
      }
    }
  }

  .floating-toolbar {
    position: absolute;
    z-index: 1000;
    background: white;
    border-radius: 8px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
    padding: 8px;
    border: 1px solid #ebeef5;

    .toolbar-inner {
      display: flex;
      align-items: center;
      gap: 6px;

      :deep(.el-button) {
        padding: 6px 12px;
        font-size: 12px;
        border-width: 1px;
        border-style: solid;
        
        &:hover {
          opacity: 0.8;
        }
      }
    }
  }
}

.side-panels {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 320px;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

.entity-panel {
  max-height: 350px;
  display: flex;
  flex-direction: column;

  .entity-list {
    overflow-y: auto;
    flex: 1;

    .entity-item {
      padding: 12px;
      border: 1px solid #ebeef5;
      border-radius: 4px;
      margin-bottom: 8px;
      cursor: pointer;
      transition: all 0.2s;
      position: relative;

      &:hover {
        border-color: #409eff;
        background: #ecf5ff;

        .quick-delete-btn {
          opacity: 1;
        }
      }

      &.selected {
        border-color: #409eff;
        background: #ecf5ff;
        box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
      }

      .entity-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 6px;

        .entity-flags {
          display: flex;
          gap: 4px;
        }

        .quick-delete-btn {
          opacity: 0;
          transition: opacity 0.2s;
          margin-left: auto;
        }
      }

      .entity-text {
        font-weight: 500;
        margin-bottom: 4px;
        word-break: break-all;
      }

      .entity-meta {
        font-size: 12px;
        color: #909399;
        display: flex;
        gap: 12px;
        flex-wrap: wrap;
      }
    }
  }
}

.relation-panel {
  max-height: 250px;

  .relation-list {
    overflow-y: auto;

    .relation-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px;
      border: 1px solid #ebeef5;
      border-radius: 4px;
      margin-bottom: 8px;

      .relation-content {
        flex: 1;

        .relation-pair {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-top: 4px;
          font-size: 13px;

          .relation-entity {
            padding: 2px 6px;
            background: #f5f7fa;
            border-radius: 3px;
          }

          .relation-arrow {
            color: #909399;
            font-size: 12px;
          }
        }
      }
    }
  }
}

.timeline-panel {
  flex: 1;
  min-height: 150px;

  .timeline-list {
    max-height: 200px;
    overflow-y: auto;

    :deep(.el-timeline-item__wrapper) {
      padding-bottom: 12px;
    }
  }
}
</style>
