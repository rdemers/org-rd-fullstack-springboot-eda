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

    <v-snackbar v-model="showSuccess" color="success" timeout="2000" location="top">
      {{ t('common.message.updateSuccess') }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, onMounted } from "vue";
    import type RequestView   from "@/types/RequestView";
    import type Request       from "@/types/Request";
    import RequestService     from "@/services/RequestService";

    const { t } = useI18n();
    const route = useRoute();

    useHead({
      title: t('request.title.edit')
    });

    const isError = ref(false);
    const isFetching = ref(true);
    const isSubmitting = ref(false);
    const showSuccess = ref(false);
    const requestView = ref<RequestView>({ 
      requestId: null, personId: null, productId: null, qty: 0, 
      operation: null, result: null, personFirstName: "", personLastName: "",  
      productCode: "", productDescription: "", 
      strOperation: "", strResult: "" });

    async function load(id: number) {
        isFetching.value = true;
        isError.value = false;
        try {
            const response = await RequestService.getView(id);
            requestView.value = response.data;
        } catch (err) {
            console.error("Error loading request:", err);
            isError.value = true;
        } finally {
            isFetching.value = false;
        }
    }

    async function update(updatedRequestView: RequestView) {
        isSubmitting.value = true;
        isError.value = false;
        try {
            const request = updatedRequestView as Request;
            await RequestService.update(request);
            showSuccess.value = true;
            setTimeout(() => {
                navigateToParent();
            }, 1000);
        } catch (err) {
            console.error("Error updating request:", err);
            isError.value = true;
        } finally {
            isSubmitting.value = false;
        }
    }

    function navigateToParent() {
        return navigateTo("/requests");
    }

    onMounted(() => {
        const idStr = route.params.id as string;
        const id = parseInt(idStr);
      
        if (!isNaN(id)) {
          load(id);
        } else {
          isError.value = true;
          isFetching.value = false;
        }
    });
</script>

<style scoped>
</style>