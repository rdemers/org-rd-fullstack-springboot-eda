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
  <v-container fluid class="fill-height">
    <v-row align="center" justify="center">
      <v-col cols="12" sm="10" md="8" lg="6">
        <v-card elevation="12" class="pa-6 rounded-xl" width="100%" 
                max-width="800" style="margin: 0 auto;">
          <v-card-title class="text-h4 text-center py-4">
            <v-icon color="error" size="large" class="mb-2">mdi-alert-circle-outline</v-icon>
            {{ t('error.title') }}
          </v-card-title>
          <v-card-text>
            <v-alert type="error" variant="tonal" :title="t('error.exception')" class="mb-4">
              {{ error?.message || t('error.msg_default') }}
            </v-alert>
            <div class="text-center">
              <v-chip size="small" variant="outlined" color="secondary" class="mb-2">
                {{ t('error.status_code') }} : {{ error?.statusCode || '500' }}
              </v-chip>
              <p v-if="error?.statusText" class="text-body-2 text-medium-emphasis italic">
                "{{ error.statusText }}"
              </p>
            </div>
            <v-expansion-panels v-if="isDev && error?.stack" class="mt-4">
              <v-expansion-panel :title="t('error.detail')">
                <v-expansion-panel-text>
                  <pre class="text-caption overflow-auto">{{ error.stack }}</pre>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>
          </v-card-text>
          <v-divider/>
          <v-card-actions class="justify-center pt-4">
            <v-btn color="primary" variant="elevated" prepend-icon="mdi-home" @click="handleError">
              {{ t('error.go_home') }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
    import type { NuxtError } from "#app";

    // Defining props with a default value for security.
    const props = withDefaults(defineProps<{
      error?: NuxtError
    }>(), {
      error: () => ({ statusCode: 500, statusMessage: "Internal Server Error" } as NuxtError)
    });

    const { t } = useI18n();
    //const isDev = import.meta.dev; // Check if we are in development mode.
    const isDev = true;

    const handleError = () => clearError({ redirect: "/" });
</script>

<style scoped>
    .italic {
      font-style: italic;
    }
    pre {
      white-space: pre-wrap;
      word-wrap: break-word;
    }

    .fill-height {
      min-height: 100vh;
    }
</style>