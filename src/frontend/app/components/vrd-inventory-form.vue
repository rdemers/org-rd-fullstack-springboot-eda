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
    <v-alert v-if="isError" type="error" variant="tonal" closable class="mb-6"
             @click:close="emit('update:isError', false)">
      {{ t('common.alert.text') }}
    </v-alert>
    <v-form ref="formRef" v-model="isValid" @submit.prevent="handleSubmit">
      <v-row>
        <v-col v-if="isEdit" cols="12">
          <v-text-field 
            v-model="localInventoryView.productDescription" 
            :label="t('inventory.product-descr')"
            variant="outlined" 
            density="comfortable"
            prepend-inner-icon="mdi-lock-outline"
            readonly
            disabled/>
        </v-col>
        <v-col v-else cols="12">
          <v-autocomplete hide-details="auto" v-model="selectedProduct" v-model:search="productSearch"
            :items="products" 
            :loading="loadingProducts" 
            :label="t('inventory.product-descr')"
            :no-data-text="t('common.label.no-data')"
            :rules="[rules.charRequired(t('inventory.product-descr'))]"
            item-value="productId"
            item-title="displayProduct"
            variant="outlined" 
            density="comfortable"
            return-object clearable
            @update:model-value="onProductSelected"/>
        </v-col>
        <v-col cols="12">
          <v-text-field v-model.number="localInventoryView.qty"
            type="number" variant="outlined" density="comfortable" min="1"
            :label="t('inventory.qty')"
            :rules="[rules.numRequired(t('inventory.qty')), rules.integer, rules.positive]"/>
        </v-col>
      </v-row>
      <v-divider class="my-4"/>
      <div class="d-flex justify-end ga-2">
        <v-btn variant="text" color="secondary" :disabled="loading" @click="emit('cancel')">
          {{ t('common.button.cancel') }}
        </v-btn>
        <v-btn type="submit" color="primary" :disabled="!isValid" :loading="loading" min-width="120">
          {{ t('common.button.ok') }}
        </v-btn>
      </div>
    </v-form>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, onMounted } from "vue";
    import { useI18n }               from "vue-i18n";
    import type { VForm }            from "vuetify/components";
    import type InventoryView        from "@/types/InventoryView";
    import type Product              from "@/types/Product";
    import ProductService            from "@/services/ProductService";
    import { useRules }              from "@/composables/useRules";
    import { useEntitySearch }       from "@/composables/useEntitySearch";

    interface ProductWithDisplay extends Product {
      displayProduct: string;
    }

    const props = defineProps<{
        inventoryView: InventoryView;
        isEdit:        boolean;
        loading:       boolean;
        isError:       boolean;
    }>();

    const emit = defineEmits<{
        (e: "submit", inventoryView: InventoryView): void;
        (e: "cancel"):                               void;
        (e: "update:isError", value: boolean):       void;
    }>();

    const { t }                   = useI18n();
    const rules                   = useRules();
    const isValid                 = ref(false);
    const formRef                 = ref<VForm | null>(null);
    const selectedProduct         = ref<ProductWithDisplay | null>(null);
    const localInventoryView      = ref<InventoryView>({ ...props.inventoryView });

    const {
        items: products, loading: loadingProducts, search: productSearch, runSearch: searchProducts
    } = useEntitySearch<Product, ProductWithDisplay>({
        fetchAll:     () => ProductService.getAll(),
        fetchByQuery: (query) => ProductService.findByCode(query),
        mapToDisplay: (p) => ({ ...p, displayProduct: `${p.code} – ${p.description}` }),
        selectedDisplay: () => selectedProduct.value?.displayProduct
    });

    onMounted(() => {
        if (props.isEdit)
          return;

        searchProducts("");
    });

    const onProductSelected = (product: ProductWithDisplay | null) => {
        localInventoryView.value.productId          = product?.productId   ?? null;
        localInventoryView.value.productCode        = product?.code        ?? "";
        localInventoryView.value.productDescription = product?.description ?? "";
    }

    const handleSubmit = async () => {
        const result = await formRef.value?.validate();
        if (!result?.valid) 
            return;

        emit("submit", { ...localInventoryView.value });
    }
</script>

<style scoped>
</style>
