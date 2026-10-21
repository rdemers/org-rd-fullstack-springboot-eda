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
        <v-col cols="12" md="6">
          <v-text-field 
            v-model="localProduct.code" 
            :label="t('product.code')" 
            :rules="[rules.charRequired(t('product.code')), rules.charMin(t('product.code'), 2)]"
            variant="outlined" 
            density="comfortable"/>
        </v-col>
        <v-col cols="12" md="6">
          <v-text-field 
            v-model="localProduct.description" 
            :label="t('product.description')" 
            :rules="[rules.charRequired(t('product.description')), rules.charMin(t('product.description'), 2)]"
            variant="outlined" 
            density="comfortable"/>
        </v-col>
        <v-col cols="12">
          <v-text-field
            v-model="priceText" :label="t('product.price')"
            placeholder="0.00" variant="outlined" density="comfortable" inputmode="decimal"
            :rules="[rules.charRequired(t('product.price')), rules.currency()]">
            <template #append-inner>
              <span class="formatted-preview">{{ formattedPreview }}</span>
            </template>
          </v-text-field>
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
    import { ref }               from "vue";
    import { useI18n }           from "vue-i18n";
    import type { VForm }        from "vuetify/components";
    import type Product          from "@/types/Product";
    import { useRules }          from "@/composables/useRules";
    import { useCurrencyField }  from "@/composables/useCurrencyField";

    const props = defineProps<{
        product: Product;
        loading: boolean;
        isError: boolean;
    }>();

    const emit = defineEmits<{
        (e: "submit", product: Product):     void;
        (e: "cancel"):                       void;
        (e: "update:isError", value: boolean): void;
    }>();

    const { t }        = useI18n();
    const rules        = useRules();
    const formRef      = ref<VForm | null>(null);
    const isValid      = ref(false);
    const localProduct = ref<Product>({ ...props.product });

    const { text: priceText, formattedPreview, resolve: resolvePrice } =
        useCurrencyField(localProduct.value.price);

    const handleSubmit = async () => {
        const result = await formRef.value?.validate();
        if (!result?.valid)
            return;

        const price = resolvePrice();
        if (price === null)
            return;

        emit("submit", { ...localProduct.value, price });
    }
</script>

<style scoped>
  .formatted-preview {
    font-weight: bold;
    font-size: 0.85rem;
    color: rgb(var(--v-theme-primary));
    opacity: 0.8;
    white-space: nowrap;
  }
</style>
