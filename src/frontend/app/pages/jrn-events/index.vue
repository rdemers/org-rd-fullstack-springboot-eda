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
    <v-data-table :headers="headers" :items="jrnEvents" :loading="loading" :items-per-page="5"
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
      <template #item.eventType="{ value }">
        <v-chip v-if="value != null" :color="getEventTypeColor(value)" size="small" label variant="flat">
          {{ findEventType(value) ? t(findEventType(value)!.translationKey) : value }}
        </v-chip>
      </template>
      <template #item.result="{ value }">
        <v-chip v-if="value != null" :color="getResultColor(value)" size="small" label variant="flat">
          {{ t(findResult(value).translationKey) }}
        </v-chip>
      </template>
      <template #item.receivedAt="{ value }">
        {{ formatDateTime(value) }}
      </template>
      <template #item.processedAt="{ value }">
        {{ formatDateTime(value) }}
      </template>
      <template #item.actions="{ item }">
        <div class="d-flex gap-2">
          <v-btn size="small" variant="text" color="primary" icon="mdi-eye"
            :title="t('common.button.view')"
            @click="navigateDetail(item.consumerId, item.eventId)"/>
        </div>
      </template>
      <template #no-data>
        <span class="text-grey">{{ t('common.label.no-data') }}</span>
      </template>
    </v-data-table>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, computed }                    from "vue";
    import { useI18n }                          from "vue-i18n";
    import { getResultColor, findResult }       from "@/types/Result";
    import { findEventType, getEventTypeColor } from "@/types/EventType";
    import { useCrudListPage }                  from "@/composables/useCrudListPage";

    import type JrnEvent   from "@/types/JrnEvent";
    import JrnEventService from "@/services/JrnEventService";

    const { t } = useI18n();

    const jrnEvents    = ref<JrnEvent[]>([]);
    const errorMessage = ref<string | null>(null);

    const { loading } = useCrudListPage({
        items: jrnEvents,
        fetchAll: () => JrnEventService.getAll(),
        onSuccess: () => { errorMessage.value = null; },
        onError: (msg) => { errorMessage.value = msg; }
    });

    const headers = computed(() => [
      { title: t('jrn-event.consumer-id'),  key: 'consumerId',  align: 'start' as const },
      { title: t('jrn-event.event-id'),     key: 'eventId' },
      { title: t('jrn-event.batch-id'),     key: 'batchId' },
      { title: t('jrn-event.event-type'),   key: 'eventType' },
      { title: t('jrn-event.result'),       key: 'result' },
      { title: t("common.label.action"), key: "actions", sortable: false, align: "start" as const }
    ])

    function formatDateTime(value: string | null): string {
        return value ? new Date(value).toLocaleString("en-US", { timeZone: "UTC" }) : "";
    }

    function navigateDetail(consumerId: string, eventId: string) {
        if (consumerId && eventId) {
            navigateTo(`/jrn-events/jrn-event/${consumerId}/${eventId}`);
        }
    }
</script>

<style scoped>
</style>
