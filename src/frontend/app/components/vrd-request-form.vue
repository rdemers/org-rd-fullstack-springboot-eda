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
    <v-form v-if="!isError" v-model="isValid" @submit.prevent="submit">
      <v-row>
        <v-col v-if="isEdit" cols="12">
          <v-text-field 
            v-model="localRequestView.productDescription" 
            :label="t('inventory.productdescr')"
            variant="outlined" 
            density="comfortable"
            prepend-inner-icon="mdi-lock-outline"
            readonly
            disabled/>
        </v-col>
        <v-col v-else cols="12">
          <v-autocomplete
            v-model="selectedProduct"
            :items="products"
            :loading="loadingProducts"
            :label="t('inventory.productdescr')"
            item-title="displayLabel"
            item-value="productId"
            variant="outlined"
            density="comfortable"
            return-object
            clearable
            prepend-inner-icon="mdi-magnify"
            :rules="[rules.charRequired(t('inventory.productdescr'))]">
            <template #item="{ props, item }">
              <v-list-item v-bind="props" :subtitle="item.raw.description"/>
            </template>
          </v-autocomplete>
        </v-col>
        <v-col cols="12">
          <v-text-field 
            v-model.number="localRequestView.qty" 
            :label="t('inventory.qty')"
            type="number"
            variant="outlined" 
            density="comfortable"
            min="1"
            :rules="[rules.numRequired(t('inventory.qty')), rules.positive]"/>
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
    import { ref, watch, onMounted } from "vue";
    import { useI18n }               from "vue-i18n";
    import type RequestView          from "@/types/RequestView";
    import type Person               from "@/types/Person";
    import type Product              from "@/types/Product";
    import ProductService            from "@/services/ProductService";
    import PersonService             from "@/services/PersonService";
    import { useRules }              from "@/composables/useRules";

    const props = defineProps<{
        requestView: RequestView;
        isError: boolean;
        isEdit: boolean;
        loading: boolean;
    }>();

    const emit = defineEmits<{
        (e: "submit", requestView: RequestView): void;
        (e: "cancel"): void;
        (e: "update:isError", value: boolean): void;
    }>();

    const { t } = useI18n();
    const isValid = ref(false);
    const loadingProducts = ref(false);
    const products = ref<Product[]>([]);
    const selectedProduct = ref<Product | null>(null);
    const persons = ref<Person[]>([]);
    const selectedPerson = ref<Person | null>(null);
    const rules = useRules();
    const localRequestView = ref<RequestView>({ ...props.requestView });

    watch(() => props.requestView, (newVal) => {
      localRequestView.value = { ...newVal };
    }, { deep: true });

    onMounted(async () => {
        if (props.isEdit) return;

        loadingProducts.value = true;
        try {
            const response = await ProductService.getAll();
            products.value = response.data.map((p: Product) => ({
              ...p, 
              displayLabel: `${p.code} - ${p.description}`
          }));
        } catch (err) {
            console.error("Error loading products", err);
        } finally {
            loadingProducts.value = false;
        }
    });

    watch(selectedProduct, (newProd) => {
      if (newProd) {
          localRequestView.value.productId = newProd.productId;
          localRequestView.value.productCode = newProd.code;
          localRequestView.value.productDescription = newProd.description;
      } else {
          localRequestView.value.productId = null;
          localRequestView.value.productCode = "";
          localRequestView.value.productDescription = "";
      }
    });

  function submit() {
    if (isValid.value) {
      emit("submit", { ...localRequestView.value });
    }
  }
</script>

<style scoped>
</style>