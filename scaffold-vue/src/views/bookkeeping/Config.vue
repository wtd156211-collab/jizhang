<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Field, Segmented, Select, StatusTag, Switch, TextInput, type Column } from '@/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@/components/business'
import { bookkeepingApi, type ConfigRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { RECORD_TYPES } from '@/utils/bookkeeping'
type Kind = 'categories' | 'paymentMethods' | 'fundAccounts' | 'projects'
const kind = ref<Kind>('categories')
const descriptors = {
  categories: { label: '分类', name: 'categoryName', code: 'categoryCode' },
  paymentMethods: { label: '支付方式', name: 'methodName', code: 'methodCode' },
  fundAccounts: { label: '资金账户', name: 'accountName', code: 'accountCode' },
  projects: { label: '项目', name: 'projectName', code: 'projectCode' },
}
const active = computed(() => descriptors[kind.value])
const list = useListPage<ConfigRow, { keyword: string; status: string }>({ query: { keyword: '', status: '' }, fetch: async params => { const page = await bookkeepingApi.config[kind.value].list(params); return { ...page, rows: page.rows.map(row => ({ ...row })) } }, immediate: false })
const columns = computed<Column[]>(() => [{ key: active.value.name, title: '名称', minWidth: 190 }, { key: active.value.code, title: '编码' }, { key: 'type', title: '类型', width: 115 }, { key: 'sort', title: '排序', width: 75 }, { key: 'status', title: '状态', width: 85 }, { key: 'actions', title: '操作', width: 95, className: 'col-actions' }])
const dialog = ref(false), saving = ref(false), deleting = ref(false)
const empty = () => ({ id: undefined as number | undefined, name: '', code: '', categoryType: 'EXPENSE' as 'EXPENSE' | 'INCOME', parentId: 0, accountType: 'BANK', sort: 0, status: 1, remark: '' })
const form = reactive(empty()), error = ref('')
watch(kind, () => { dialog.value = false; list.reset() }, { immediate: true })
function edit(row?: ConfigRow) { Object.assign(form, empty(), row ? { ...row, name: row[active.value.name], code: row[active.value.code] } : {}); error.value = ''; dialog.value = true }
async function save() {
  if (saving.value) return
  if (!form.name.trim() || !/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(form.code)) { error.value = '请填写名称和有效编码（字母开头，最多64位字母、数字、下划线或短横线）'; return }
  saving.value = true
  try {
    const shared = { sort: form.sort, status: form.status, remark: form.remark }
    if (kind.value === 'categories') { const payload = { ...shared, categoryType: form.categoryType, categoryName: form.name.trim(), categoryCode: form.code.trim(), parentId: form.parentId }; if (form.id) await bookkeepingApi.config.categories.update(form.id, payload); else await bookkeepingApi.config.categories.create(payload) }
    if (kind.value === 'paymentMethods') { const payload = { ...shared, methodName: form.name.trim(), methodCode: form.code.trim() }; if (form.id) await bookkeepingApi.config.paymentMethods.update(form.id, payload); else await bookkeepingApi.config.paymentMethods.create(payload) }
    if (kind.value === 'fundAccounts') { const payload = { ...shared, accountName: form.name.trim(), accountCode: form.code.trim(), accountType: form.accountType }; if (form.id) await bookkeepingApi.config.fundAccounts.update(form.id, payload); else await bookkeepingApi.config.fundAccounts.create(payload) }
    if (kind.value === 'projects') { const payload = { ...shared, projectName: form.name.trim(), projectCode: form.code.trim() }; if (form.id) await bookkeepingApi.config.projects.update(form.id, payload); else await bookkeepingApi.config.projects.create(payload) }
    pushToast('配置已保存'); dialog.value = false; await list.load()
  } catch (e) { toastError(e, '配置保存失败') }
  finally { saving.value = false }
}
async function remove(row: ConfigRow) {
  if (deleting.value || !await confirm({ title: `删除${active.value.label}`, message: `确认删除「${row[active.value.name]}」？已有历史引用的配置不能删除。`, danger: true, confirmText: '删除' })) return
  deleting.value = true
  try { await bookkeepingApi.config[kind.value].remove(row.id); pushToast('配置已删除'); await list.load() }
  catch (e) { toastError(e, '配置删除失败') }
  finally { deleting.value = false }
}
const TYPE_LABELS: Record<string, string> = { BANK: '银行账户', CASH: '现金账户', OTHER: '其他账户', WECHAT: '微信账户', ALIPAY: '支付宝账户', INCOME: '收入', EXPENSE: '支出' }
</script>
<template>
  <ListPage title="记账配置" desc="维护收支分类、支付方式、资金账户和项目，历史使用记录保留。" :table-title="active.label" :list="list" :columns="columns" :min-width="690">
    <template #before><Segmented v-model="kind" label="配置类别" :options="Object.entries(descriptors).map(([value, item]) => ({ label: item.label, value: value as Kind }))" /></template>
    <template #actions><PermissionButton perm="bookkeeping:config:edit" variant="primary" icon="plus" @click="edit()">新增{{ active.label }}</PermissionButton></template>
    <template #search><Field label="关键词" wide><TextInput v-model="list.query.keyword" placeholder="名称 / 编码" icon="search" clearable @enter="list.search()" /></Field><Field label="状态"><Select v-model="list.query.status" :options="[{ label: '全部状态', value: '' }, { label: '正常', value: '1' }, { label: '停用', value: '0' }]" /></Field></template>
    <template #cell-type="{ row }"><span class="soft">{{ TYPE_LABELS[String(row.categoryType || row.accountType)] || '—' }}</span></template>
    <template #cell-status="{ row }"><StatusTag :status="Number(row.status)" /></template>
    <template #cell-actions="{ row }"><div class="row-actions"><PermissionButton perm="bookkeeping:config:edit" icon-only icon="edit" label="编辑配置" @click="edit(row)" /><PermissionButton perm="bookkeeping:config:edit" icon-only icon="trash" label="删除配置" danger :disabled="deleting" @click="remove(row)" /></div></template>
    <FormDialog :open="dialog" :title="`${form.id ? '编辑' : '新增'}${active.label}`" :saving="saving" @close="!saving && (dialog = false)" @submit="save">
      <Field label="名称" required><TextInput v-model="form.name" :maxlength="100" /></Field><Field label="编码" required><TextInput v-model="form.code" :maxlength="64" placeholder="如 OFFICE" /></Field>
      <Field v-if="kind === 'categories'" label="收支类型"><Select v-model="form.categoryType" :options="RECORD_TYPES" /></Field>
      <Field v-if="kind === 'fundAccounts'" label="账户类型"><Select v-model="form.accountType" :options="[{ label: '银行账户', value: 'BANK' }, { label: '现金账户', value: 'CASH' }, { label: '其他账户', value: 'OTHER' }]" /></Field>
      <Field label="排序"><TextInput v-model="form.sort" type="number" /></Field><Field label="状态" group><Switch :model-value="form.status === 1" :label="form.status === 1 ? '正常' : '停用'" @update:model-value="form.status = $event ? 1 : 0" /></Field>
      <Field v-if="kind === 'projects' || kind === 'fundAccounts'" label="备注" span><TextInput v-model="form.remark" multiline :maxlength="500" /></Field><p v-if="error" class="field-error span-2" role="alert">{{ error }}</p>
    </FormDialog>
  </ListPage>
</template>
