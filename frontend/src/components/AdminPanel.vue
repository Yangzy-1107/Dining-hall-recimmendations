<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api, queryString } from '../api'
import Modal from './Modal.vue'
const props = defineProps({ metadata: Object })
const emit = defineEmits(['changed', 'notify'])
const section = ref('dishes')
const stats = ref({})
const rows = ref([])
const busy = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
const filters = reactive({ keyword: '', canteenId: '' })
const modal = ref(null)
const form = reactive({})
const saving = ref(false)
const uploading = ref(false)
const formError = ref('')
const confirmation = ref(null)
const formCanteen = ref('')
const filteredStalls = computed(() => props.metadata.stalls.filter(item => !formCanteen.value || item.canteenId === Number(formCanteen.value)))
const stallRows = computed(() => props.metadata.stalls.filter(item => (!filters.canteenId || item.canteenId === Number(filters.canteenId)) && item.name.includes(filters.keyword.trim())))
const canteenRows = computed(() => props.metadata.canteens.filter(item => item.name.includes(filters.keyword.trim())))
const sectionName = computed(() => ({ dishes: '菜品管理', canteens: '食堂管理', stalls: '窗口管理' })[section.value])
const modalTitle = computed(() => modal.value ? (modal.value.id ? '编辑' : '新增') + ({ dishes: '菜品', canteens: '食堂', stalls: '窗口' })[modal.value.type] : '')
let listRequest = 0

async function load() {
  const current = ++listRequest
  busy.value = true; error.value = ''
  try {
    const [statistics, result] = await Promise.all([
      api('/admin/statistics'),
      api(`/dishes?${queryString({ ...filters, page: page.value, pageSize: 10 })}`),
    ])
    if (current !== listRequest) return
    stats.value = statistics; rows.value = result.items; total.value = result.total
    if (page.value > Math.max(1, Math.ceil(total.value / 10))) { page.value = Math.max(1, Math.ceil(total.value / 10)); return load() }
  } catch (ex) { if (current === listRequest) error.value = ex.message }
  finally { if (current === listRequest) busy.value = false }
}
function switchSection(value) { section.value = value; filters.keyword = ''; filters.canteenId = ''; page.value = 1; load() }
function search() { page.value = 1; load() }
function openForm(type, row = null) {
  formError.value = ''; modal.value = { type, id: row?.id }
  for (const key of Object.keys(form)) delete form[key]
  if (type === 'dishes') {
    Object.assign(form, row ? { stallId: row.stallId, name: row.name, category: row.category, taste: row.taste, price: row.price, imageUrl: row.imageUrl || '', labelsVerified: row.labelsVerified, ingredients: row.ingredients || '', calories: row.calories ?? '' } : { stallId: '', name: '', category: '待分类', taste: '待核实', price: '', imageUrl: '', labelsVerified: false, ingredients: '', calories: '' })
    formCanteen.value = row?.canteenId || ''
  } else if (type === 'stalls') Object.assign(form, row ? { ...row } : { canteenId: '', name: '', collectedAveragePrice: '', collectedRating: '' })
  else Object.assign(form, { name: row?.name || '' })
}
function closeForm() { if (!saving.value && !uploading.value) modal.value = null }
async function upload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  formError.value = ''
  if (file.size > 5 * 1024 * 1024) { formError.value = '图片不能超过5MB'; event.target.value = ''; return }
  uploading.value = true
  try { const body = new FormData(); body.append('file', file); form.imageUrl = (await api('/admin/images', { method: 'POST', body })).url }
  catch (ex) { formError.value = ex.message }
  finally { uploading.value = false; event.target.value = '' }
}
async function save() {
  saving.value = true; formError.value = ''
  try {
    const body = { ...form }
    for (const key of ['collectedAveragePrice', 'collectedRating']) if (key in body && body[key] === '') body[key] = null
    if ('calories' in body) body.calories = body.calories === '' || body.calories == null ? null : Number(body.calories)
    await api(`/admin/${modal.value.type}${modal.value.id ? '/' + modal.value.id : ''}`, { method: modal.value.id ? 'PUT' : 'POST', body })
    modal.value = null; emit('changed'); emit('notify', '资料已保存'); await load()
  } catch (ex) { formError.value = ex.message }
  finally { saving.value = false }
}
async function remove() {
  saving.value = true; formError.value = ''
  try {
    await api(`/admin/${confirmation.value.type}/${confirmation.value.id}`, { method: 'DELETE' })
    confirmation.value = null; emit('changed'); emit('notify', '删除已完成'); await load()
  } catch (ex) { formError.value = ex.message }
  finally { saving.value = false }
}
function confirmDelete(type, row) { formError.value = ''; confirmation.value = { type, id: row.id, name: row.name } }
function canteenName(id) { return props.metadata.canteens.find(item => item.id === id)?.name || '—' }
function money(value) { return value == null ? '—' : `¥${Number(value).toFixed(2)}` }
onMounted(load)
</script>

