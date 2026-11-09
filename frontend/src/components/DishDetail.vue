<script setup>
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import Modal from './Modal.vue'
const props = defineProps({ dish: Object, user: Object, random: Boolean })
const emit = defineEmits(['close', 'login', 'updated'])
const data = ref(props.dish)
const score = ref(5)
const comment = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const ownReview = computed(() => data.value.reviews.find(item => item.userId === props.user?.id))
watch([() => props.dish, () => props.user], () => { data.value = props.dish; fillReview() }, { immediate: true })
function fillReview() { score.value = ownReview.value?.score ?? 5; comment.value = ownReview.value?.comment ?? '' }
function price(value) { return Number(value).toFixed(2).replace(/\.00$/, '') }
function date(value) { return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }
async function save() {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    data.value = await api(`/dishes/${data.value.id}/review`, { method: 'PUT', body: { score: score.value, comment: comment.value } })
    notice.value = '评价已保存'
    emit('updated', data.value)
  } catch (ex) { error.value = ex.message }
  finally { busy.value = false }
}
</script>

<template>
  <Modal :title="random ? '今天就吃这道' : '菜品详情'" wide @close="!busy && emit('close')">
    <div class="detail-top">
      <img class="detail-image" :src="data.imageUrl || '/dish-placeholder.svg'" :alt="data.name" @error="event => event.target.src = '/dish-placeholder.svg'" />
      <div class="detail-summary">
        <span class="eyebrow">{{ data.canteenName }} · {{ data.stallName }}</span>
        <h2>{{ data.name }}</h2>
        <div class="tag-row"><span class="tag">{{ data.category }}</span><span class="tag neutral">{{ data.taste }}</span></div>
        <div v-if="data.calories != null" class="nutrition-detail"><strong>{{ data.calories }} 千卡 / 份</strong><p>{{ data.ingredients }}</p><span class="small muted">每份热量为估算值</span></div>
        <p v-if="!data.labelsVerified" class="small muted">分类按菜名初分，口味待窗口核实。</p>
        <div class="detail-price">¥{{ price(data.price) }}</div>
        <p class="rating-line"><span class="star">★</span> {{ data.averageScore == null ? '暂无评分' : Number(data.averageScore).toFixed(1) + ' 分' }}<span class="muted"> · {{ data.reviewCount }} 人评价</span></p>
        <div class="source-info">窗口采集信息：人均 ¥{{ price(data.collectedAveragePrice ?? 0) }} · 窗口评分 {{ data.collectedRating ?? '暂无' }}<br /><span class="small muted">窗口采集评分与本菜品的学生评分分别记录。</span></div>
      </div>
    </div>
    <section class="review-section">
      <div class="section-line"><h3>我的用餐评价</h3><span class="small muted">{{ ownReview ? '再次提交将更新你的原评价' : '分享口味与就餐体验' }}</span></div>
      <form v-if="user?.role === 'STUDENT'" class="form-stack" @submit.prevent="save">
        <div class="rating-input" role="group" aria-label="选择评分">
          <button v-for="n in 5" :key="n" type="button" :class="{ active: n <= score }" :aria-label="`${n}分`" :aria-pressed="score === n" @click="score = n">★</button><span>{{ score }} 分</span>
        </div>
        <label><span class="sr-only">评价留言</span><textarea v-model="comment" maxlength="200" rows="3" placeholder="这道菜怎么样？可以聊聊口味、分量或搭配（选填）" /></label>
        <div class="review-footer"><span class="small muted">{{ comment.length }}/200</span><button class="button primary" :disabled="busy">{{ busy ? '正在保存…' : ownReview ? '更新评价' : '发布评价' }}</button></div>
      </form>
      <p v-else-if="!user" class="login-hint"><button class="text-button" @click="emit('login')">登录账号</button> 后，可以为这道菜评分留言。</p>
      <p v-else class="muted">管理员可查看学生评价，评分由学生账号提交。</p>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p><p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    </section>
    <section class="review-section">
      <div class="section-line"><h3>大家的留言 <span class="count">{{ data.reviewCount }}</span></h3><span class="small muted">最近更新优先</span></div>
      <div v-if="!data.reviews.length" class="empty-inline">还没有评价，来分享第一份用餐体验吧。</div>
      <article v-for="item in data.reviews" :key="item.id" class="review-item">
        <span class="avatar">{{ item.displayName.slice(0, 1) }}</span><div class="review-body"><div class="review-title"><strong>{{ item.displayName }}</strong><span class="star small">{{ '★'.repeat(item.score) }}<span class="muted">{{ '☆'.repeat(5 - item.score) }}</span></span></div><p>{{ item.comment || '该同学只留下了评分' }}</p><time class="small muted">{{ date(item.updatedAt) }}</time></div>
      </article>
    </section>
  </Modal>
</template>
