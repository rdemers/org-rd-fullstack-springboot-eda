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
    <v-card class="mx-auto mt-5" max-width="900" elevation="10" border>
      <v-card-title class="bg-indigo-darken-3 text-white d-flex align-center py-4">
        <v-icon start icon="mdi-vector-polyline" class="mr-3"></v-icon>
        <h2 class="text-h5 font-weight-bold">{{ t('kafka.title') }}</h2>
        <v-spacer/>
        <v-chip size="x-small" color="white" variant="outlined" class="text-caption">
          {{ t('kafka.sync') }} {{ lastUpdate }}
        </v-chip>
      </v-card-title>
      <v-card-subtitle class="py-3 px-4 bg-grey-lighten-4">
        <v-row no-gutters justify="space-between">
          <span>
            <span class="mr-2 text-caption font-weight-bold">{{ t('kafka.cluster_id') }}</span>
            <code class="font-weight-bold">{{kafkaDashboard?.clusterId}}</code>
          </span>
          <span>
            <v-badge dot :color="errorMessage ? 'red' : 'success'" inline class="mr-1"/>
            <span class="mr-2 text-caption font-weight-bold">{{ t('kafka.address') }}</span>
            <code class="font-weight-bold">{{kafkaDashboard?.clusterIp}}</code>
          </span>
        </v-row>
      </v-card-subtitle>
      <v-divider/>
      <v-card-text>
        <div class="text-overline mb-2 text-grey-darken-1">{{ t('kafka.topics') }}</div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-left">{{ t('kafka.name') }}</th>
              <th class="text-center">{{ t('kafka.partitions') }}</th>
              <th class="text-center">{{ t('kafka.replicas') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!kafkaDashboard?.topics || kafkaDashboard.topics.length === 0">
            <td colspan="3" class="text-center py-4 text-grey-darken-1 italic">
              <v-icon icon="mdi-database-off-outline" class="mr-2"></v-icon>
              {{ t('kafka.no_topics') }}
            </td>
          </tr>
            <tr v-for="topic in kafkaDashboard?.topics" :key="topic.name">
              <td class="font-weight-medium">{{ topic.name }}</td>
              <td class="text-center">{{ topic.partitions }}</td>
              <td class="text-center">{{ topic.replicationFactor }}</td>
            </tr>
          </tbody>
        </v-table>
        <v-divider class="my-6"/>
        <div class="d-flex justify-space-between align-center mb-2">
          <div class="text-overline text-grey-darken-1">{{ t('kafka.consumer') }}</div>
        </div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-left">{{ t('kafka.groupid') }}</th>
              <th class="text-left">{{ t('kafka.status') }}</th>
              <th class="text-right">{{ t('kafka.lag') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!kafkaDashboard?.groupLagSummaries || kafkaDashboard.groupLagSummaries.length === 0">
              <td colspan="3" class="text-center py-4 text-grey-darken-1 italic">
                <v-icon icon="mdi-account-group-outline" class="mr-2"></v-icon>
                {{ t('kafka.no_consumers') }}
              </td>
            </tr>
            <tr v-for="group in kafkaDashboard?.groupLagSummaries" :key="group.groupId">
              <td>{{ group.groupId }}</td>
              <td>
                <v-chip :color="group.status === 'STABLE' ? 'success' : 'warning'" size="x-small" variant="flat">
                  {{ group.status }}
                </v-chip>
              </td>
              <td class="text-right">
                <v-scroll-x-transition mode="out-in">
                  <span :key="group.totalLag" :class="group.totalLag > 1000 ? 'text-error font-weight-black' : 'font-weight-medium'">
                    {{ group.totalLag.toLocaleString() }}
                    <v-icon v-if="group.totalLag > 1000" color="error" size="18" class="ml-1">mdi-alert-decagram</v-icon>
                  </span>
                </v-scroll-x-transition>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
      <v-divider/>
      <v-card-actions class="bg-grey-lighten-5 px-4">
        <v-switch v-model="autoRefresh" :label="t('kafka.monitor')" 
                  color="primary" hide-details density="compact"/>
        <v-spacer/>
        <v-btn icon="mdi-refresh" variant="text" @click="fetchKafkaData" :loading="loading"/>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, onMounted, onUnmounted, watch } from "vue";
    import { useI18n }                            from "vue-i18n";
    import type KafkaDashboard                    from "@/types/KafkaDashboard";
    import KafkaService                           from "@/services/KafkaService";

    const kafkaDashboard = ref<KafkaDashboard | null>(null);
    const errorMessage   = ref<string | null>(null);
    const loading        = ref(false);
    const autoRefresh    = ref(true);
    const lastUpdate     = ref("");

    const { t } = useI18n();

    let timer: ReturnType<typeof setInterval> | null = null;

    const fetchKafkaData = async () => {

        if (loading.value) 
          return;

        loading.value = true;
        await new Promise(r => setTimeout(r, 1000));
        try {
            const response = await KafkaService.get();
            kafkaDashboard.value = response.data;
            errorMessage.value = null;
        } catch (err) {
            console.error(err);
            errorMessage.value = t("common.label.errorfetch");
        } finally {
            lastUpdate.value = new Date().toLocaleTimeString();
            loading.value = false;
        }
    };

    const startTimer = () => {
        stopTimer();
        timer = setInterval(fetchKafkaData, 5000);
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
        fetchKafkaData();
        if (autoRefresh.value) 
            startTimer();
    });
    
    onUnmounted(() => {
        stopTimer();
    });
</script>

<style scoped>
</style>