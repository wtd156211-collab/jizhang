<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Button, Field, PageHeader, Panel, Switch, Tag, TextInput } from '@/components/ui'
import { PermissionButton } from '@/components/business'
import { bookkeepingApi, type OcrConfigStatus } from '@/api'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
const status = ref<OcrConfigStatus | null>(null), loading = ref(false), saving = ref(false)
const form = reactive({ enabled: false, accessKeyId: '', apiKey: '' })
const error = ref('')
let active = true
function clear() { form.accessKeyId = ''; form.apiKey = '' }
async function load() {
  loading.value = true
  try { const data = await bookkeepingApi.ocr.configStatus(); if (active) { status.value = data; form.enabled = data.provider === 'aliyun' } }
  catch (e) { if (active) toastError(e, 'OCR 配置加载失败') }
  finally { if (active) loading.value = false }
}
async function save(clearApiKey = false) {
  if (saving.value || !status.value) return
  error.value = ''
  if (!clearApiKey && [form.accessKeyId, form.apiKey].some(value => value && !/^[!-~]{1,256}$/.test(value))) { error.value = '凭据须为1至256个非空白 ASCII 字符'; return }
  saving.value = true
  try { await bookkeepingApi.ocr.saveConfig({ provider: form.enabled ? 'aliyun' : 'disabled', ...(clearApiKey ? { clearApiKey: true } : { accessKeyId: form.accessKeyId || undefined, apiKey: form.apiKey || undefined }) }); clear(); pushToast(clearApiKey ? '凭据已清空' : 'OCR 配置已保存'); await load() }
  catch (e) { toastError(e, '配置保存失败') }
  finally { saving.value = false }
}
async function remove() { if (await confirm({ title: '清空阿里云凭据', message: '清空后暂停识别，重新填写凭据才能恢复。票据原图和历史保留。', danger: true, confirmText: '清空凭据' })) await save(true) }
onMounted(load)
onBeforeUnmount(() => { active = false; clear() })
</script>
<template>
  <div class="page">
    <PageHeader title="OCR 配置" desc="管理员维护阿里云识别凭据，识别结果由录入人员核对。"><Button icon="refresh" :loading="loading" @click="load">刷新状态</Button></PageHeader>
    <Panel title="阿里云 OCR" class="reveal"><div class="bk-config-body">
      <p v-if="loading" class="soft">正在加载配置…</p>
      <p v-else-if="!status" class="field-error">配置未加载，请重试。</p>
      <template v-else>
        <div class="bk-config-status"><Tag :tone="status.recognitionAvailable ? 'ok' : 'neutral'" dot>{{ status.recognitionAvailable ? '本地配置就绪' : '尚未就绪' }}</Tag><span class="soft">云服务是否开通、授权及额度正常，需通过实际票据识别验证。</span></div>
        <p v-if="!status.encryptionReady" class="field-error">服务端加密密钥未就绪，请在后端启动环境设置 BOOKKEEPING_SECRET_KEY。</p>
        <div class="form-grid">
          <Field label="识别服务" group><Switch v-model="form.enabled" label="启用阿里云 OCR" :disabled="saving" /></Field>
          <Field label="接口区域" hint="使用官方 HTTPS 地址"><TextInput model-value="杭州 · ocr-api.cn-hangzhou.aliyuncs.com" readonly /></Field>
          <Field label="AccessKey ID" :hint="status.accessKeyIdSuffix ? `当前：${status.accessKeyIdSuffix}，留空保留` : '尚未保存'"><TextInput v-model="form.accessKeyId" type="password" autocomplete="new-password" :maxlength="256" placeholder="输入新 ID" :disabled="saving || !status.encryptionReady" /></Field>
          <Field label="AccessKey Secret" :hint="status.keySuffix ? `当前：${status.keySuffix}，留空保留` : '尚未保存'"><TextInput v-model="form.apiKey" type="password" autocomplete="new-password" :maxlength="256" placeholder="输入新 Secret" :disabled="saving || !status.encryptionReady" /></Field>
        </div>
        <p v-if="error" class="field-error" role="alert">{{ error }}</p>
        <div class="bk-config-actions"><PermissionButton perm="bookkeeping:ocr:config" variant="primary" :loading="saving" @click="save()">保存配置</PermissionButton><PermissionButton perm="bookkeeping:ocr:config" danger :disabled="saving || !status.keyConfigured" @click="remove">清空凭据</PermissionButton></div>
        <p class="soft">凭据加密保存在服务端，页面仅显示脱敏后缀。启停和替换凭据不会删除已保存的原始票据。</p>
      </template>
    </div></Panel>
  </div>
</template>
