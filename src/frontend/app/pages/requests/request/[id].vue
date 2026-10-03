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
        <v-card :loading="isFetching" class="mt-4 elevation-2">
          <v-card-title class="text-h5 pa-4">{{ t('request.title.edit') }}</v-card-title>
          <v-divider/>
          <v-card-text>
            <vrd-request-form v-if="!isFetching" :requestView="requestView" v-model:isError="isError" 
                                                 :is-edit="true" :loading="isSubmitting" 
              @submit="update" 
              @cancel="navigateToParent"/>           
            <v-skeleton-loader v-else type="article, actions"/>
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
    import { useSnackbar }        from "@/composables/useSnackbar";
    import { useCrudDetailPage }  from "@/composables/useCrudDetailPage";

    import type RequestView   from "@/types/RequestView";
    import type Request       from "@/types/Request";
    import RequestService     from "@/services/RequestService";

    const { t } = useI18n();
    const { snackbar, notify } = useSnackbar();

    useHead({
      title: t('request.title.edit')
    });

    const requestView = ref<RequestView>({
      requestId: null, personId: null, productId: null, qty: 0,
      operation: 0, result: 0,
      personFirstName: "", personLastName: "",
      productCode: "", productDescription: "" });

    const { isFetching, isSubmitting, isError, update, onSnackbarChange } = useCrudDetailPage({
        entity:     requestView,
        load:       (id) => RequestService.getView(id),
        update:     (data) => RequestService.update(data as Request),
        notify,
        parentPath: "/requests"
    });

    function navigateToParent() {
        return navigateTo("/requests");
    }
</script>

<style scoped>
</style>