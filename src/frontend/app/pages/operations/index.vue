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
    <v-alert v-if="errorMessage" title="Exception" type="error" class="mb-4">
      {{ errorMessage }}
    </v-alert>
    <v-card class="mx-auto mt-5" max-width="900" elevation="10" border>
      <v-card-title class="bg-indigo-darken-3 text-white d-flex align-center py-0">
        <v-icon start icon="mdi-cogs" class="mr-3"></v-icon>
        <h2 class="text-h5 font-weight-bold">{{ t('operations.title') }}</h2>
        <v-spacer/>
        <v-chip size="x-small" color="white" variant="outlined" class="text-caption">
          {{ t('operations.sync') }} {{ lastUpdate }}
        </v-chip>
      </v-card-title>
      <v-card-subtitle class="py-3 px-4 bg-grey-lighten-4">
        <v-row density="compact" justify="space-between">
          <span class="mr-2 text-caption font-weight-bold">{{ t('operations.subtitle') }}</span>
          <span>
            <v-badge dot :color="getPipelineStateColor(pipelineState.value)" inline class="mr-1 custom-dot"/>
            <span class="mr-2 text-caption font-weight-bold">
              {{ t('operations.pipeline') }} : {{ t(`${pipelineState.translationKey}`) }}
            </span>
          </span>
        </v-row>
      </v-card-subtitle>
      <v-divider/>

      <!-- Vertical Tabs -->
      <v-card-text class="pa-0">
        <div class="d-flex">

          <!-- Left column : tabs -->
          <v-tabs v-model="activeTab" direction="vertical"
                  bg-color="indigo-darken-1" color="white" class="flex-shrink-0">
            <v-tab value="options">
              <v-icon start icon="mdi-tune"/>
              {{ t('operations.options') }}
            </v-tab>
            <v-tab value="results">
              <v-icon start icon="mdi-chart-bar"/>
              {{ t('operations.result') }}
            </v-tab>
            <v-tab value="stats">
              <v-icon start icon="mdi-counter"/>
              {{ t('operations.stats') }}
            </v-tab>
          </v-tabs>

          <v-divider vertical/>

          <!-- Right column : data -->
          <v-tabs-window v-model="activeTab" class="flex-grow-1">

            <!-- Tab 1 : Options -->
            <v-tabs-window-item value="options" class="pa-4">
              <div class="text-overline text-medium-emphasis mb-2">{{ t('operations.options') }}</div>
              <v-row dense>
                <v-col v-for="(column, index) in optionColumns" :key="index" cols="12" sm="6">
                  <div v-for="item in column" class="d-flex align-center ga-3 pa-3 rounded-lg mb-2 switch-row"
                       :key="item.id" :class="{ 'bg-blue-darken-4': item.value }" 
                       @click="item.value = !item.value">
                    <v-switch
                      v-model="item.value" hide-details compact density="compact"
                      color="#58a6ff" class="switch-mini" @click.stop/>
                    <v-avatar size="20" rounded="lg">
                      <v-icon :icon="item.icon" size="20"/>
                    </v-avatar>
                    <div>
                      <div class="text-body-2 font-weight-medium text-white">{{ t(item.label) }}</div>
                      <div class="text-caption text-grey">{{ t(item.desc) }}</div>
                    </div>
                  </div>
                </v-col>
              </v-row>

              <!-- Actions -->
              <div class="text-overline text-medium-emphasis mt-4 mb-2">{{ t('operations.actions') }}</div>
              <v-divider class="mb-3" color="red" opacity=".7" thickness="3" gradient/>
              <div class="d-flex ga-2">
                <v-btn type="button" :disabled="!canStart" color="primary"
                       class="flex-grow-1" variant="flat" @click="startPipeline">
                  {{ t('operations.start') }}
                </v-btn>
                <v-btn type="button" :disabled="!canInit" color="primary"
                       class="flex-grow-1" variant="flat" @click="resetPipeline">
                  {{ t('operations.reset') }}
                </v-btn>
              </div>
            </v-tabs-window-item>

            <!-- Tab 2 : Results -->
            <v-tabs-window-item value="results" class="pa-4">
              <div class="text-overline text-medium-emphasis mb-2">{{ t('operations.result') }}</div>
              <v-list density="compact" bg-color="transparent">
                <v-list-item v-for="field in fields" :key="field.id">
                  <template #prepend>
                    <v-icon :color="field.color" size="x-small" icon="mdi-circle" class="mr-3"/>
                  </template>
                  <v-list-item-title class="font-weight-medium">{{ t(field.label) }}</v-list-item-title>
                  <template #append>
                    <span class="text-h6 font-weight-bold">
                      {{ requestCount?.[field.id] ?? 0 }}
                    </span>
                  </template>
                </v-list-item>
              </v-list>
            </v-tabs-window-item>

            <!-- Tab 3 : Stats -->
            <v-tabs-window-item value="stats" class="pa-4">
              <div class="text-overline text-medium-emphasis mb-2">{{ t('operations.stats') }}</div>
              <v-list density="compact" bg-color="transparent">
                <v-list-item v-for="stat in statFields" :key="stat.id">
                  <template #prepend>
                    <v-icon :color="stat.color" size="x-small" :icon="stat.icon" class="mr-3"/>
                  </template>
                  <v-list-item-title class="font-weight-medium">{{ t(stat.label) }}</v-list-item-title>
                  <template #append>
                    <span class="text-h6 font-weight-bold">
                      {{ stats?.[stat.id] ?? 0 }}
                    </span>
                  </template>
                </v-list-item>
              </v-list>
              <v-alert v-if="stats?.exceptionMSG" type="error" variant="tonal" density="compact"
                       class="mt-3" :title="t('operations.exception')">
                {{ stats.exceptionMSG }}
              </v-alert>
            </v-tabs-window-item>

          </v-tabs-window>
        </div>
      </v-card-text>

      <v-divider/>
      <v-card-actions class="bg-grey-lighten-5 px-4">
        <v-switch v-model="autoRefresh" :label="t('kafka.monitor')"
                  color="primary" hide-details density="compact"/>
        <v-spacer/>
        <v-btn icon="mdi-refresh" variant="text" @click="refreshStats" :loading="loading"/>
      </v-card-actions>
    </v-card>
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="snackbar.timeout"
                style="white-space: pre-line" location="top" timer="bottom" timer-color="white">
      {{ snackbar.message }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, computed, reactive,
             onMounted, onUnmounted, watch } from "vue";
    import { useI18n }                       from "vue-i18n";
    import type RequestCount                 from "@/types/RequestCount";
    import RequestService                    from "@/services/RequestService";
    import type PipelineContext              from "@/types/PipelineContext";
    import type StatsContext                 from "@/types/StatsContext";
    import PipelineService                   from "@/services/PipelineService";
    import { useSnackbar }                   from "@/composables/useSnackbar";
    import { getResultColor }                from "@/types/Result";
    import { getPipelineStateColor,
             PipelineState,
             findPipelineState,
             type PipelineStateValue }       from "@/types/PipelineState";

    const { t }          = useI18n();
    const loading        = ref(false);
    const autoRefresh    = ref(true);
    const lastUpdate     = ref("--:--:--");
    const activeTab      = ref("options");

    const errorMessage   = ref<string | null>(null);
    const requestCount   = ref<RequestCount | null>(null);
    const stats          = ref<StatsContext | null>(null);
    const pipelineState  = ref<PipelineStateValue>(PipelineState.UNKNOWN);
    const optionsHydrated = ref(false);

    const { snackbar, notify } = useSnackbar();
    const canStart = computed(() => pipelineState.value.value === PipelineState.READY.value);
    const canInit  = computed(() => pipelineState.value.value === PipelineState.COMPLETED.value ||
                                    pipelineState.value.value === PipelineState.EXCEPTION.value ||
                                    pipelineState.value.value === PipelineState.UNKNOWN.value);

    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setInterval> | null = null;

    interface FieldDef { id: keyof RequestCount, label: string, color: string };
    const fields: FieldDef[] = [
      { id: "nbrPending",   label: "operations.pending",   color: getResultColor(10) },
      { id: "nbrBackOrder", label: "operations.backOrder", color: getResultColor(20) },
      { id: "nbrExecuted",  label: "operations.executed",  color: getResultColor(30) },
      { id: "nbrError",     label: "operations.error",     color: getResultColor(99) },
      { id: "nbrUnknown",   label: "operations.unknown",   color: getResultColor(0)  }
    ];

    type StatKey = "nbrPublished" | "nbrProcessed" | "nbrProcessedWithError";
    interface StatFieldDef { id: StatKey, label: string, color: string, icon: string };
    const statFields: StatFieldDef[] = [
      { id: "nbrPublished",          label: "operations.published",        color: "info",    icon: "mdi-send" },
      { id: "nbrProcessed",          label: "operations.processed",        color: "success", icon: "mdi-check-circle" },
      { id: "nbrProcessedWithError", label: "operations.processed-error",  color: "error",   icon: "mdi-alert-circle" }
    ];

    interface OptionsDef { id: string, label: string, desc: string, icon: string, value: boolean };
    const options: OptionsDef[] = reactive([
      { id: "pause",   label: "operations.pause-kafka",      desc: "operations.pause-kafka-desc",      icon: "mdi-pause-octagon", value: false },
      { id: "key",     label: "operations.kafka-keys",       desc: "operations.kafka-keys-desc",       icon: "mdi-key-variant",   value: true },
      { id: "replay",  label: "operations.replay",           desc: "operations.replay-desc",           icon: "mdi-replay",        value: false },
      { id: "latence", label: "operations.latence-insert",   desc: "operations.latence-insert-desc",   icon: "mdi-timer-outline", value: false }
    ]);

    // Two-column layout for the options tab.
    // Column 1: Pause Kafka + Enable keys. Column 2: Enable replay + Insert latency.
    const optionColumns = computed(() => {
      const byId = (...ids: string[]) =>
        ids.map(id => options.find(o => o.id === id)).filter((o): o is OptionsDef => o != null);
      return [
        byId("pause", "key"),
        byId("replay", "latence")
      ];
    });

    // The pause toggle acts IMMEDIATELY (not only on Start): as soon as the switch flips,
    // update the context and pause/resume the Kafka listener via the backend. Fires on
    // mount-hydration only if the stored value differs from the default (i.e. re-asserts a
    // persisted pause), which is harmless/idempotent.
    watch(() => options.find(o => o.id === "pause")?.value, async (paused) => {
        if (paused === undefined)
            return;
        try {
            await PipelineService.setPause(paused);
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("operations.pause-exception", { message: msg }), "error");
        }
    });

    const startTimer = () => {
        stopTimer();
        timer = setInterval(refreshStats, 5000);
    }

    const stopTimer = () => {
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    }

    // One-shot: align the option switches with the stored backend parameters.
    const hydrateOptions = (ctx: PipelineContext) => {
        const apply = (id: string, value: boolean | null | undefined) => {
            const opt = options.find(o => o.id === id);
            if (opt && typeof value === "boolean")
                opt.value = value;
        };
        apply("pause",   ctx.pause);
        apply("key",     ctx.key);
        apply("replay",  ctx.replay);
        apply("latence", ctx.latence);
    }

    // Polled refresh: request counts + pipeline stats/state only (never the parameters).
    const refreshStats = async () => {
        if (loading.value)
          return;

        controller?.abort();
        controller = new AbortController();
        const signal = controller.signal;

        loading.value = true;
        await new Promise(r => setTimeout(r, 1000));
        try {
            const respRequest = await RequestService.getCount();
            const respStats   = await PipelineService.getStats();

            if (signal.aborted)
              return;

            requestCount.value  = respRequest.data;
            stats.value         = respStats.data;
            pipelineState.value = findPipelineState(respStats.data.pipelineState);
            errorMessage.value  = null;
        } catch (err) {
            if (signal.aborted)
              return;

            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            errorMessage.value = t("common.message.select-failed", { message: msg });
        } finally {
            lastUpdate.value = new Date().toLocaleTimeString();
            loading.value = false;
        }
    }

    // Initial load (on mount): hydrate the parameter toggles ONCE from the backend
    // context, then refresh the stats. Subsequent refreshes only touch the stats.
    const loadAll = async () => {
        try {
            const respContext = await PipelineService.getPipelineContext();
            if (!optionsHydrated.value) {
                hydrateOptions(respContext.data);
                optionsHydrated.value = true;
            }
        } catch (err) {
            console.error(err);
        }
        await refreshStats();
    }

    const startPipeline = async () => {
      try {
        const pipelineContext: PipelineContext = {
          pause:   options.find(o => o.id === "pause")?.value   ?? false,
          key:     options.find(o => o.id === "key")?.value     ?? true,
          replay:  options.find(o => o.id === "replay")?.value  ?? false,
          latence: options.find(o => o.id === "latence")?.value ?? false
        };
        await PipelineService.startPipeline(pipelineContext);
        refreshStats();
        notify(t("operations.pipeline-started"), "info");
      } catch (err) {
        console.error(err);
        const msg = err instanceof Error ? err.message : String(err);
        notify(t("operations.start-exception", { message: msg }), "error");
      }
    }

    const resetPipeline = async () => {
      try {
        await PipelineService.resetPipeline();
        refreshStats();
        notify(t("operations.pipeline-reset"), "info");
      } catch (err) {
        console.error(err);
        const msg = err instanceof Error ? err.message : String(err);
        notify(t("operations.reset-exception", { message: msg }), "error");
      }
    }

    watch(autoRefresh, (newVal) => {
        newVal ? startTimer() : stopTimer();
    })

    onMounted(() => {
        loadAll();
        if (autoRefresh.value)
            startTimer();
    })

    onUnmounted(() => {
        stopTimer();
        controller?.abort();
    })
</script>

<style scoped>
  .switch-mini :deep(.v-switch__track) {
    width: 30px;
    height: 14px;
  }

  .switch-mini :deep(.v-switch__thumb) {
    width: 10px;
    height: 10px;
  }

  .switch-mini :deep(.v-selection-control) {
    min-height: 24px;
  }

  :deep(.v-badge--dot .v-badge__badge) {
    height: 12px;
    width: 12px;
    min-width: 12px;
  }
</style>