<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">智能查询</h1>
        <p class="page-subtitle">用自然语言查询活动、报名、签到、反馈、信用和通知数据。</p>
      </div>
      <el-button :icon="Refresh" @click="submit">刷新</el-button>
    </div>

    <div class="panel query-panel">
      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        maxlength="120"
        show-word-limit
        placeholder="例如：查询本月计算机协会发布的活动报名情况"
        @keyup.ctrl.enter="submit"
      />
      <div class="query-actions">
        <div class="example-list">
          <el-button
            v-for="item in examples"
            :key="item"
            size="small"
            plain
            @click="useExample(item)"
          >
            {{ item }}
          </el-button>
        </div>
        <el-button type="primary" :icon="Search" :loading="loading" @click="submit">查询</el-button>
      </div>
    </div>

    <div v-if="result" class="panel result-panel">
      <div class="result-summary">
        <div>
          <strong>{{ result.summary }}</strong>
          <span class="muted">意图：{{ result.intent }}，共 {{ result.total }} 条</span>
        </div>
        <el-tag effect="plain">第 {{ result.page }} 页 / 每页 {{ result.size }} 条</el-tag>
      </div>

      <el-empty v-if="result.rows.length === 0" description="暂无匹配数据" />
      <el-table v-else :data="result.rows" stripe>
        <el-table-column
          v-for="column in result.columns"
          :key="column.key"
          :prop="column.key"
          :label="column.label"
          min-width="140"
          show-overflow-tooltip
        />
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="result.total"
          @size-change="submit"
          @current-change="submit"
        />
      </div>

      <el-collapse v-if="showSqlPreview && result.sqlPreview" class="sql-preview">
        <el-collapse-item title="SQL 预览" name="sql">
          <pre>{{ result.sqlPreview }}</pre>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div v-else class="soft-panel">
      <h2 class="section-title">可尝试的问题</h2>
      <div class="hint-grid">
        <div v-for="item in examples" :key="item" class="hint-item" @click="useExample(item)">
          {{ item }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { runNaturalQuery } from '../api/naturalQuery'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const question = ref('查询明天的活动')
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const result = ref<any>(null)

const examples = computed(() => {
  if (userStore.user?.role === 'STUDENT') {
    return ['查询明天的活动', '查询我的报名记录', '查询我的未读通知', '查询我的信用流水']
  }
  if (userStore.user?.role === 'ORGANIZER') {
    return ['查询我的活动报名情况', '查询未签到学生', '查询缺勤记录', '查询低评分反馈']
  }
  return ['查询待审核活动', '查询候补人数最多的活动', '查询信用分低的学生', '查询活动取消通知']
})

const showSqlPreview = computed(() => userStore.user?.role === 'ADMIN')

function useExample(value: string) {
  question.value = value
  page.value = 1
  submit()
}

async function submit() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入查询问题')
    return
  }
  loading.value = true
  try {
    result.value = await runNaturalQuery({
      question: question.value.trim(),
      page: page.value,
      size: size.value,
    })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.query-panel {
  margin-bottom: 16px;
}

.query-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.example-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.result-panel {
  margin-bottom: 16px;
}

.result-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.result-summary strong,
.result-summary span {
  display: block;
}

.result-summary span {
  margin-top: 4px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.sql-preview {
  margin-top: 14px;
}

.sql-preview pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #334155;
}

.hint-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.hint-item {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.hint-item:hover {
  border-color: #93c5fd;
  color: #2563eb;
}
</style>
