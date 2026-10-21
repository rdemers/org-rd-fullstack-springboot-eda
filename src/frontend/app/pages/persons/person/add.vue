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
        <v-card class="mt-4 elevation-2">
          <v-card-title class="text-h5 pa-4">{{ t('person.title.add') }}</v-card-title>
          <v-divider/>
          <v-card-text>
            <vrd-person-form :person="person" :loading="isSubmitting" 
              @submit="save" 
              @cancel="navigateToParent"/>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="snackbar.timeout"
                style="white-space: pre-line" location="top" timer="bottom" timer-color="white" 
                @update:model-value="onSnackbarChange">
      {{ snackbar.message }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { ref }                from "vue";
    import Decimal                from "decimal.js";
    import type Person            from "@/types/Person";
    import PersonService          from "@/services/PersonService";
    import { useSnackbar }        from "@/composables/useSnackbar"
    import { useCrudCreatePage }  from "@/composables/useCrudCreatePage";

    const { t } = useI18n();
    const { snackbar, notify } = useSnackbar();

    useHead({
        title: t('person.title.add')
    });

    const person = ref<Person>({ personId: null, firstName: "", lastName: "", balance: new Decimal(0) });

    const { isSubmitting, save, onSnackbarChange } = useCrudCreatePage({
        create:     (data: Person) => PersonService.create(data),
        notify,
        parentPath: "/persons"
    });

    function navigateToParent() {
        return navigateTo("/persons");
    }
</script>

<style scoped>
</style>