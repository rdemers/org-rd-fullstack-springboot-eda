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
        <v-icon start icon="mdi-hexagon-multiple-outline" class="mr-3"/>
        <h2 class="text-h5 font-weight-bold">{{ t('hazelcast.title') }}</h2>
        <v-spacer/>
        <v-chip size="x-small" color="white" variant="outlined" class="text-caption">
          {{ t('hazelcast.sync') }} {{ lastUpdate }}
        </v-chip>
      </v-card-title>
      <v-card-subtitle class="py-3 px-4 bg-grey-lighten-4">
        <v-row density="compact" justify="space-between">
          <span>
            <span class="mr-2 text-caption font-weight-bold">{{ t('hazelcast.cluster-id') }}</span>
            <code class="font-weight-bold">{{ hazelcastDashboard?.instanceName }}/{{ hazelcastDashboard?.clusterName }}</code>
          </span>
          <span>
            <v-badge dot :color="clusterStateColor" inline class="mr-1 custom-dot"/>
            <v-chip :color="clusterStateColor" size="x-small" variant="flat" class="text-caption font-weight-bold">
              {{ hazelcastDashboard?.clusterState ?? '—' }}
            </v-chip>
          </span>
        </v-row>
      </v-card-subtitle>
      <v-divider/>
      <v-card-text>
        <div class="text-overline mb-2 text-grey-darken-1">
          <v-icon icon="mdi-server-network" size="16" class="mr-1"/>
          {{ t('hazelcast.members') }}
          <v-chip size="x-small" color="teal" variant="flat" class="ml-2">
            {{ hazelcastDashboard?.members?.length ?? 0 }}
          </v-chip>
        </div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-left">{{ t('hazelcast.address') }}</th>
              <th class="text-left">{{ t('hazelcast.uuid') }}</th>
              <th class="text-center">{{ t('hazelcast.local') }}</th>
              <th class="text-center">{{ t('hazelcast.lite') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!hazelcastDashboard?.members?.length">
              <td colspan="4" class="text-center py-4 text-grey-darken-1 italic">
                <v-icon icon="mdi-server-off" class="mr-2"/>
                {{ t('hazelcast.no-members') }}
              </td>
            </tr>
            <tr v-for="member in hazelcastDashboard?.members" :key="member.uuid">
              <td class="font-weight-medium">
                <v-icon v-if="member.local" icon="mdi-map-marker-check" color="teal" size="16" class="mr-1"/>
                {{ member.address }}
              </td>
              <td>
                <code class="text-caption text-grey-darken-2">{{ member.uuid }}</code>
              </td>
              <td class="text-center">
                <v-icon :icon="member.local ? 'mdi-check-circle' : 'mdi-minus-circle-outline'"
                        :color="member.local ? 'teal' : 'grey'" size="18"/>
              </td>
              <td class="text-center">
                <v-chip v-if="member.liteMember" size="x-small" color="orange" variant="flat">
                  LITE
                </v-chip>
                <v-icon v-else icon="mdi-minus" color="grey-lighten-1" size="16"/>
              </td>
            </tr>
          </tbody>
        </v-table>
        <v-divider class="my-6"/>
        <div class="text-overline mb-2 text-grey-darken-1">
          <v-icon icon="mdi-database-sync-outline" size="16" class="mr-1"/>
          {{ t('hazelcast.map-stats') }}
        </div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-left">{{ t('hazelcast.map-name') }}</th>
              <th class="text-right">{{ t('hazelcast.owned') }}</th>
              <th class="text-right">{{ t('hazelcast.backup') }}</th>
              <th class="text-right">{{ t('hazelcast.hits') }}</th>
              <th class="text-right">{{ t('hazelcast.misses') }}</th>
              <th class="text-right">{{ t('hazelcast.locks') }}</th>
              <th class="text-right">{{ t('hazelcast.heap') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!hazelcastDashboard?.stats?.length">
              <td colspan="7" class="text-center py-4 text-grey-darken-1 italic">
                <v-icon icon="mdi-database-off-outline" class="mr-2"/>
                {{ t('hazelcast.no-maps') }}
              </td>
            </tr>
            <tr v-for="stats in hazelcastDashboard?.stats" :key="stats.mapName">
              <td class="font-weight-medium">{{ stats.mapName }}</td>
              <td class="text-right">{{ stats.ownedEntryCount.toLocaleString() }}</td>
              <td class="text-right text-grey-darken-1">
                {{ stats.backupEntryCount.toLocaleString() }}
              </td>
              <td class="text-right text-success font-weight-medium">
                {{ stats.hits.toLocaleString() }}
              </td>
              <td class="text-right">
                <v-scroll-x-transition mode="out-in">
                  <span :key="stats.missCount" :class="hitRatio(stats) < 50 ? 
                        'text-error font-weight-black' : 'text-grey-darken-1'">
                    {{ stats.missCount.toLocaleString() }}
                    <v-icon v-if="hitRatio(stats) < 50" 
                            icon="mdi-alert-decagram" color="error" size="14" class="ml-1"/>
                  </span>
                </v-scroll-x-transition>
              </td>
              <td class="text-right">
                <v-chip v-if="stats.lockedEntryCount > 0"
                        size="x-small" color="warning" variant="flat">
                  {{ stats.lockedEntryCount }}
                </v-chip>
                <span v-else class="text-grey-lighten-1">0</span>
              </td>
              <td class="text-right text-caption text-grey-darken-1">
                {{ formatBytes(stats.heapCost) }}
              </td>
            </tr>
          </tbody>
        </v-table>
        <v-divider class="my-6"/>
        <div class="text-overline mb-3 text-grey-darken-1">
          <v-icon icon="mdi-chart-pie" size="16" class="mr-1"/>
          {{ t('hazelcast.partitions') }}
        </div>
        <v-row density="compact" class="mb-2">
          <v-col cols="6" sm="3">
            <div class="border rounded pa-3 text-center">
              <div class="text-caption text-grey-darken-1 mb-1">{{ t('hazelcast.total-partitions') }}</div>
              <div class="text-h6 font-weight-bold">
                {{ hazelcastDashboard?.partitionInfo?.totalPartitions ?? '—' }}
              </div>
            </div>
          </v-col>
          <v-col cols="6" sm="3">
            <div class="border rounded pa-3 text-center">
              <div class="text-caption text-grey-darken-1 mb-1">{{ t('hazelcast.local-partitions') }}</div>
              <div class="text-h6 font-weight-bold text-teal">
                {{ hazelcastDashboard?.partitionInfo?.localPartitions ?? '—' }}
              </div>
            </div>
          </v-col>
          <v-col cols="6" sm="3">
            <div class="border rounded pa-3 text-center">
              <div class="text-caption text-grey-darken-1 mb-1">{{ t('hazelcast.cluster-safe') }}</div>
              <v-icon
                :icon="hazelcastDashboard?.partitionInfo?.clusterSafe ? 'mdi-shield-check' : 'mdi-shield-alert'"
                :color="hazelcastDashboard?.partitionInfo?.clusterSafe ? 'success' : 'error'" size="28"/>
            </div>
          </v-col>
          <v-col cols="6" sm="3">
            <div class="border rounded pa-3 text-center">
              <div class="text-caption text-grey-darken-1 mb-1">{{ t('hazelcast.migration') }}</div>
              <v-chip :color="hazelcastDashboard?.partitionInfo?.migrationInProgress ? 'warning' : 'success'"
                size="small" variant="flat">
                <v-icon :icon="hazelcastDashboard?.partitionInfo?.migrationInProgress
                        ? 'mdi-swap-horizontal-bold' : 'mdi-check'" size="14" class="mr-1"/>
                        {{ hazelcastDashboard?.partitionInfo?.migrationInProgress
                        ? t('hazelcast.migrating') : t('hazelcast.stable') }}
              </v-chip>
            </div>
          </v-col>
        </v-row>
        <div v-if="hazelcastDashboard?.partitionInfo" class="mt-3">
          <div class="d-flex justify-space-between text-caption text-grey-darken-1 mb-1">
            <span>{{ t('hazelcast.partition-coverage') }}</span>
            <span>{{ partitionPercent }}%</span>
          </div>
          <v-progress-linear :model-value="partitionPercent" color="primary" 
                             bg-color="grey-lighten-3" rounded height="6"/>
        </div>
        </v-card-text>
        <v-divider/>
          <v-card-actions class="bg-grey-lighten-5 px-4">
            <v-switch v-model="autoRefresh" :label="t('hazelcast.monitor')"
                      color="primary" hide-details density="compact"/>
        <v-spacer/>
        <v-btn icon="mdi-refresh" variant="text" @click="fetchHazelcastData" :loading="loading"/>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, computed, onMounted, onUnmounted, watch } from "vue";
    import { useI18n }                                      from "vue-i18n";
    import type HazelcastDashboard                          from "@/types/HazelcastDashboard";
    import type HazelcastStats                              from "@/types/HazelcastStats";
    import HazelcastService                                 from "@/services/HazelcastService";

    const { t }               = useI18n();
    const loading             = ref(false);
    const autoRefresh         = ref(true);
    const lastUpdate          = ref("--:--:--");
    const errorMessage        = ref<string | null>(null);
    const hazelcastDashboard  = ref<HazelcastDashboard | null>(null);

    let controller: AbortController | null = null;
    let timer: ReturnType<typeof setInterval> | null = null;

    const clusterStateColor = computed(() => {
      switch (hazelcastDashboard.value?.clusterState) {
        case "ACTIVE":        return "success";
        case "NO_MIGRATION":  return "info";
        case "FROZEN":        return "warning";
        case "PASSIVE":       return "error";
        default:              return "grey";
      }
    });

    const partitionPercent = computed(() => {
      const info = hazelcastDashboard.value?.partitionInfo;
      if (!info || info.totalPartitions === 0)
        return 0;
      return Math.round((info.localPartitions / info.totalPartitions) * 100);
    });

    const hitRatio = (stats: HazelcastStats): number => {
      const total = stats.hits + stats.missCount;
      if (total === 0)
        return 100;
      return Math.round((stats.hits / total) * 100);
    };

    const formatBytes = (bytes: number): string => {
      if (bytes === 0)   
          return "0 B";
      if (bytes < 1024)  
          return `${bytes} B`;
      if (bytes < 1_048_576) 
          return `${(bytes / 1024).toFixed(1)} KB`;

      return `${(bytes / 1_048_576).toFixed(1)} MB`;
    };

    const fetchHazelcastData = async () => {
      if (loading.value)
          return;

      controller?.abort();
      controller = new AbortController();
      const signal = controller.signal;

      loading.value = true;
      await new Promise(r => setTimeout(r, 1000));
      try {
          const response = await HazelcastService.get();

          if (signal.aborted)
              return;

          hazelcastDashboard.value = response.data;
          errorMessage.value = null;
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
    };

    const startTimer = () => {
        stopTimer();
       timer = setInterval(fetchHazelcastData, 5000);
    };

    const stopTimer = () => {
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    };

    watch(autoRefresh, (newVal) => {
      newVal ? startTimer() : stopTimer();
    });

    onMounted(() => {
        fetchHazelcastData();
        if (autoRefresh.value)
            startTimer();
    });

    onUnmounted(() => {
        stopTimer();
        controller?.abort();
    });
</script>

<style scoped>
  :deep(.v-badge--dot .v-badge__badge) {
    height: 12px;
    width: 12px;
    min-width: 12px;
  }
</style>