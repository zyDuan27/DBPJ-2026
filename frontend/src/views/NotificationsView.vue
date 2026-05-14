<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">站内通知</h1>
        <p class="page-subtitle">集中查看审核、报名、候补、签到和信用状态变化。</p>
      </div>
      <div class="toolbar">
        <el-switch v-model="unreadOnly" active-text="仅未读" @change="load" />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Check" @click="readAll">全部已读</el-button>
      </div>
    </div>

    <div class="panel">
      <el-empty v-if="records.length === 0" description="暂无通知" />
      <div v-else class="notification-list">
        <div
          v-for="item in records"
          :key="item.notificationId"
          class="notification-item"
          :class="{ unread: !item.read }"
        >
          <div class="notification-main">
            <div class="notification-title-row">
              <el-tag v-if="!item.read" size="small" type="danger" effect="plain">未读</el-tag>
              <strong>{{ item.title }}</strong>
              <span class="muted">{{ formatTime(item.createdAt) }}</span>
            </div>
            <p>{{ item.content }}</p>
            <el-tag size="small" effect="plain">{{ typeText(item.type) }}</el-tag>
          </div>
          <el-button v-if="!item.read" link type="primary" @click="read(item.notificationId)">
            标为已读
          </el-button>
        </div>
      </div>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { Check, Refresh } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../api/notification'

const page = ref(1)
const size = ref(10)
const total = ref(0)
const unreadOnly = ref(false)
const records = ref<any[]>([])

const typeMap: Record<string, string> = {
  ACTIVITY_APPROVED: '审核通过',
  ACTIVITY_REJECTED: '审核驳回',
  ACTIVITY_CANCELLED: '活动取消',
  REGISTRATION_ENROLLED: '报名成功',
  REGISTRATION_WAITLISTED: '候补队列',
  REGISTRATION_CANCELLED: '报名取消',
  WAITLIST_PROMOTED: '候补转正',
  CHECK_IN_SUCCESS: '签到成功',
  ABSENCE_MARKED: '缺勤记录',
}

function typeText(type: string) {
  return typeMap[type] || type
}

function formatTime(value: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : ''
}

async function load() {
  const data = await getNotifications({
    page: page.value,
    size: size.value,
    unreadOnly: unreadOnly.value,
  }) as any
  records.value = data.list || []
  total.value = Number(data.total || 0)
}

async function read(id: number) {
  await markNotificationRead(id)
  await load()
}

async function readAll() {
  await markAllNotificationsRead()
  page.value = 1
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notification-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.notification-item.unread {
  border-color: #93c5fd;
  background: #eff6ff;
}

.notification-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.notification-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.notification-main p {
  margin: 0;
  color: #334155;
  line-height: 1.6;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
