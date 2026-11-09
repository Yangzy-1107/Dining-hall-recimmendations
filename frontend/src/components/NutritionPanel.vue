<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, queryString } from '../api'

const emit = defineEmits(['open-dish'])
const target = ref(1000)
const ingredient = ref('')
const response = ref(null)
const busy = ref(false)
const error = ref('')
const singleItems = ref([])
const quantities = reactive({})
const singleItemsBusy = ref(true)
const singleItemsError = ref('')
const selectedItems = computed(() => singleItems.value
  .filter(item => quantities[item.id] > 0)
  .map(item => ({ ...item, quantity: quantities[item.id] })))
const totalPortions = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0))
const totalCalories = computed(() => selectedItems.value.reduce((sum, item) => sum + item.calories * item.quantity, 0))
const totalPriceCents = computed(() => selectedItems.value
  .reduce((sum, item) => sum + Math.round(Number(item.price) * 100) * item.quantity, 0))

async function loadSingleItems() {
  singleItemsBusy.value = true
  singleItemsError.value = ''
  try {
    singleItems.value = await api('/nutrition/single-items')
  } catch (ex) {
    singleItemsError.value = ex.message
  } finally {
    singleItemsBusy.value = false
  }
}

function changeQuantity(id, change) {
  quantities[id] = Math.max(0, Math.min(99, (quantities[id] || 0) + change))
}

function clearSelection() {
  for (const id of Object.keys(quantities)) delete quantities[id]
}

async function recommend() {
  busy.value = true
  error.value = ''
  try {
    response.value = await api(`/nutrition/recommendations?${queryString({ calories: target.value, ingredient: ingredient.value.trim() })}`)
  } catch (ex) {
    error.value = ex.message
    response.value = null
  } finally { busy.value = false }
}

onMounted(loadSingleItems)
</script>

