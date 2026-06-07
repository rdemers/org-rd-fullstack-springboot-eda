<!--
  * Copyright 2026; Réal Demers.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  -->
<template>
  <v-container>
    <v-alert v-if="errorMessage" type="error" variant="tonal" closable class="mb-4"
             @click:close="errorMessage = null">
      {{ errorMessage }}
    </v-alert>
    <v-data-table :headers="headers" :items="requests" :loading="loading" :items-per-page="5" 
                  density="compact" class="elevation-1"
                  :items-per-page-options="[
                    { value: 5, title: '5' }, 
                    { value: 50, title: '50' },
                    { value: 100, title: '100' }, 
                    { value: -1, title: t('common.label.all') }
                  ]"
                  :items-per-page-text="t('common.label.items-per-page')" 
                  :page-text="`{0}-{1} ${t('common.label.of')} {2}`">
      <template #loading>
        <v-skeleton-loader type="table-row-divider@5" />
      </template>
      <template #item.actions="{ item }">
        <div class="d-flex gap-2">
          <v-btn size="small" variant="text" color="primary" icon="mdi-pencil"
            :title="t('common.button.edit')"
            @click="navigateDetail(item.requestId)"/>
          <v-btn size="small" variant="text" color="error" icon="mdi-trash-can-outline"
            :title="t('common.button.delete')"
            @click="openDeleteDialog(item.requestId)"/>
        </div>
      </template>
      <template #item.operation="{ value }">
        {{ value != null ? t(findOperation(value).translationKey) : '!!! BUG !!!' }}
      </template>
      <template #item.result="{ value }">
        <v-chip v-if="value" :color="getResultColor(value)" size="small" label variant="flat"> 
          {{ value != null ? t(findResult(value).translationKey) : '!!! BUG !!!' }}
        </v-chip>
      </template>
      <template #no-data>
        <span class="text-grey">{{ t('common.label.no-data') }}</span>
      </template>
    </v-data-table>
    <v-row class="mt-4 px-3 gap-2" align="start" no-gutters>
      <v-btn color="success" prepend-icon="mdi-plus" @click="navigateAdd">
        {{ t('common.button.add') }}
      </v-btn>
      <v-spacer/>
      <v-select v-model="selectedResult" :items="resultItems" item-title="title" item-value="value"
        :label="t('request.result')" density="compact" variant="outlined"
        hide-details style="max-width: 220px"/>
      <v-btn color="success" prepend-icon="mdi-restore" :disabled="selectedResult == null" @click="resetAll">
        {{ t('common.button.reset') }}
      </v-btn>
    </v-row>
    <v-dialog v-model="deleteDialog" persistent max-width="420">
      <v-card>
        <v-card-title class="text-h6 pb-0">{{ t('common.label.confirmation') }}</v-card-title>
        <v-card-text class="pt-4">{{ t('common.label.ask-delete') }}</v-card-text>
        <v-card-actions>
          <v-spacer/>
          <v-btn variant="text" @click="closeDeleteDialog">{{ t('common.button.cancel') }}</v-btn>
          <v-btn color="error" variant="elevated" @click="deleteID">{{ t('common.button.ok') }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="snackbar.timeout" 
                style="white-space: pre-line" location="top" timer="bottom" timer-color="white">
      {{ snackbar.message }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { onMounted, ref, computed }   from "vue";
    import { useI18n }                    from "vue-i18n";
    import { getResultColor, findResult, getResults, Result } from "@/types/Result";
    import { findOperation }              from "@/types/Operation";  
    import { useSnackbar }                from "@/composables/useSnackbar";

    import type RequestView from "@/types/RequestView";   
    import RequestService   from "@/services/RequestService";

    const { t } = useI18n();
    const { snackbar, notify } = useSnackbar();

    const requests       = ref<RequestView[]>([]);
    const loading        = ref(false);
    const errorMessage   = ref<string | null>(null);
    const deleteDialog   = ref(false);
    const idToDelete     = ref<number | null>(null);
    const selectedResult = ref<number>(Result.PENDING.value);

    const resultItems = computed(() =>
      getResults().map(r => ({ value: r.value, title: t(r.translationKey) }))
    );

    const headers = computed(() => [
      { title: t('request.id'),               key: 'requestId', align: 'start' as const },
      { title: t('request.person-firstname'), key: 'personFirstName' },
      { title: t('request.person-lastname'),  key: 'personLastName' },
      { title: t('request.product-code'),     key: 'productCode' },    
      { title: t('request.product-descr'),    key: 'productDescription' },
      { title: t('request.qty'),              key: 'qty' },
      { title: t('request.operation'),        key: 'operation' },
      { title: t('request.result'),           key: 'result' },
      { title: t('common.label.action'),      key: 'actions', sortable: false, align: 'start' as const }
    ])

    async function retrieve() {
        loading.value = true;
        errorMessage.value = null;
    
        try {
          const response = await RequestService.getAllView();
          if (!response || !response.data) {
            requests.value = [];
          } else {
            requests.value = response.data;
          }
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            errorMessage.value = t("common.message.select-failed", { message: msg });
        } finally {
            loading.value = false;
        }
    }

    async function resetAll() {
        if (selectedResult.value === null) 
          return;

        loading.value = true;
        errorMessage.value = null;

        try {
          const response = await RequestService.resetAll(selectedResult.value);
          requests.value = (!response || !response.data) ? [] : response.data;
          notify(t("common.message.reset-success"));
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.reset-failed", { message: msg }), "error");
        } finally {
            loading.value = false;
        }
    }

    async function deleteID() {
        if (idToDelete.value === null) 
          return;

        try {
            await RequestService.delete(idToDelete.value);
            notify(t("common.message.delete-success"));
            await retrieve();
        } catch (err) {
            console.error(err);
            notify(t("common.message.delete-failed"), "error");
        } finally {
            closeDeleteDialog();
        }
    }

    const navigateDetail = (id: number | null) => navigateTo("/requests/request/" + id?.toString());
    const navigateAdd = () => navigateTo("/requests/request/add");

    const openDeleteDialog = (id: number | null) => {
      idToDelete.value = id;
      deleteDialog.value = true;
    }

    const closeDeleteDialog = () => {
      deleteDialog.value = false;
      idToDelete.value = null;
    }

    onMounted(retrieve);
</script>

<style scoped>
  .gap-2 {
    gap: 8px;
  }
</style>