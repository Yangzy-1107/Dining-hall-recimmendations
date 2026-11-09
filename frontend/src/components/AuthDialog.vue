<script setup>
import { reactive, ref } from 'vue'
import { api } from '../api'
import Modal from './Modal.vue'
const emit = defineEmits(['close', 'signed-in'])
const register = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const form = reactive({ username: '', password: '', displayName: '' })

async function submit() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (register.value) {
      await api('/auth/register', { method: 'POST', body: form })
      register.value = false
      notice.value = '账号已创建，请登录'
      form.password = ''
    } else {
      const user = await api('/auth/login', { method: 'POST', body: { username: form.username, password: form.password } })
      emit('signed-in', user)
    }
  } catch (ex) { error.value = ex.message }
  finally { busy.value = false }
}
</script>

<template>
  <Modal :title="register ? '创建学生账号' : '欢迎回来'" @close="!busy && emit('close')">
    <p class="muted">{{ register ? '记录你的用餐体验，分享值得一试的菜品。' : '登录后可以评分留言，管理员可进入资料管理。' }}</p>
    <form class="form-stack" @submit.prevent="submit">
      <label>账号<input v-model="form.username" autocomplete="username" required maxlength="40" placeholder="请输入账号" /></label>
      <label v-if="register">昵称<input v-model="form.displayName" autocomplete="nickname" required maxlength="30" placeholder="留言时显示的昵称" /></label>
      <label>密码<input v-model="form.password" type="password" :autocomplete="register ? 'new-password' : 'current-password'" :minlength="register ? 8 : 1" :maxlength="register ? 30 : 72" required :placeholder="register ? '8～30位密码' : '请输入密码'" /></label>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
      <button class="button primary full" :disabled="busy">{{ busy ? '正在处理…' : register ? '创建账号' : '登录' }}</button>
    </form>
    <button class="text-button full auth-switch" :disabled="busy" @click="register = !register; error = ''; notice = ''">{{ register ? '已有账号，返回登录' : '还没有账号？创建学生账号' }}</button>
  </Modal>
</template>