<template>
  <section class="nutrition-page">
    <div class="nutrition-intro"><div><span class="eyebrow">EAT WELL, FEEL GOOD</span><h1>按你的目标搭配一餐<span>让每一份选择更清晰</span></h1><p>选择低价单点自由组合，也可以输入目标热量生成推荐搭配。</p></div><span class="nutrition-symbol" aria-hidden="true">✳</span></div>
    <section class="single-items-panel" aria-labelledby="single-items-heading">
      <div class="section-line">
        <div><span class="eyebrow">BUILD YOUR OWN MEAL</span><h2 id="single-items-heading">单点自由搭配</h2></div>
        <button type="button" class="text-button" :disabled="!selectedItems.length" @click="clearSelection">清空选择</button>
      </div>
      <p class="single-items-note">按每份价格选择食材，使用加减按钮调整份数，每项最多99份。</p>
      <p v-if="singleItemsBusy" class="empty-inline" role="status">正在加载单点食材…</p>
      <div v-else-if="singleItemsError" class="single-items-error">
        <p class="error-message" role="alert">{{ singleItemsError }}</p>
        <button type="button" class="button outline" @click="loadSingleItems">重新加载单点</button>
      </div>
      <p v-else-if="!singleItems.length" class="empty-inline">暂无可搭配的单点食材。</p>
      <div v-else class="single-items-grid">
        <article v-for="item in singleItems" :key="item.id" class="single-item-card" :class="{ selected: quantities[item.id] > 0 }">
          <button type="button" class="single-item-name" @click="emit('open-dish', item.id)">{{ item.name }} <span aria-hidden="true">↗</span></button>
          <p class="single-item-portion">{{ item.ingredients }}</p>
          <p class="single-item-location">{{ item.canteenName }} · {{ item.stallName }}</p>
          <div class="single-item-facts"><strong>¥{{ Number(item.price).toFixed(2) }}<small>/份</small></strong><span>{{ item.calories }}千卡/份</span></div>
          <div class="single-item-quantity">
            <span>份数</span>
            <div class="quantity-controls">
              <button type="button" :aria-label="`减少${item.name}份数`" :disabled="!quantities[item.id]" @click="changeQuantity(item.id, -1)">−</button>
              <output :aria-label="`${item.name}份数`">{{ quantities[item.id] || 0 }}</output>
              <button type="button" :aria-label="`增加${item.name}份数`" :disabled="quantities[item.id] >= 99" @click="changeQuantity(item.id, 1)">＋</button>
            </div>
          </div>
        </article>
      </div>
      <div class="single-items-summary" aria-live="polite" aria-atomic="true">
        <p v-if="!selectedItems.length">选择食材后，可查看这餐的价格和热量。</p>
        <template v-else>
          <ul class="selected-items-list"><li v-for="item in selectedItems" :key="item.id">{{ item.name }}×{{ item.quantity }}份</li></ul>
          <div class="single-items-totals">
            <span>已选{{ selectedItems.length }}种，共{{ totalPortions }}份</span>
            <strong>总价¥{{ (totalPriceCents / 100).toFixed(2) }}</strong>
            <strong>总热量{{ totalCalories }}千卡</strong>
          </div>
        </template>
      </div>
      <p class="nutrition-note">自由搭配按所选份数计算，每份热量为估算值。</p>
    </section>
    <form class="nutrition-form" @submit.prevent="recommend">
      <div class="section-line"><div><span class="eyebrow">YOUR MEAL PLAN</span><h2>设置搭配条件</h2></div><span class="nutrition-form-badge">每份 · 估算热量</span></div>
      <div class="nutrition-fields"><label>目标热量（千卡）<input v-model.number="target" type="number" required min="1" max="5000" step="1" placeholder="例如 1000" /></label><label>想吃的食材（选填）<input v-model="ingredient" maxlength="50" placeholder="例如 鸡胸肉" /></label><button class="button primary" :disabled="busy">{{ busy ? '正在搭配…' : '生成菜品搭配' }} <span>↗</span></button></div>
      <p class="nutrition-note">每道菜按一份计算，最多选择 5 道；总热量不超过目标，并优先推荐接近目标的组合。食材名称按配料文字匹配。</p>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    </form>
    <section class="nutrition-results" aria-live="polite">
      <div class="section-line"><div><span class="eyebrow">MEAL COMBINATIONS</span><h2>推荐搭配</h2></div><span v-if="response" class="small muted">目标 {{ response.targetCalories }} 千卡<span v-if="response.ingredient"> · 包含 {{ response.ingredient }}</span></span></div>
      <div v-if="!response && !busy" class="nutrition-empty"><span>◌</span><p>设置条件后，即可查看菜品搭配。</p></div>
      <div v-else-if="busy" class="nutrition-empty"><p>正在寻找合适的菜品组合…</p></div>
      <div v-else-if="!response.results.length" class="nutrition-empty"><span>⌕</span><p>没有符合条件的搭配，可以调整目标热量或食材名称。</p></div>
      <div v-else class="combination-list">
        <article v-for="(result, index) in response.results" :key="index" class="combination-card">
          <div class="combination-header"><div><span class="combination-number">0{{ index + 1 }}</span><strong>推荐搭配 {{ index + 1 }}</strong></div><div><strong class="combination-energy">{{ result.totalCalories }} <small>千卡</small></strong><span class="small muted">距离目标还差 {{ result.remainingCalories }} 千卡</span></div></div>
          <div class="combination-dishes"><button v-for="dish in result.dishes" :key="dish.id" type="button" class="combination-dish" @click="emit('open-dish', dish.id)"><img :src="dish.imageUrl || '/dish-placeholder.svg'" :alt="dish.name" @error="event => event.target.src = '/dish-placeholder.svg'" /><span><strong>{{ dish.name }}</strong><small>{{ dish.canteenName }} · {{ dish.stallName }}</small><small>{{ dish.ingredients }}</small><b>{{ dish.calories }} 千卡 / 份</b></span><span class="combination-arrow">↗</span></button></div>
          <div class="combination-footer"><span>共 {{ result.dishes.length }} 道菜 · ¥{{ Number(result.totalPrice).toFixed(2) }}</span><span>每份热量为估算值</span></div>
        </article>
      </div>
      <p class="nutrition-disclaimer">热量根据所列配料及份量估算；实际数值会随食材、份量和烹饪方式变化。</p>
    </section>
  </section>
</template>
