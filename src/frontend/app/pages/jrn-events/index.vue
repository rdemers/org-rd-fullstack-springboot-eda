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
    <v-window v-model="tab">
      <v-window-item value="journal">
        <vrd-jrn-event-list/>
      </v-window-item>
      <!-- eager: keeps the findings (and the tab badge) alive while the journal tab is shown. -->
      <v-window-item value="findings" eager>
        <vrd-finding-list ref="findingTable"/>
      </v-window-item>
    </v-window>

    <!-- Tabs at the bottom of the window. -->
    <v-tabs v-model="tab" grow density="compact" color="primary" class="border-t mt-4">
      <v-tab value="journal" prepend-icon="mdi-book-open-variant">
        {{ t('jrn-event.tab.journal') }}
      </v-tab>
      <v-tab value="findings" prepend-icon="mdi-clipboard-check-outline">
        {{ t('jrn-event.tab.conciliation') }}
        <v-badge v-if="errorCount > 0" :content="errorCount" color="error" inline />
      </v-tab>
    </v-tabs>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, computed } from "vue";
    import { useI18n }       from "vue-i18n";

    const { t } = useI18n();

    const tab          = ref<"journal" | "findings">("journal");
    const findingTable = ref<{ errorCount: number } | null>(null);
    const errorCount   = computed(() => findingTable.value?.errorCount ?? 0);
</script>

<style scoped>
</style>
