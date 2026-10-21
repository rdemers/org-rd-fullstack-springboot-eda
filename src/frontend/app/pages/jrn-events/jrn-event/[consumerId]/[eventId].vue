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
    <v-row justify="center">
      <v-col cols="12" sm="10" md="8" lg="6">
        <v-card :loading="isFetching" class="mt-4 elevation-2">
          <v-card-title class="text-h5 pa-4">{{ t('jrn-event.title.view') }}</v-card-title>
          <v-divider/>
          <v-card-text>
            <template v-if="!isFetching && jrnEvent">
              <v-row>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.consumer-id') }}</div>
                  <div class="text-body-1">{{ jrnEvent.consumerId }}</div>
                </v-col>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.event-id') }}</div>
                  <div class="text-body-1">{{ jrnEvent.eventId }}</div>
                </v-col>
              </v-row>
              <v-row>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.batch-id') }}</div>
                  <div class="text-body-1">{{ jrnEvent.batchId }}</div>
                </v-col>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.event-type') }}</div>
                  <v-chip :color="getEventTypeColor(jrnEvent.eventType)" size="small" label variant="flat">
                    {{ findEventType(jrnEvent.eventType) ? t(findEventType(jrnEvent.eventType)!.translationKey) : jrnEvent.eventType }}
                  </v-chip>
                </v-col>
              </v-row>
              <v-row>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.result') }}</div>
                  <v-chip :color="getResultColor(jrnEvent.result)" size="small" label variant="flat">
                    {{ t(findResult(jrnEvent.result).translationKey) }}
                  </v-chip>
                </v-col>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.payload') }}</div>
                  <div class="text-body-1">{{ jrnEvent.payload }}</div>
                </v-col>
              </v-row>
              <v-row>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.received-at') }}</div>
                  <div class="text-body-1">{{ formatDateTime(jrnEvent.receivedAt) }}</div>
                </v-col>
                <v-col cols="12" sm="6">
                  <div class="text-caption text-medium-emphasis">{{ t('jrn-event.processed-at') }}</div>
                  <div class="text-body-1">{{ formatDateTime(jrnEvent.processedAt) }}</div>
                </v-col>
              </v-row>
              <v-divider class="my-4"/>
              <div class="d-flex justify-end">
                <v-btn color="primary" min-width="120" @click="navigateToParent">
                  {{ t('common.button.ok') }}
                </v-btn>
              </div>
            </template>
            <v-skeleton-loader v-else type="article, actions"/>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="snackbar.timeout"
                style="white-space: pre-line" location="top" timer="bottom" timer-color="white">
      {{ snackbar.message }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, onMounted }             from "vue";
    import { useI18n }                    from "vue-i18n";
    import { useSnackbar }                from "@/composables/useSnackbar";
    import { getResultColor, findResult }       from "@/types/Result";
    import { findEventType, getEventTypeColor } from "@/types/EventType";

    import type JrnEvent   from "@/types/JrnEvent";
    import JrnEventService from "@/services/JrnEventService";

    const { t }    = useI18n();
    const route    = useRoute();
    const { snackbar, notify } = useSnackbar();

    useHead({
      title: t('jrn-event.title.view')
    });

    const jrnEvent    = ref<JrnEvent | null>(null);
    const isFetching  = ref(true);

    function formatDateTime(value: string | null): string {
        return value ? new Date(value).toLocaleString("en-US", { timeZone: "UTC" }) : "";
    }

    async function load() {
        const consumerId = route.params.consumerId as string;
        const eventId    = route.params.eventId as string;

        isFetching.value = true;
        try {
            const response = await JrnEventService.get(consumerId, eventId);
            jrnEvent.value = response.data;
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.select-failed", { message: msg }), "error");
        } finally {
            isFetching.value = false;
        }
    }

    onMounted(load);

    function navigateToParent() {
        return navigateTo("/jrn-events");
    }
</script>

<style scoped>
</style>
