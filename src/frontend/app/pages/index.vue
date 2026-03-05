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
  <v-row justify="center" class="mt-4">
    <v-col cols="12" md="8" sm="10">
      <v-card class="logo-wrapper pa-4 mb-6" flat color="transparent">
        <div class="logo-item"><vrd-springboot-logo/></div>
        <div class="logo-item"><vrd-kafka-logo/></div>
        <div class="logo-item"><vrd-flink-logo/></div>
        <div class="logo-item"><vrd-nuxt-logo/></div>
        <div class="logo-item"><vrd-vuetify-logo/></div>
      </v-card>
      <v-card elevation="2" class="border">
        <v-card-title class="text-h5 pt-4">{{ t('index.welcome') }}</v-card-title>
        <v-card-text class="text-body-1 py-4">{{ t('index.sommaire') }}</v-card-text>        
        <v-divider/>
        <v-card-text>
          <p class="text-subtitle-1 font-weight-bold mb-2">{{ t('index.techno') }}</p>
          <v-list lines="one" class="bg-transparent pa-0">
            <v-list-item
              v-for="item in items"
              :key="item.techno"
              :title="t(item.description)"
              :href="item.url"
              target="_blank"
              rel="noopener noreferrer"
              append-icon="mdi-open-in-new"
              class="custom-list-item mb-1">
              <template #prepend>
                <v-icon class="item-icon" size="small">mdi-chevron-right</v-icon>
              </template>
            </v-list-item>
          </v-list>
        </v-card-text>
        <v-divider/>
        <v-card-actions class="justify-center pa-4">
          <v-btn
            variant="text"
            color="primary"
            prepend-icon="mdi-github"
            href="https://github.com/rdemers/org-rd-fullstack-springboot-eda"
            target="_blank"
            rel="noopener noreferrer"
            class="text-none">
            {{ t('index.scm') }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
    const { t } = useI18n();
    const items = [
        { techno: "SpringBoot", description: "index.techno1", url: "https://spring.io/projects/spring-boot" },
        { techno: "Kafka",      description: "index.techno2", url: "https://kafka.apache.org/" },
        { techno: "Flink",      description: "index.techno3", url: "https://flink.apache.org/" },
        { techno: "Nuxt",       description: "index.techno4", url: "https://nuxt.com" },
        { techno: "Vue",        description: "index.techno5", url: "https://vuejs.org" },
        { techno: "Vuetify",    description: "index.techno6", url: "https://next.vuetifyjs.com" }
    ];
</script>

<style scoped>
  .logo-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 32px;
    flex-wrap: nowrap;
  }

  .logo-item {
    flex: 0 1 auto;
    width: 100px;
    height: 100px;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .logo-item :deep(svg), 
  .logo-item :deep(.v-icon),
  .logo-item :deep(img) {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }

  .custom-list-item {
    border-radius: 8px !important;
    transition: all 0.3s ease;
    border: 1px solid transparent;
  }

  .custom-list-item:hover {
    background-color: rgba(var(--v-theme-primary), 0.08) !important;
    border-color: rgba(var(--v-theme-primary), 0.2);
    transform: translateX(5px);
  }

  .item-icon {
    color: rgb(var(--v-theme-primary));
    transition: transform 0.3s ease;
  }

  .custom-list-item:hover .item-icon {
    transform: scale(1.2);
  }

  .v-list {
    overflow-x: hidden;
  }

  @media (max-width: 600px) {
    .logo-wrapper {
      flex-wrap: wrap;
      justify-content: center;
      gap: 16px;
    }

    .logo-item {
      flex: 0 0 30%;
    }

    .custom-list-item:hover {
      transform: none;
    }
  }
</style>