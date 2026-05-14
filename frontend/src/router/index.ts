import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import LoginView from '../views/LoginView.vue'
import RoleLayout from '../views/RoleLayout.vue'
import StudentActivities from '../views/student/StudentActivities.vue'
import ActivityDetail from '../views/student/ActivityDetail.vue'
import MyRegistrations from '../views/student/MyRegistrations.vue'
import OrganizerActivities from '../views/organizer/OrganizerActivities.vue'
import ActivityForm from '../views/organizer/ActivityForm.vue'
import ActivityRegistrations from '../views/organizer/ActivityRegistrations.vue'
import ActivityFeedback from '../views/organizer/ActivityFeedback.vue'
import AdminReviews from '../views/admin/AdminReviews.vue'
import AdminDictionaries from '../views/admin/AdminDictionaries.vue'
import AdminStats from '../views/admin/AdminStats.vue'
import NotificationsView from '../views/NotificationsView.vue'
import NaturalQueryView from '../views/NaturalQueryView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    {
      path: '/student',
      component: RoleLayout,
      meta: { roles: ['STUDENT'] },
      children: [
        { path: 'activities', component: StudentActivities },
        { path: 'activities/:id', component: ActivityDetail },
        { path: 'my-registrations', component: MyRegistrations },
        { path: 'query', component: NaturalQueryView },
        { path: 'notifications', component: NotificationsView },
      ],
    },
    {
      path: '/organizer',
      component: RoleLayout,
      meta: { roles: ['ORGANIZER', 'ADMIN'] },
      children: [
        { path: 'activities', component: OrganizerActivities },
        { path: 'activities/new', component: ActivityForm },
        { path: 'activities/:id/edit', component: ActivityForm },
        { path: 'activities/:id/registrations', component: ActivityRegistrations },
        { path: 'activities/:id/feedback', component: ActivityFeedback },
        { path: 'query', component: NaturalQueryView },
        { path: 'notifications', component: NotificationsView },
      ],
    },
    {
      path: '/admin',
      component: RoleLayout,
      meta: { roles: ['ADMIN'] },
      children: [
        { path: 'reviews', component: AdminReviews },
        { path: 'dictionaries', component: AdminDictionaries },
        { path: 'stats', component: AdminStats },
        { path: 'query', component: NaturalQueryView },
        { path: 'notifications', component: NotificationsView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    return '/login'
  }
  const roles = to.matched.flatMap((item) => (item.meta.roles as string[] | undefined) || [])
  if (roles.length && (!userStore.user || !roles.includes(userStore.user.role))) {
    if (userStore.user?.role === 'STUDENT') return '/student/activities'
    if (userStore.user?.role === 'ORGANIZER') return '/organizer/activities'
    if (userStore.user?.role === 'ADMIN') return '/admin/reviews'
    return '/login'
  }
  return true
})

export default router