<template>
  <section class="admin-heading"><div><span class="eyebrow">CANTEEN MANAGEMENT</span><h1>食堂资料管理</h1><p class="muted">维护准确的菜品信息，让每一次选择都有依据。</p></div><span class="admin-badge">管理员工作台</span></section>
  <div class="stat-grid"><div v-for="item in [{ key: 'canteens', label: '食堂总数' }, { key: 'stalls', label: '窗口总数' }, { key: 'dishes', label: '在售菜品' }, { key: 'reviews', label: '学生评价' }]" :key="item.key" class="stat-card"><span>{{ item.label }}</span><strong>{{ stats[item.key] ?? '—' }}</strong></div></div>
  <div class="admin-layout">
    <aside class="admin-sidebar"><button v-for="(label, key) in { dishes: '菜品管理', canteens: '食堂管理', stalls: '窗口管理' }" :key="key" :class="{ active: section === key }" @click="switchSection(key)"><span>{{ { dishes: '▦', canteens: '⌂', stalls: '▤' }[key] }}</span>{{ label }}<span>›</span></button><div class="sidebar-note">资料维护提示<p>分类与口味分别保存。确认实际情况后，勾选标签已核实。</p></div></aside>
    <section class="admin-content">
      <div class="section-line"><div><h2>{{ sectionName }}</h2><p v-if="section === 'dishes'" class="small muted">{{ stats.pendingLabels ?? 0 }} 道菜品的标签待核实</p></div><button class="button primary" @click="openForm(section)">＋ 新增{{ { dishes: '菜品', canteens: '食堂', stalls: '窗口' }[section] }}</button></div>
      <form class="admin-search" @submit.prevent="search"><input v-model="filters.keyword" :placeholder="`搜索${section === 'canteens' ? '食堂' : section === 'stalls' ? '窗口' : '菜品或窗口'}名称`" aria-label="名称查询" maxlength="50" /><select v-if="section !== 'canteens'" v-model="filters.canteenId" aria-label="食堂筛选"><option value="">全部食堂</option><option v-for="item in metadata.canteens" :key="item.id" :value="item.id">{{ item.name }}</option></select><button class="button outline">查询</button></form>
      <div v-if="error" class="error-message" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></div>
      <p v-else-if="busy" class="empty-inline">正在加载资料…</p>
      <div v-else class="table-scroll">
        <table v-if="section === 'dishes'" class="data-table"><thead><tr><th>菜品</th><th>所属食堂 / 窗口</th><th>分类 / 口味</th><th>价格</th><th>学生评分</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><div class="table-dish"><img :src="row.imageUrl || '/dish-placeholder.svg'" alt="" @error="event => event.target.src = '/dish-placeholder.svg'" /><strong>{{ row.name }}</strong></div></td><td>{{ row.canteenName }}<small class="block muted">{{ row.stallName }}</small></td><td>{{ row.category }} / {{ row.taste }}<small v-if="!row.labelsVerified" class="block pending-label">待核实</small></td><td class="nowrap">{{ money(row.price) }}</td><td>{{ row.averageScore == null ? '暂无' : Number(row.averageScore).toFixed(1) }}<small class="block muted">{{ row.reviewCount }} 人</small></td><td><div class="actions"><button class="text-button" @click="openForm('dishes', row)">编辑</button><button class="text-button danger" @click="confirmDelete('dishes', row)">删除</button></div></td></tr><tr v-if="!rows.length"><td colspan="6" class="empty-inline">暂无符合条件的菜品</td></tr></tbody></table>
        <table v-else-if="section === 'canteens'" class="data-table"><thead><tr><th>编号</th><th>食堂名称</th><th>窗口数量</th><th>操作</th></tr></thead><tbody><tr v-for="row in canteenRows" :key="row.id"><td>{{ row.id }}</td><td><strong>{{ row.name }}</strong></td><td>{{ metadata.stalls.filter(item => item.canteenId === row.id).length }}</td><td><div class="actions"><button class="text-button" @click="openForm('canteens', row)">编辑</button><button class="text-button danger" @click="confirmDelete('canteens', row)">删除</button></div></td></tr><tr v-if="!canteenRows.length"><td colspan="4" class="empty-inline">暂无符合条件的食堂</td></tr></tbody></table>
        <table v-else class="data-table"><thead><tr><th>窗口名称</th><th>所属食堂</th><th>采集人均消费</th><th>窗口采集评分</th><th>操作</th></tr></thead><tbody><tr v-for="row in stallRows" :key="row.id"><td><strong>{{ row.name }}</strong></td><td>{{ canteenName(row.canteenId) }}</td><td>{{ money(row.collectedAveragePrice) }}</td><td>{{ row.collectedRating ?? '—' }}</td><td><div class="actions"><button class="text-button" @click="openForm('stalls', row)">编辑</button><button class="text-button danger" @click="confirmDelete('stalls', row)">删除</button></div></td></tr><tr v-if="!stallRows.length"><td colspan="5" class="empty-inline">暂无符合条件的窗口</td></tr></tbody></table>
      </div>
      <nav v-if="section === 'dishes' && total > 10" class="pagination" aria-label="管理列表翻页"><button class="button outline" :disabled="page <= 1 || busy" @click="page--; load()">上一页</button><span>{{ page }} / {{ Math.ceil(total / 10) }}</span><button class="button outline" :disabled="page * 10 >= total || busy" @click="page++; load()">下一页</button></nav>
    </section>
  </div>

  <Modal v-if="modal" :title="modalTitle" :wide="modal.type === 'dishes'" @close="closeForm">
    <form class="form-stack" @submit.prevent="save">
      <label>名称<input v-model="form.name" required maxlength="50" placeholder="请输入名称" /></label>
      <template v-if="modal.type === 'dishes'">
        <div class="form-grid"><label>所属食堂<select v-model="formCanteen" required @change="form.stallId = ''"><option value="" disabled>请选择食堂</option><option v-for="item in metadata.canteens" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><label>所属窗口<select v-model="form.stallId" required><option value="" disabled>请选择窗口</option><option v-for="item in filteredStalls" :key="item.id" :value="item.id">{{ item.name }}</option></select></label></div>
        <div class="form-grid"><label>菜品分类<select v-model="form.category"><option v-for="item in metadata.categories" :key="item">{{ item }}</option></select></label><label>口味<select v-model="form.taste"><option v-for="item in metadata.tastes" :key="item">{{ item }}</option></select></label></div>
        <label>价格（元）<input v-model="form.price" required type="number" min="0" max="999999.99" step="0.01" placeholder="请输入菜品实际售价" /></label>
        <label>每份配料及份量<textarea v-model="form.ingredients" :required="form.category === '单点食材'" maxlength="500" rows="3" placeholder="例如 鸡胸肉150克、西兰花100克" /></label>
        <label>每份热量（千卡）<input v-model="form.calories" :required="form.category === '单点食材'" type="number" min="1" max="5000" step="1" placeholder="请输入每份估算热量" /></label>
        <p class="small muted">{{ form.category === '单点食材' ? '单点食材须填写每份配料及份量和热量。' : '配料和热量需同时填写，无数据时两项留空。' }}热量用于估算，烹饪方式和份量会影响实际数值。</p>
        <label class="checkbox-label"><input v-model="form.labelsVerified" type="checkbox" />分类和口味已核实</label>
        <div class="image-upload"><img :src="form.imageUrl || '/dish-placeholder.svg'" alt="菜品图片" /><div><label class="button outline upload-button">{{ uploading ? '正在上传…' : '选择菜品图片' }}<input type="file" accept="image/jpeg,image/png" :disabled="uploading || saving" @change="upload" /></label><p class="small muted">JPG / PNG，单张不超过5MB</p><button v-if="form.imageUrl" class="text-button danger" type="button" :disabled="uploading" @click="form.imageUrl = ''">移除图片</button></div></div>
      </template>
      <template v-if="modal.type === 'stalls'">
        <label>所属食堂<select v-model="form.canteenId" required><option value="" disabled>请选择食堂</option><option v-for="item in metadata.canteens" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <div class="form-grid"><label>采集人均消费（元）<input v-model="form.collectedAveragePrice" type="number" min="0" max="999999.99" step="0.01" placeholder="选填" /></label><label>窗口采集评分<input v-model="form.collectedRating" type="number" min="0" max="5" step="0.1" placeholder="选填，0～5分" /></label></div><p class="small muted">上述数据来自窗口采集资料，与学生对菜品的评分分别保存。</p>
      </template>
      <p v-if="formError" class="error-message" role="alert">{{ formError }}</p>
      <div class="modal-actions"><button type="button" class="button outline" :disabled="saving || uploading" @click="closeForm">取消</button><button class="button primary" :disabled="saving || uploading">{{ saving ? '正在保存…' : '保存资料' }}</button></div>
    </form>
  </Modal>
  <Modal v-if="confirmation" title="确认删除" @close="!saving && (confirmation = null)"><p>确定删除“{{ confirmation.name }}”吗？</p><p class="muted">{{ confirmation.type === 'dishes' ? '删除后，该菜品不再参与浏览和推荐，已有评价将保留。' : '存在关联资料时，系统会阻止删除。' }}</p><p v-if="formError" class="error-message" role="alert">{{ formError }}</p><div class="modal-actions"><button class="button outline" :disabled="saving" @click="confirmation = null">取消</button><button class="button danger-button" :disabled="saving" @click="remove">{{ saving ? '正在处理…' : '确认删除' }}</button></div></Modal>
</template>
