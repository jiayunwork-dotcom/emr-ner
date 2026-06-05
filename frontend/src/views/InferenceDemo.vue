<template>
  <div class="inference-demo">
    <el-card class="input-card">
      <template #header>
        <h2>推理演示</h2>
      </template>
      
      <el-form :model="form" label-width="100px">
        <el-form-item label="文档类型">
          <el-select v-model="form.documentType" style="width: 200px">
            <el-option label="入院记录" value="admission" />
            <el-option label="病程记录" value="progress" />
            <el-option label="出院小结" value="discharge" />
          </el-select>
        </el-form-item>
        <el-form-item label="参考日期">
          <el-date-picker v-model="form.referenceDate" type="date" style="width: 200px" />
        </el-form-item>
        <el-form-item label="病历文本" required>
          <el-input
            v-model="form.text"
            type="textarea"
            :rows="10"
            placeholder="请输入要分析的病历文本..."
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runInference" :loading="loading">
            <el-icon><MagicStick /></el-icon>
            开始抽取
          </el-button>
          <el-button @click="loadSample">
            <el-icon><Document /></el-icon>
            加载示例
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="result-card">
      <template #header>
        <div class="result-header">
          <h3>抽取结果</h3>
          <div class="result-meta">
            <el-tag>模型: {{ result.modelVersion }}</el-tag>
            <span style="margin-left: 12px; color: #909399">
              实体数: {{ result.entities?.length || 0 }} | 
              关系数: {{ result.relations?.length || 0 }}
            </span>
          </div>
        </div>
      </template>

      <el-tabs>
        <el-tab-pane label="高亮视图" name="highlight">
          <div class="highlight-text">
            <span
              v-for="(segment, index) in textSegments"
              :key="index"
              :class="['text-segment', { 'entity-highlight': segment.entity }]"
              :style="segment.entity ? getEntityStyle(segment.entity) : {}"
            >{{ segment.text }}</span>
          </div>
        </el-tab-pane>

        <el-tab-pane label="实体列表" name="entities">
          <el-table :data="result.entities" stripe>
            <el-table-column prop="entityText" label="实体文本" min-width="150" />
            <el-table-column prop="entityType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :style="getEntityTagStyle(row.entityType)">
                  {{ getEntityTypeName(row.entityType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="位置" width="120">
              <template #default="{ row }">
                [{{ row.startPos }}, {{ row.endPos }})
              </template>
            </el-table-column>
            <el-table-column label="标记" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.isNegated" size="small" type="danger">否定</el-tag>
                <el-tag v-if="row.isUncertain" size="small" type="warning" style="margin-left: 4px">不确定</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" width="100">
              <template #default="{ row }">
                {{ (row.confidence * 100).toFixed(1) }}%
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="关系列表" name="relations">
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
              <el-card shadow="never" style="margin-bottom: 8px">
                <strong>{{ tl.timeExpression }}</strong>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="JSON输出" name="json">
          <pre class="json-output">{{ JSON.stringify(result, null, 2) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Document } from '@element-plus/icons-vue'
import { documentApi } from '@/api'

const loading = ref(false)
const result = ref(null)

const form = ref({
  documentType: 'admission',
  referenceDate: null,
  text: ''
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

const textSegments = computed(() => {
  if (!form.value.text || !result.value?.entities) return []
  
  const text = form.value.text
  const entities = result.value.entities
  const segments = []
  let lastEnd = 0
  
  const sortedEntities = [...entities].sort((a, b) => a.startPos - b.startPos)
  
  for (const entity of sortedEntities) {
    if (entity.startPos > lastEnd) {
      segments.push({ text: text.slice(lastEnd, entity.startPos), entity: null })
    }
    segments.push({ text: text.slice(entity.startPos, entity.endPos), entity })
    lastEnd = entity.endPos
  }
  
  if (lastEnd < text.length) {
    segments.push({ text: text.slice(lastEnd), entity: null })
  }
  
  return segments
})

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
    'drug_for_disease': '药物-适应症',
    'test_for_disease': '检查-疾病'
  }
  return map[type] || type
}

const runInference = async () => {
  if (!form.value.text.trim()) {
    ElMessage.warning('请输入病历文本')
    return
  }
  
  loading.value = true
  try {
    result.value = await documentApi.infer(form.value.text)
    ElMessage.success('抽取完成')
  } catch (error) {
    ElMessage.error('抽取失败')
  } finally {
    loading.value = false
  }
}

const loadSample = () => {
  form.value.text = `患者因"反复头痛3月，加重伴呕吐1周"入院。3月前患者无明显诱因出现头痛，以双侧颞部为主，呈搏动性疼痛，休息后可缓解。1周前头痛加重，伴恶心呕吐，呕吐物为胃内容物。既往史: 有高血压病史5年，规律服用硝苯地平缓释片控制血压，否认糖尿病史。查体: 体温36.8℃，脉搏72次/分，呼吸18次/分，血压145/90mmHg。神志清楚，言语流利，双侧瞳孔等大等圆，直径约3mm，对光反射灵敏。四肢肌力肌张力正常，病理征未引出。辅助检查: 头颅CT未见明显异常，血常规白细胞计数正常，血糖5.6mmol/L。初步诊断: 1.偏头痛；2.高血压病2级。`
}
</script>

<style scoped lang="scss">
.inference-demo {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .input-card, .result-card {
    h2, h3 {
      margin: 0;
      font-size: 20px;
    }
  }

  .result-card {
    .result-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .highlight-text {
      padding: 16px;
      background: #fafafa;
      border-radius: 4px;
      line-height: 2.2;
      font-size: 15px;

      .text-segment {
        &.entity-highlight {
          padding: 2px 0;
          border-radius: 2px;
        }
      }
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
}
</style>
