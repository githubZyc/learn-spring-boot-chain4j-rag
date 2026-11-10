<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import DocumentUpload from './components/DocumentUpload.vue'
import DocumentTable from './components/DocumentTable.vue'
import ChatPanel from './components/ChatPanel.vue'
import { useDocuments } from './composables/useDocuments'
import { useChat } from './composables/useChat'

const { documents, loading, uploading, busyId, load, upload, remove, reindex } = useDocuments()
const { messages, streaming, error, send, stop, newConversation } = useChat()

const activeTab = ref<'docs' | 'chat'>('docs')

onMounted(async () => {
  try {
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载文档列表失败')
  }
})

async function handleUpload(file: File) {
  try {
    const item = await upload(file)
    ElMessage.success(`「${item.fileName}」已提交，正在后台解析入库…`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  }
}

async function handleDelete(id: string) {
  try {
    await remove(id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function handleReindex(id: string) {
  try {
    const item = await reindex(id)
    ElMessage.success(`「${item.fileName}」已提交重建，请稍候查看状态`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重建失败')
  }
}
</script>

<template>
  <div class="app">
    <header class="app-header">
      <div class="brand">
        <div class="logo">RAG</div>
        <div>
          <h1>langchain4j 知识库</h1>
          <p>Spring Boot + Qdrant + Vue 3</p>
        </div>
      </div>
      <div class="tabs">
        <el-radio-group v-model="activeTab" size="small">
          <el-radio-button value="docs">文档管理</el-radio-button>
          <el-radio-button value="chat">知识问答</el-radio-button>
        </el-radio-group>
      </div>
    </header>

    <main class="app-main">
      <section class="docs-pane" :class="{ 'tab-hidden': activeTab !== 'docs' }">
        <div class="pane-card">
          <h2>上传文档</h2>
          <DocumentUpload :uploading="uploading" @upload="handleUpload" />
        </div>
        <div class="pane-card grow">
          <div class="pane-title">
            <h2>文档列表</h2>
            <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
          <DocumentTable
            :documents="documents"
            :loading="loading"
            :busy-id="busyId"
            @delete="handleDelete"
            @reindex="handleReindex"
          />
        </div>
      </section>

      <section class="chat-pane" :class="{ 'tab-hidden': activeTab !== 'chat' }">
        <ChatPanel
          :messages="messages"
          :streaming="streaming"
          :error="error"
          @send="send"
          @stop="stop"
          @new-conversation="newConversation"
        />
      </section>
    </main>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff, #67c23a);
}

.brand h1 {
  margin: 0;
  font-size: 18px;
}

.brand p {
  margin: 2px 0 0;
  color: #909399;
  font-size: 12px;
}

.app-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  padding: 16px 24px 24px;
}

.docs-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-pane {
  flex: 1.1;
  min-width: 0;
  min-height: calc(100vh - 120px);
}

.pane-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 16px;
}

.pane-card.grow {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.pane-card h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.pane-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.pane-title h2 {
  margin: 0;
}

.tab-hidden {
  display: none;
}

.tabs {
  display: none;
}

@media (max-width: 899px) {
  .app-main {
    flex-direction: column;
    padding: 12px;
  }

  .chat-pane {
    min-height: calc(100vh - 180px);
  }

  .tabs {
    display: block;
  }
}

@media (min-width: 900px) {
  /* 宽屏时两栏同时展示，标签页只作为移动端切换 */
  .docs-pane.tab-hidden {
    display: flex;
  }

  .chat-pane.tab-hidden {
    display: block;
  }
}
</style>
