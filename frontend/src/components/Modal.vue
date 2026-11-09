<script setup>
import { onMounted, ref } from 'vue'
defineProps({ title: String, wide: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
onMounted(() => dialog.value.showModal())
</script>

<template>
  <dialog ref="dialog" class="modal" :class="{ wide }" :aria-label="title" @cancel.prevent="emit('close')" @click="event => { if (event.target === dialog) emit('close') }">
    <div class="modal-heading"><h2>{{ title }}</h2><button class="icon-button" type="button" aria-label="关闭" @click="emit('close')">×</button></div>
    <slot />
  </dialog>
</template>
