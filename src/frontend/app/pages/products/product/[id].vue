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
          <v-card-title class="text-h5 pa-4">{{ t('product.title.edit') }}</v-card-title>
          <v-divider/>
          <v-card-text>
            <vrd-product-form v-if="!isFetching" :product="product" v-model:isError="isError" :loading="isSubmitting" 
              @submit="update" 
              @cancel="navigateToParent"/>           
            <v-skeleton-loader v-else type="article, actions"/>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="snackbar.timeout"
                style="white-space: pre-line" location="top" @update:model-value="onSnackbarChange">
      {{ snackbar.message }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, onMounted } from "vue";
    import Decimal            from "decimal.js";
    import type Product       from "@/types/Product";
    import ProductService     from "@/services/ProductService";
    import { useSnackbar }    from "@/composables/useSnackbar";

    const { t } = useI18n();
    const route = useRoute();
    const { snackbar, notify } = useSnackbar();

    useHead({
      title: t('product.title.edit')
    });

    const isError = ref(false);
    const isFetching = ref(true);
    const isSubmitting = ref(false);
    const product = ref<Product>({ productId: null, code: "", description: "", price: new Decimal(0) });

    async function load(id: number) {
        isFetching.value = true;
        isError.value = false;
        try {
            const response = await ProductService.get(id);
            product.value = response.data;
        } catch (err) {
            isError.value = true;
            console.error("Error loading product:", err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.selectfailed", { message: msg }), "error");
        } finally {
            isFetching.value = false;
        }
    };

    async function update(updatedProduct: Product) {
        isSubmitting.value = true;
        isError.value = false;
        try {
            await ProductService.update(updatedProduct);
            notify(t("common.message.updatesuccess"));
        } catch (err) {
            isError.value = true;
            console.error("Error updating product:", err);
            const msg = err instanceof Error ? err.message : String(err);
            notify(t("common.message.updatefailed", { message: msg }), "error");
        } finally {
            isSubmitting.value = false;
        }
    };

    function onSnackbarChange(value: boolean) {
        if (!value && !isError.value) {
            navigateToParent();
        }
    };

    function navigateToParent() {
        return navigateTo("/products");
    };

    onMounted(() => {
        const idStr = route.params.id as string;
        const id = parseInt(idStr);
      
        if (!isNaN(id)) {
            load(id);
        } else {
            const msg = "Not a number";
            isError.value = true;
            notify(t("common.message.selectfailed", { message: msg }), "error");
        }
    });
</script>

<style scoped>
</style>