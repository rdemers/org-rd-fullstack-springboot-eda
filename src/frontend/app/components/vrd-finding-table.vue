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
  <div>
    <v-alert v-if="errorMessage" type="error" variant="tonal" closable class="mb-4"
             @click:close="errorMessage = null">
      {{ errorMessage }}
    </v-alert>
    <v-alert v-else-if="executed && errorCount === 0" type="success" variant="tonal" class="mb-4">
      {{ t('finding.message.no-error') }}
    </v-alert>

    <v-data-table :headers="headers" :items="findings" :loading="loading" :items-per-page="50"
                  :sort-by="[{ key: 'severity', order: 'desc' }]"
                  :custom-key-sort="{ severity: compareSeverity }"
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
      <template #item.step="{ value }">
        <v-chip size="small" label variant="outlined">
          {{ findStep(value) ? t(findStep(value)!.translationKey) : value }}
        </v-chip>
      </template>
      <template #item.severity="{ value }">
        <v-chip :color="getSeverityColor(value)" size="small" label variant="flat">
          {{ findSeverity(value) ? t(findSeverity(value)!.translationKey) : value }}
        </v-chip>
      </template>
      <template #no-data>
        <span class="text-grey">
          {{ executed ? t('common.label.no-data') : t('finding.message.not-executed') }}
        </span>
      </template>
    </v-data-table>

    <div class="d-flex align-center mt-4">
      <v-btn color="primary" prepend-icon="mdi-play" :loading="loading" :disabled="loading"
             @click="execute">
        {{ t('finding.button.execute') }}
      </v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
    import { ref, computed } from "vue";
    import { useI18n }       from "vue-i18n";

    import type Finding        from "@/types/Finding";
    import { findStep, findSeverity, getSeverityColor } from "@/types/Finding";
    import ConciliationService from "@/services/ConciliationService";

    const { t } = useI18n();

    const findings     = ref<Finding[]>([]);
    const errorMessage = ref<string | null>(null);
    const loading      = ref(false);
    const executed     = ref(false);

    const errorCount = computed(() => findings.value.filter(f => f.severity === "ERROR").length);

    // Lets the parent show the number of ERROR findings on the tab.
    defineExpose({ errorCount });

    const headers = computed(() => [
      { title: t('finding.column.step'),     key: 'step',     align: 'start' as const },
      { title: t('finding.column.severity'), key: 'severity' },
      { title: t('finding.column.message'),  key: 'message' }
    ])

    function compareSeverity(a: string, b: string): number {
        return (findSeverity(a)?.rank ?? 0) - (findSeverity(b)?.rank ?? 0);
    }

    // Manual execution only: the conciliation is a full read-only analysis that can take several seconds.
    async function execute() {
        loading.value = true;
        try {
            const response = await ConciliationService.getFindings();
            findings.value     = response?.data ?? [];
            errorMessage.value = null;
            executed.value     = true;
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            errorMessage.value = t("common.message.select-failed", { message: msg });
        } finally {
            loading.value = false;
        }
    }
</script>
