<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const props = defineProps<{ content: string }>()

const html = computed(() => {
  const rendered = marked.parse(props.content || '', { async: false }) as string
  return DOMPurify.sanitize(rendered)
})
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>

<style scoped>
.markdown-body {
  line-height: 1.7;
  word-break: break-word;
}

.markdown-body :deep(p) {
  margin: 0 0 0.6em;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(pre) {
  background: #f6f8fa;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}

.markdown-body :deep(code) {
  background: #f6f8fa;
  padding: 1px 4px;
  border-radius: 4px;
  font-size: 0.9em;
}

.markdown-body :deep(pre code) {
  padding: 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  max-width: 100%;
  display: block;
  overflow-x: auto;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 4px 8px;
}

.markdown-body :deep(th) {
  background: #f5f7fa;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 10px;
  border-left: 3px solid #dcdfe6;
  color: #606266;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.4em;
  margin: 0.4em 0;
}
</style>
