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
          <v-card-title class="text-h5 pa-4">{{ t('request.title.add') }}</v-card-title>
          <v-divider/>
          <v-card-text>
            <vrd-request-form :requestView="requestView" v-model:isError="isError" 
                              :isEdit="false" :loading="isSubmitting" 
              @submit="save" 
              @cancel="navigateToParent"/>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-snackbar v-model="showSuccess" color="success" timeout="3000" location="top">
      {{ t('common.message.createSuccess') }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { ref }            from "vue";
    import type Request       from "@/types/Request";
    import type RequestView   from "@/types/RequestView";
    import RequestService     from "@/services/RequestService";

    const { t } = useI18n();

    useHead({
        title: t('request.title.add')
    });

    const isError = ref(false);
    const isSubmitting = ref(false);
    const showSuccess = ref(false);
    const requestView = ref<RequestView>({ 
      requestId: null, personId: null, productId: null, qty: 0, 
      operation: null, result: null, personFirstName: "", personLastName: "",  
      productCode: "", productDescription: "", 
      strOperation: "", strResult: "" });

    async function save(data: RequestView) {
        isSubmitting.value = true;
        isError.value = false;
        try {
            await RequestService.create(data as Request);
            showSuccess.value = true;
            setTimeout(() => navigateToParent(), 1000);
        } catch (err) {
          console.error("Error:", err);
          isError.value = true;
      } finally {
          isSubmitting.value = false;
      }
    }

    function navigateToParent() {
        return navigateTo("/requests");
    }
</script>

<style scoped>
</style>