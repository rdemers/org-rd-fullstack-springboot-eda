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
        <v-icon start icon="mdi-pipe" class="mr-3"></v-icon>
        <h2 class="text-h5 font-weight-bold">FLINK CLUSTER DASHBOARD</h2>
        <v-spacer/>
        <v-chip size="x-small" color="white" variant="outlined" class="text-caption">
          SYNC: {{ lastUpdate }}
        </v-chip>
      </v-card-title>
      <v-card-subtitle class="py-3 px-4 bg-grey-lighten-4">
        <v-row no-gutters justify="space-between">
          <span>
            <span class="mr-2 text-caption font-weight-bold">Cluster ID</span>
            <code class="font-weight-bold">123456</code>
          </span>
          <span>
            <v-badge dot :color="clusterLevel" inline class="mr-1" />
            <span class="mr-2 text-caption font-weight-bold">Address</span>
            <code class="font-weight-bold">tcp://123456</code>
          </span>
        </v-row>
      </v-card-subtitle>
      <v-divider/>
      <v-card-text>
        <div class="text-overline mb-2 text-grey-darken-1">ACTIVE & PAST JOBS</div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th class="text-left">Job Name / ID</th>
              <th class="text-center">Status</th>
              <th class="text-center">Tasks (R/T)</th>
              <th class="text-right">Duration</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!sortedJobs?.length || sortedJobs.length === 0">
              <td colspan="5" class="text-center py-4 text-grey-darken-1 italic">
                <v-icon icon="mdi-database-off-outline" class="mr-2" />
                NO JOBS
              </td>
            </tr>
            <tr v-for="job in sortedJobs" :key="job.jid">
              <td>
                <div class="font-weight-bold text-truncate" style="max-width: 240px">
                  {{ job.name }}
                </div>
                <div class="text-caption text-grey font-mono">
                  {{ job.jid.substring(0, 8) }}
                </div>
              </td>
              <td class="text-center">
                <v-chip :color="getStatusColor(job.state)" size="x-small"
                        variant="flat" class="font-weight-bold">
                  {{ job.state }}
                </v-chip>
              </td>
              <td class="text-center">
                <b class="text-success">{{ job.tasks.running }}</b>
                / {{ job.tasks.total }}
              </td>
              <td class="text-right text-caption font-mono">
                {{ formatDuration(job.duration) }}
              </td>
              <td class="text-right">
                <v-btn icon="mdi-open-in-new" size="x-small" variant="text" 
                       :href="`/flink/#/jobs/${job.jid}`" target="_blank"/>
              </td>
            </tr>
          </tbody>
        </v-table>
        <div class="d-flex justify-space-between align-center my-4">
          <div class="text-overline text-grey-darken-1">ACTIVE NOTIFICATIONS</div>
        </div>
        <v-table density="compact" class="border">
          <thead>
            <tr>
              <th width="32"></th>
              <th class="text-left">Time</th>
              <th class="text-left">Description</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="incidents.length === 0">
              <td colspan="3" class="text-center py-4 text-grey-darken-1 italic">
                <v-icon icon="mdi-check-circle-outline" class="mr-2" />
                NO NOTIFICATIONS
              </td>
            </tr>
            <tr v-for="incident in incidents" :key="incident.id">
              <td>
                <v-icon icon="mdi-alert" :color="incident.severity" size="small"/>
              </td>
              <td class="text-caption font-mono">{{ incident.time }}</td>
              <td>{{ incident.message }}</td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
      <v-divider/>
      <v-card-actions class="bg-grey-lighten-5 px-4">
        <v-switch v-model="autoRefresh" label="Live Monitor" 
                  color="primary" hide-details density="compact"/>
        <v-spacer/>
        <v-btn icon="mdi-refresh" variant="text" @click="fetchFlinkData" :loading="loading"/>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup>
    import { ref, computed, onMounted, onUnmounted, watch } from "vue";

    const jobs = ref([]);
    const incidents = ref([]);
    const loading = ref(false);
    const autoRefresh = ref(true);
    const lastUpdate = ref('--:--:--');
    let timer = null;

    const fetchFlinkData = async () => {
        loading.value = true;
        try {
            await new Promise(r => setTimeout(r, 1000));
            jobs.value = [
              {
                jid: '7684be6004e4e955c2a558a9bc463f65',
                name: 'Streaming WordCount',
                state: 'RUNNING',
                duration: 520000,
                tasks: { running: 4, total: 4 }
              },
              {
                jid: 'ab78dcdbb1db025539e30217ec54ee16',
                name: 'Inventory Analytics',
                state: 'RESTARTING',
                duration: 45000,
                tasks: { running: 0, total: 2 }
              }
            ];

            incidents.value = [
              {
                id: 1,
                time: '02:07',
                message: 'billing – Checkpoints failing (5x)',
                severity: 'error'
              },
              {
                id: 2,
                time: '10:00',
                message: 'sending – Checkpoints failing (2x)',
                severity: 'warning'
              }
            ];

            lastUpdate.value = new Date().toLocaleTimeString();
        } finally {
            loading.value = false;
        }
    }

    const clusterLevel = computed(() => {
      if (incidents.value.some(i => i.severity === 'error')) return 'error'
      if (jobs.value.some(j => j.state === 'RESTARTING')) return 'warning'
      return 'success'
    });

    const sortedJobs = computed(() => {
      const weight = s => ({ FAILED: 3, RESTARTING: 2, RUNNING: 1 }[s] || 0)
      return [...jobs.value].sort((a, b) => weight(b.state) - weight(a.state))
    });

    const getStatusColor = state =>
      ({ RUNNING: 'success', FAILED: 'error', RESTARTING: 'warning' }[state] || 'grey')

    const formatDuration = ms => {
      const s = Math.floor(ms / 1000)
      return `${Math.floor(s / 60)}m ${s % 60}s`
    };

    const startTimer = () => {
      stopTimer()
      timer = setInterval(fetchFlinkData, 5000)
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
      fetchFlinkData()
      if (autoRefresh.value)
          startTimer()
    });
    
    onUnmounted(() => {
        stopTimer();
    });
</script>

<style scoped>
</style>