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
    <v-data-table :headers="headers" :items="inventories" :loading="loading" :items-per-page="5" 
                  density="compact" class="elevation-1"
                  :items-per-page-options="[
                    { value: 5, title: '5' }, 
                    { value: 10, title: '10' },
                    { value: 15, title: '15' }, 
                    { value: -1, title: t('common.label.all') }
                  ]"
                  :items-per-page-text="t('common.label.items-per-page')" 
                  :page-text="`{0}-{1} ${t('common.label.of')} {2}`">
      <template #loading>
        <v-skeleton-loader type="table-row-divider@5"/>
      </template>
      <template #item.actions="{ item }">
        <div class="d-flex gap-2">
          <v-btn size="small" variant="text" color="primary" icon="mdi-pencil"
            :title="t('common.button.edit')"
            @click="navigateDetail(item.inventoryId)"/>
          <v-btn size="small" variant="text" color="error" icon="mdi-trash-can-outline"
            :title="t('common.button.delete')"
            @click="openDeleteDialog(item.inventoryId)"/>
        </div>
      </template>
      <template #no-data>
        <span class="text-grey">{{ t('common.label.no-data') }}</span>
      </template>
    </v-data-table>
    <v-row class="mt-4 px-3 gap-2" align="start" no-gutters>
      <v-btn color="success" prepend-icon="mdi-plus" @click="navigateAdd">
        {{ t('common.button.add') }}
      </v-btn>
      <v-btn color="success" prepend-icon="mdi-file-pdf-box" @click="navigatePdf">
        {{ t('common.button.report') }}
      </v-btn>
      <v-spacer/>
      <v-text-field v-model.number="refillQty" type="number" :min="1" :max="1000"
        :label="t('inventory.qty')" density="compact" variant="outlined"
        hide-details="auto" :rules="refillRules" style="max-width: 130px"/>
      <v-btn color="success" prepend-icon="mdi-basket-fill" :disabled="!isRefillValid" @click="refill">
        {{ t('common.button.fill') }}
      </v-btn>
    </v-row>
    <v-dialog v-model="deleteDialog" persistent max-width="420">
      <v-card>
        <v-card-title class="text-h6 pb-0">{{ t('common.label.confirmation') }}</v-card-title>
        <v-card-text class="pt-4">{{ t('common.label.ask-delete') }}</v-card-text>
        <v-card-actions>
          <v-spacer/>
          <v-btn variant="text" @click="closeDeleteDialog">{{ t('common.button.cancel') }}</v-btn>
          <v-btn variant="elevated" color="error"  @click="deleteID">{{ t('common.button.ok') }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-dialog v-model="pdfDialog" persistent width="900">
        <v-card>
            <vrd-pdfviewer :url="`${useRuntimeConfig().public.apiURL.replace('/api', '')}/report/invents-report`"
                           :title="t('inventory.report.title')" 
                           :subtitle="t('inventory.report.subtitle')"/>
            <v-card-actions>
                <v-btn color="success" variant="elevated" @click="pdfDialog=false">
                  {{ t('common.button.ok') }}
                </v-btn>
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
    import { onMounted, ref, computed } from "vue";
    import { useI18n }                  from "vue-i18n";

    import type InventoryView from "@/types/InventoryView";   
    import InventoryService   from "@/services/InventoryService";
    import { useSnackbar }    from "@/composables/useSnackbar";

    const REFILL_MIN = 1;
    const REFILL_MAX = 1000;

    const { t }                = useI18n();
    const inventories          = ref<InventoryView[]>([]);
    const loading              = ref(false);
    const deleteDialog         = ref(false);
    const pdfDialog            = ref<boolean>(false);
    const idToDelete           = ref<number | null>(null);
    const refillQty            = ref<number>(100);
    const { snackbar, notify } = useSnackbar();

    const refillRules = computed(() => [
      (v: number | null) =>
        (v !== null && Number.isInteger(v) && v >= REFILL_MIN && v <= REFILL_MAX) ||
        t('common.rule.between', { field: t('inventory.qty'), min: REFILL_MIN, max: REFILL_MAX })
    ]);

    const isRefillValid = computed(() =>
      Number.isInteger(refillQty.value) &&
      refillQty.value >= REFILL_MIN &&
      refillQty.value <= REFILL_MAX
    );

    const headers = computed(() => [
      { title: t('inventory.id'),            key: 'inventoryId', align: 'start' as const },
      { title: t('inventory.product-code'),  key: 'productCode' },    
      { title: t('inventory.product-descr'), key: 'productDescription' },
      { title: t('inventory.qty'),           key: 'qty' },
      { title: t('common.label.action'),     key: 'actions', sortable: false, align: 'start' as const }
    ]);

    async function retrieve() {
        loading.value = true;
    
        try {
          const response = await InventoryService.getAllView();
          if (!response || !response.data) {
            inventories.value = [];
          } else {
            inventories.value = response.data;
          }
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.select-failed", { message: msg }), "error");
        } finally {
            loading.value = false;
        }
    }

    async function refill() {
        if (!isRefillValid.value)
            return;

        loading.value = true;

        try {
          const response = await InventoryService.refill(refillQty.value);
          if (!response || !response.data) {
            inventories.value = [];
          } else {
            inventories.value = response.data;
          }
          notify(t("common.message.refill-success"));
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.refill-failed", { message: msg }), "error");
        } finally {
            loading.value = false;
        }
    }

    async function deleteID() {
        if (idToDelete.value === null) 
            return;

        try {
            await InventoryService.delete(idToDelete.value);
            notify(t("common.message.delete-success"));
            await retrieve();
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.delete-failed", { message: msg }), "error");
        } finally {
            closeDeleteDialog();
        }
    }

    const navigateDetail = (id: number | null) => {
        if (id == null) {
          console.warn("navigateDetail is called with a null id.");
          return;
        }
        navigateTo(`/inventories/inventory/${id}`);
    }

    const navigateAdd = () => {
        navigateTo("/inventories/inventory/add");
    }

    const openDeleteDialog = (id: number | null) => {
        idToDelete.value = id;
        deleteDialog.value = true;
    }

    const closeDeleteDialog = () => {
        deleteDialog.value = false;
        idToDelete.value = null;
    }

    function navigatePdf(): void {
        pdfDialog.value = true;
    }

    onMounted(retrieve);
</script>

<style scoped>
  .gap-2 {
    gap: 8px;
  }
</style>