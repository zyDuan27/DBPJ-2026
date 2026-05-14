<template>
  <el-container class="app-shell">
    <el-aside width="220px">
      <div class="brand">
        <div class="brand-mark">C</div>
        <div>
          <strong>校园活动</strong>
          <span>Campus Hub</span>
        </div>
      </div>
      <el-menu router :default-active="$route.path">
        <template v-for="item in menus" :key="item.path">
          <el-menu-item :index="item.path">{{ item.label }}</el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <div>
          <strong>{{ roleName }}</strong>
          <span class="header-subtitle">{{ userStore.user?.name }}</span>
        </div>
        <div class="header-actions">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
            <el-button :icon="Bell" circle @click="goNotifications" />
          </el-badge>
          <el-tag type="success" effect="plain">智能查询一期</el-tag>
          <el-button @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { getUnreadCount } from '../api/notification'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const unreadCount = ref(0)

const menus = computed(() => {
  if (userStore.user?.role === 'STUDENT') {
    return [
      { path: '/student/activities', label: '活动大厅' },
      { path: '/student/my-registrations', label: '我的活动' },
      { path: '/student/query', label: '智能查询' },
      { path: '/student/notifications', label: '站内通知' },
    ]
  }
  if (userStore.user?.role === 'ORGANIZER') {
    return [
      { path: '/organizer/activities', label: '活动管理' },
      { path: '/organizer/activities/new', label: '创建活动' },
      { path: '/organizer/query', label: '智能查询' },
      { path: '/organizer/notifications', label: '站内通知' },
    ]
  }
  return [
    { path: '/admin/reviews', label: '审核中心' },
    { path: '/admin/dictionaries', label: '资源管理' },
    { path: '/admin/stats', label: '统计概览' },
    { path: '/admin/query', label: '智能查询' },
    { path: '/organizer/activities', label: '活动管理' },
    { path: '/admin/notifications', label: '站内通知' },
  ]
})

const roleName = computed(() => {
  if (userStore.user?.role === 'STUDENT') return '学生门户'
  if (userStore.user?.role === 'ORGANIZER') return '组织者工作台'
  if (userStore.user?.role === 'ADMIN') return '管理员控制台'
  return '校园活动'
})

const notificationPath = computed(() => {
  if (userStore.user?.role === 'STUDENT') return '/student/notifications'
  if (userStore.user?.role === 'ORGANIZER') return '/organizer/notifications'
  return '/admin/notifications'
})

function logout() {
  userStore.logout()
  router.push('/login')
}

function goNotifications() {
  router.push(notificationPath.value)
}

async function refreshUnread() {
  if (!userStore.token) return
  const data = await getUnreadCount() as any
  unreadCount.value = Number(data.unreadCount || 0)
}

watch(() => route.fullPath, refreshUnread)
onMounted(refreshUnread)
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.el-aside {
  background: #0f172a;
  border-right: 1px solid #e5e7eb;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #2563eb, #14b8a6);
  border-radius: 8px;
  font-weight: 800;
}

.brand strong,
.brand span {
  display: block;
}

.brand span {
  margin-top: 2px;
  color: #9ca3af;
  font-size: 12px;
}

.el-menu {
  border-right: none;
  background: transparent;
}

:deep(.el-menu-item) {
  margin: 6px 10px;
  color: #cbd5e1;
  border-radius: 8px;
}

:deep(.el-menu-item.is-active) {
  color: #fff;
  background: rgba(37, 99, 235, 0.85);
}

:deep(.el-menu-item:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.el-header {
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-subtitle {
  margin-left: 10px;
  color: #64748b;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notification-badge {
  line-height: 1;
}

.el-main {
  padding: 0;
}
</style>
