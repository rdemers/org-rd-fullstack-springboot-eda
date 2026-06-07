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
  <v-row justify="center" align="center" class="fill-height">
    <v-col cols="12" md="10" lg="8" class="text-center">
      <div class="mb-6"><vrd-osl-logo/></div>
      <v-card flat color="transparent" class="mx-auto license-container">
        <blockquote class="license-text text-body-2 text-medium-emphasis">
          <p class="mb-4">
            {{ t('about.licence-01') }}<br>
            {{ t('about.licence-02') }}<br>
            {{ t('about.licence-03') }}
          </p>
          <p class="mb-4">
            {{ t('about.licence-04') }}
          </p>
          <p class="mb-4">
            {{ t('about.licence-05') }}
          </p>
          <p class="mb-4">
            {{ t('about.licence-06') }}<br>
            {{ t('about.licence-07') }}<br>
            {{ t('about.licence-08') }}
          </p>
          <p class="mb-6">
            <v-btn
              variant="text"
              color="primary"
              href="http://www.apache.org/licenses/LICENSE-2.0"
              target="_blank"
              rel="noopener noreferrer"
              prepend-icon="mdi-file-certificate-outline"
              class="text-none">
              {{ t('about.licence-09') }}
            </v-btn>
          </p>
          <p class="font-italic opacity-70">
            {{ t('about.licence-10') }}<br>
            {{ t('about.licence-11') }}<br>
            {{ t('about.licence-12') }}<br>
            {{ t('about.licence-13') }}<br>
            {{ t('about.licence-14') }}
          </p>
        </blockquote>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
    const { t } = useI18n();
</script>

<style scoped>
    .license-container {
        max-width: 700px;
    }
    .license-text {
        line-height: 1.6;
        border-left: 3px solid rgba(var(--v-theme-primary), 0.2);
        padding-left: 24px;
        text-align: left;
        display: inline-block;
    }
    .opacity-70 {
        opacity: 0.7;
    }
</style>