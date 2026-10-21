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
        <v-icon start icon="mdi-pipe" class="mr-3"></v-icon>
        <h2 class="text-h5 font-weight-bold">{{ t('flink.title') }}</h2>
        <v-spacer/>
        <v-chip size="x-small" color="white" variant="outlined" class="text-caption">
          {{ t('flink.sync') }} {{ lastUpdate }}
        </v-chip>
      </v-card-title>
      <v-card-subtitle class="py-3 px-4 bg-grey-lighten-4">
        <v-row density="compact" justify="space-between">
          <span>
            <span class="mr-2 text-caption font-weight-bold">{{ t('flink.cluster-id') }}</span>
            <code class="font-weight-bold">{{flinkDashboard?.clusterId}}</code>
          </span>
          <span>
            <v-badge dot :color="errorMessage ? 'red' : 'success'" inline class="mr-1"/>
            <span class="mr-2 text-caption font-weight-bold">{{ t('flink.address') }}</span>
            <code class="font-weight-bold">{{flinkDashboard?.clusterIp}}</code>
          </span>
        </v-row>
      </v-card-subtitle>
      <v-divider/>
      <v-card-text>
        <div class="text-overline mb-2 text-grey-darken-1">{{ t('flink.summary') }}</div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-right">{{ t('flink.task-managers') }}</th>
              <th class="text-right">{{ t('flink.slots-state') }}</th>
              <th class="text-right">{{ t('flink.jobs-state') }}</th>
              <th class="text-right">{{ t('flink.version') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
                <td class="text-right text-caption font-mono">
                  {{flinkDashboard?.summary.taskManagers}}</td>
                <td class="text-right text-caption font-mono">
                  {{flinkDashboard?.summary.slotsTotal}} / {{flinkDashboard?.summary.slotsAvailable}}</td>
                <td class="text-right text-caption font-mono">
                  {{flinkDashboard?.summary.jobsRunning}} / {{flinkDashboard?.summary.jobsFinished}} / {{flinkDashboard?.summary.jobsCancelled}} /  {{flinkDashboard?.summary.jobsFailed}}</td>
                <td class="text-right text-caption font-mono">
                  {{flinkDashboard?.summary.version}} / {{flinkDashboard?.summary.commit}}</td>
            </tr>
          </tbody>
        </v-table>
        <div class="text-overline mb-2 text-grey-darken-1">{{ t('flink.jobs') }}</div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-left">{{ t('flink.job-name') }}</th>
              <th class="text-left">{{ t('flink.job-status') }}</th>
              <th class="text-left">{{ t('flink.job-since') }}</th>
              <th class="text-right">{{ t('flink.detail') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!flinkDashboard?.jobs || flinkDashboard?.jobs.length === 0">
              <td colspan="4" class="text-center py-4 text-grey-darken-1 italic">
                <v-icon icon="mdi-database-off-outline" class="mr-2" />
                {{ t('flink.no-jobs') }}
              </td>
            </tr>
            <tr v-for="job in flinkDashboard?.jobs" :key="job.jobId">
              <td>
                <div class="font-weight-bold text-truncate" style="max-width: 240px">
                  {{ job.name }}
                </div>
                <div class="text-caption text-grey font-mono">
                  {{ job.jobId }}
                </div>
              </td>
              <td class="text-left">
                <v-chip size="x-small" variant="flat" class="font-weight-bold">
                  {{ job.state }}
                </v-chip>
              </td>
              <td class="text-left text-caption font-mono">
                {{ job.startTime }}
              </td>
              <td class="text-right">
                <v-btn icon="mdi-open-in-new" size="x-small" variant="text" 
                       :href="`${flinkDashboard?.clusterIp}/jobs/${job.jobId}`"/>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
      <v-divider/>
      <v-card-actions class="bg-grey-lighten-5 px-4">
        <v-switch v-model="autoRefresh" :label="t('flink.monitor')"
                  color="primary" hide-details density="compact"/>
        <v-spacer/>
        <v-btn icon="mdi-refresh" variant="text" @click="fetchFlinkData" :loading="loading"/>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, onMounted, onUnmounted, watch } from "vue";
    import { useI18n }                            from "vue-i18n";
    import type FlinkDashboard                    from "@/types/FlinkDashboard";
    import FlinkService                           from "@/services/FlinkService";

    const { t } = useI18n();
    const loading = ref(false);
    const autoRefresh = ref(true);
    const lastUpdate = ref("--:--:--");
    const errorMessage   = ref<string | null>(null);
    const flinkDashboard = ref<FlinkDashboard | null>(null);

    let controller: AbortController | null = null;        
    let timer: ReturnType<typeof setInterval> | null = null;

    const fetchFlinkData = async () => {

        if (loading.value) 
          return;

        controller?.abort(); // Cancels the previous one if necessary.
        controller = new AbortController();
        const signal = controller.signal;

        loading.value = true;
        try {
            await new Promise(r => setTimeout(r, 1000));
            const response = await FlinkService.get();

            if (signal.aborted) 
              return; // Normal cancellation, not an error.

            flinkDashboard.value = response.data;
            errorMessage.value = null;
        } catch (err) {
            if (signal.aborted) 
              return; // Normal cancellation, not an error.

            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            errorMessage.value = t("common.message.select-failed", { message: msg });
        } finally {
            lastUpdate.value = new Date().toLocaleTimeString();
            loading.value = false;
        }
    }

    const startTimer = () => {
      stopTimer()
      timer = setInterval(fetchFlinkData, 5000)
    }

    const stopTimer = () => {
        if (timer) {
            clearInterval(timer);
            timer = null;
      }
    }

    onMounted(() => {
        fetchFlinkData();
        if (autoRefresh.value) 
            startTimer();
    })

    watch(autoRefresh, (newVal) => {
        newVal ? startTimer() : stopTimer();
    })

    onUnmounted(() => {
        stopTimer();
        controller?.abort();
    })
</script>

<style scoped>
  :deep(.v-badge--dot .v-badge__badge) {
    height: 12px;
    width: 12px;
    min-width: 12px;
  }
</style>