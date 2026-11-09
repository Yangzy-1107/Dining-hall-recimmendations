<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, queryString } from './api'
import AuthDialog from './components/AuthDialog.vue'
import DishDetail from './components/DishDetail.vue'
import AdminPanel from './components/AdminPanel.vue'
import NutritionPanel from './components/NutritionPanel.vue'

const user = ref(null)
const view = ref('browse')
const authOpen = ref(false)
const metadata = ref({ canteens: [], stalls: [], categories: [], tastes: [] })
const filters = reactive({ keyword: '', canteenId: '', stallId: '', category: '', taste: '', minPrice: '', maxPrice: '', sort: 'default' })
const applied = ref({ ...filters })
const dishes = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const busy = ref(false)
const randomBusy = ref(false)
const error = ref('')
const toast = ref('')
const detail = ref(null)
const randomResult = ref(false)
let requestId = 0
let toastTimer
const stalls = computed(() => metadata.value.stalls.filter(item => !filters.canteenId || item.canteenId === Number(filters.canteenId)))
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const selectedCanteen = computed(() => metadata.value.canteens.find(item => item.id === Number(applied.value.canteenId))?.name || '全部食堂')

function notify(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.value = '', 4200) }
async function refreshMetadata() { metadata.value = await api('/metadata') }
async function loadList() {
  const id = ++requestId
  busy.value = true; error.value = ''
  try {
    const result = await api(`/dishes?${queryString({ ...applied.value, page: page.value, pageSize })}`)
    if (id !== requestId) return false
    dishes.value = result.items; total.value = result.total
    if (page.value > pages.value) { page.value = pages.value; return loadList() }
    return true
  } catch (ex) { if (id === requestId) error.value = ex.message; return false }
  finally { if (id === requestId) busy.value = false }
}
function apply() { applied.value = { ...filters }; page.value = 1; return loadList() }
function reset() { Object.assign(filters, { keyword: '', canteenId: '', stallId: '', category: '', taste: '', minPrice: '', maxPrice: '', sort: 'default' }); apply() }
function chooseCanteen(id) { filters.canteenId = id; filters.stallId = ''; apply() }
async function randomDish() {
  randomBusy.value = true
  try {
    applied.value = { ...filters }; page.value = 1
    if (!await loadList()) return
    detail.value = await api(`/dishes/random?${queryString(applied.value)}`)
    randomResult.value = true
  } catch (ex) { notify(ex.message) }
  finally { randomBusy.value = false }
}
async function openDish(id) {
  try { const result = await api(`/dishes/${id}`); randomResult.value = false; detail.value = result }
  catch (ex) { notify(ex.message) }
}
async function logout() {
  try { await api('/auth/logout', { method: 'POST' }); user.value = null; view.value = 'browse'; notify('已退出登录') }
  catch (ex) { notify(ex.message) }
}
function signedIn(value) { user.value = value; authOpen.value = false; notify(`欢迎，${value.displayName}`) }
async function dataChanged() {
  try { await refreshMetadata(); await loadList() } catch (ex) { notify(ex.message) }
}
async function initialize() {
  error.value = ''
  try { const [meta, identity] = await Promise.all([api('/metadata'), api('/auth/me')]); metadata.value = meta; user.value = identity; await loadList() }
  catch (ex) { error.value = ex.message }
}
onMounted(initialize)
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
      <a class="brand" href="#" @click.prevent="view = 'browse'"><img src="/favicon.svg" alt="" /><span>校园食堂<small>CAMPUS CANTEEN</small></span></a>
      <nav class="main-nav" aria-label="主导航"><button :class="{ active: view === 'browse' }" @click="view = 'browse'">发现菜品</button><button :class="{ active: view === 'nutrition' }" @click="view = 'nutrition'">热量搭配</button><button v-if="user?.role === 'ADMIN'" :class="{ active: view === 'admin' }" @click="view = 'admin'">管理后台</button></nav>
      <div class="account"><template v-if="user"><span class="account-name">{{ user.displayName }}</span><span class="role-label">{{ user.role === 'ADMIN' ? '管理员' : '学生' }}</span><button class="text-button" @click="logout">退出</button></template><button v-else class="button outline small-button" @click="authOpen = true">登录 / 注册 <span>↗</span></button></div>
    </div>
  </header>

  <main v-if="view === 'browse'" class="page-shell">
    <section class="hero">
      <div class="hero-copy"><span class="eyebrow">每一餐，都有新选择</span><h1>今天吃什么<span>从这里找到答案。</span></h1><p>按口味挑一挑，按预算选一选。<br />也可以，把今天这一餐交给一点惊喜。</p><button class="button hero-button" :disabled="randomBusy" @click="randomDish"><span class="dice">⚄</span>{{ randomBusy ? '正在挑选…' : '帮我选一道' }}<span>↗</span></button></div>
      <div class="hero-art" aria-hidden="true"><div class="orb orb-one"></div><div class="orb orb-two"></div><div class="food-stamp stamp-one">一碗热乎，刚刚好</div><div class="plate"><div class="rice"></div><div class="leaf leaf-one"></div><div class="leaf leaf-two"></div><div class="leaf leaf-three"></div><div class="food-piece piece-one"></div><div class="food-piece piece-two"></div><div class="food-piece piece-three"></div><div class="sesame"></div></div><div class="chopstick first"></div><div class="chopstick second"></div><div class="food-stamp stamp-two">好好吃饭 <span>✳</span></div></div>
      <div class="hero-index"><strong>{{ String(metadata.canteens.length).padStart(2, '0') }}</strong><span>个食堂<br />等你探索</span></div>
    </section>

    <section class="catalog-section" aria-label="菜品筛选与列表">
      <div class="section-line catalog-heading"><div><span class="eyebrow">FIND YOUR NEXT MEAL</span><h2>逛逛校园食堂 <span class="heading-dot">·</span></h2></div><span class="muted small">好吃的选择，就在身边</span></div>
      <div class="canteen-tabs" role="group" aria-label="食堂选择"><button :class="{ active: filters.canteenId === '' }" @click="chooseCanteen('')">全部食堂</button><button v-for="canteen in metadata.canteens" :key="canteen.id" :class="{ active: filters.canteenId === canteen.id }" @click="chooseCanteen(canteen.id)">{{ canteen.name }}</button></div>
      <form class="filter-panel" @submit.prevent="apply">
        <div class="search-row"><label class="search-field"><span aria-hidden="true">⌕</span><input v-model="filters.keyword" aria-label="搜索菜品或窗口" maxlength="50" placeholder="搜索想吃的菜品或窗口…" /></label><button class="button primary">查找菜品</button><button type="button" class="button random-button" :disabled="randomBusy" @click="randomDish">⚄ 随机推荐</button></div>
        <div class="filter-row">
          <label>菜品分类<select v-model="filters.category"><option value="">全部分类</option><option v-for="item in metadata.categories" :key="item">{{ item }}</option></select></label>
          <label>口味偏好<select v-model="filters.taste"><option value="">全部口味</option><option v-for="item in metadata.tastes" :key="item">{{ item }}</option></select></label>
          <label>所属窗口<select v-model="filters.stallId"><option value="">全部窗口</option><option v-for="item in stalls" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
          <fieldset class="budget"><legend>预算范围</legend><div><input v-model="filters.minPrice" aria-label="最低价格" type="number" min="0" max="999999.99" step="0.01" placeholder="最低 ¥" /><span>—</span><input v-model="filters.maxPrice" aria-label="最高价格" type="number" min="0" max="999999.99" step="0.01" placeholder="最高 ¥" /></div></fieldset>
          <button type="button" class="text-button reset-button" @click="reset">重置条件 ↻</button>
        </div>
      </form>
      <div class="result-heading"><div><strong>{{ selectedCanteen }}</strong><span class="muted"> · 共 {{ total }} 道菜品</span></div><label class="sort-label">排序<select v-model="filters.sort" aria-label="排序方式" @change="apply"><option value="default">最近录入</option><option value="priceAsc">价格从低到高</option><option value="priceDesc">价格从高到低</option><option value="rating">学生评分优先</option></select></label></div>
      <div v-if="error" class="state-panel"><h3>菜品暂时未能加载</h3><p class="muted">{{ error }}</p><button class="button outline" @click="metadata.canteens.length ? loadList() : initialize()">重新加载</button></div>
      <div v-else-if="busy" class="dish-grid" aria-label="正在加载"><div v-for="n in 6" :key="n" class="dish-skeleton"><div></div><p></p><p></p></div></div>
      <div v-else-if="!dishes.length" class="state-panel"><span class="empty-symbol">⌕</span><h3>这次没有找到合适的菜品</h3><p class="muted">调整口味、窗口或预算，再找找看。</p><button class="button outline" @click="reset">清除筛选条件</button></div>
      <div v-else class="dish-grid">
        <button v-for="dish in dishes" :key="dish.id" class="dish-card" @click="openDish(dish.id)">
          <div class="card-image-wrap"><img :src="dish.imageUrl || '/dish-placeholder.svg'" :alt="dish.name" loading="lazy" @error="event => event.target.src = '/dish-placeholder.svg'" /><span class="canteen-chip">{{ dish.canteenName }}</span></div>
          <div class="card-content"><div class="card-topline"><span class="muted small">{{ dish.stallName }}</span><span class="card-rating"><span class="star">★</span> {{ dish.averageScore == null ? '暂无评分' : Number(dish.averageScore).toFixed(1) }}</span></div><h3>{{ dish.name }}</h3><div class="tag-row"><span class="tag">{{ dish.category }}</span><span v-if="dish.taste !== '待核实'" class="tag neutral">{{ dish.taste }}</span><span v-if="dish.calories != null" class="tag neutral">{{ dish.calories }} 千卡 / 份</span></div><div class="card-bottom"><span class="price"><small>¥</small>{{ Number(dish.price).toFixed(2).replace(/\.00$/, '') }}</span><span class="card-link">看看详情 ↗</span></div></div>
        </button>
      </div>
      <nav v-if="!error && pages > 1" class="pagination" aria-label="菜品翻页"><button class="button outline" :disabled="page <= 1 || busy" @click="page--; loadList()">← 上一页</button><span>{{ page }} / {{ pages }}</span><button class="button outline" :disabled="page >= pages || busy" @click="page++; loadList()">下一页 →</button></nav>
      <p class="data-note">菜品资料由管理员维护，分类和口味以实际情况为准；学生评分根据实际评价计算。</p>
    </section>
  </main>
  <main v-else-if="view === 'nutrition'" class="page-shell"><NutritionPanel @open-dish="openDish" /></main>
  <main v-else class="page-shell"><AdminPanel :metadata="metadata" @changed="dataChanged" @notify="notify" /></main>
  <footer class="site-footer"><div><span class="footer-brand">校园食堂</span><span>认真吃饭，认真生活。</span></div><span>发现好味道 · 分享好选择</span></footer>
  <AuthDialog v-if="authOpen" @close="authOpen = false" @signed-in="signedIn" />
  <DishDetail v-if="detail" :dish="detail" :user="user" :random="randomResult" @close="detail = null" @login="authOpen = true" @updated="value => { detail = value; loadList() }" />
  <div v-if="toast" class="toast" role="status">{{ toast }}</div>
</template>
