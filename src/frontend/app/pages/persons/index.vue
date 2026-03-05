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
    <v-data-table :headers="headers" :items="persons" :loading="loading" :items-per-page="5" 
                  density="compact" class="elevation-1"
                  :items-per-page-options="[
                    { value: 5, title: '5' }, 
                    { value: 10, title: '10' },
                    { value: 15, title: '15' }, 
                    { value: -1, title: t('common.label.all') }
                  ]"
                  :items-per-page-text="t('common.label.itemsperpage')" 
                  :page-text="`{0}-{1} ${t('common.label.of')} {2}`">
      <template #loading>
        <v-skeleton-loader type="table-row-divider@5" />
      </template>
      <template #item.balance="{ value }">
        <span class="font-weight-medium">{{ formatMoney(value) }}</span>
      </template>
      <template #item.actions="{ item }">
        <div class="d-flex gap-2">
          <v-btn size="small" variant="text" color="primary" icon="mdi-pencil"
            :title="t('common.button.edit')"
            @click="navigateDetail(item.personId)"/>
          <v-btn size="small" variant="text" color="error" icon="mdi-trash-can-outline"
            :title="t('common.button.delete')"
            @click="openDeleteDialog(item.personId)"/>
        </div>
      </template>
      <template #no-data>
        <span class="text-grey">{{ t('common.label.nodata') }}</span>
      </template> 
    </v-data-table>
    <v-row class="mt-4 px-3">
      <v-btn color="success" prepend-icon="mdi-plus" @click="navigateAdd">
        {{ t('common.button.add') }}
      </v-btn>
    </v-row>
    <v-dialog v-model="deleteDialog" persistent max-width="420">
      <v-card>
        <v-card-title class="text-h6 pb-0">{{ t('common.label.confirmation') }}</v-card-title>
        <v-card-text class="pt-4">{{ t('common.label.askdelete') }}</v-card-text>
        <v-card-actions>
          <v-spacer/>
          <v-btn variant="text" @click="closeDeleteDialog">{{ t('common.button.cancel') }}</v-btn>
          <v-btn color="error" variant="elevated" @click="deleteID">{{ t('common.button.ok') }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="snackbar.timeout"
                style="white-space: pre-line" location="top">
      {{ snackbar.message }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { onMounted, ref, computed } from "vue";
    import { useI18n }                  from "vue-i18n";
    import type Person                  from "@/types/Person";
    import PersonService                from "@/services/PersonService";
    import { useMoneyFormatter }        from "@/composables/useMoneyFormatter";
    import { useSnackbar }              from "@/composables/useSnackbar";

    const { t } = useI18n();
    const { formatMoney } = useMoneyFormatter("CAD");
    const { snackbar, notify } = useSnackbar();

    const persons = ref<Person[]>([]);
    const loading = ref(false);
    const deleteDialog = ref(false);
    const idToDelete = ref<number | null>(null);

    const headers = computed(() => [
        { title: t("person.id"), key: "personId", align: "start" as const },
        { title: t("person.firstname"), key: "firstName" },
        { title: t("person.lastname"), key: "lastName" },
        { title: t("person.balance"), key: "balance", align: "end" as const },
        { title: t("common.label.action"), key: "actions", sortable: false, align: "start" as const }
    ]);

    async function retrieve() {
        loading.value = true;
    
        try {
            const response = await PersonService.getAll();
            persons.value = response.data;
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.selectfailed", { message: msg }), "error");
        } finally {
            loading.value = false;
        }
    };

    async function deleteID() {
        if (idToDelete.value === null)
            return;

        try {
            await PersonService.delete(idToDelete.value);
            notify(t("common.message.deletesuccess"));
            await retrieve();
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.deletefailed", { message: msg }), "error");
        } finally {
            closeDeleteDialog();
        }
    };

    const navigateDetail = (id: number | null) => {
      if (id == null) {
        console.warn("navigateDetail is called with a null id.");
        return;
      }
      navigateTo(`/persons/person/${id}`);
    };

    const navigateAdd = () => {
      navigateTo("/persons/person/add");
    };

    const openDeleteDialog = (id: number | null) => {
      idToDelete.value = id;
      deleteDialog.value = true;
    };

    const closeDeleteDialog = () => {
        deleteDialog.value = false;
        idToDelete.value = null;
    };

    onMounted(retrieve);
</script>

<style scoped>
  .gap-2 {
    gap: 8px;
  }
</style>