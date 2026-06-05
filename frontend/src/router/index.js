import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/documents'
  },
  {
    path: '/documents',
    name: 'Documents',
    component: () => import('@/views/DocumentList.vue')
  },
  {
    path: '/documents/:id',
    name: 'DocumentDetail',
    component: () => import('@/views/DocumentDetail.vue')
  },
  {
    path: '/annotator/:id',
    name: 'Annotator',
    component: () => import('@/views/Annotator.vue')
  },
  {
    path: '/batch-tasks',
    name: 'BatchTasks',
    component: () => import('@/views/BatchTasks.vue')
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('@/views/ModelManagement.vue')
  },
  {
    path: '/inference',
    name: 'Inference',
    component: () => import('@/views/InferenceDemo.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
