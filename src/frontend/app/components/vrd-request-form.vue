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
      <v-autocomplete hide-details="auto" v-model="selectedPerson" v-model:search="personSearch"
        :items="persons" 
        :loading="loadingPersons" 
        :label="t('request.person')" 
        :no-data-text="t('common.label.no-data')"
        :rules="[rules.charRequired(t('request.person'))]" 
        item-value="personId"
        item-title="displayPerson" 
        variant="outlined" 
        density="comfortable"
        return-object clearable
        @update:model-value="onPersonSelected"/>
    </v-row>
    <v-row>
      <v-autocomplete hide-details="auto" v-model="selectedProduct" v-model:search="productSearch"
        :items="products" 
        :loading="loadingProducts" 
        :label="t('request.product')"
        :no-data-text="t('common.label.no-data')"
        :rules="[rules.charRequired(t('request.product'))]" 
        item-value="productId"
        item-title="displayProduct"
        variant="outlined" 
        density="comfortable"
        return-object clearable
        @update:model-value="onProductSelected"/>
    </v-row>
    <v-row>
      <v-text-field hide-details="auto" v-model.number="localRequestView.qty"
        :label="t('request.qty')" type="number" min="1"
        :rules="[rules.numRequired(t('request.qty')), rules.integer, rules.positive]"
        variant="outlined" density="comfortable"/>
    </v-row>
    <v-row>
      <v-col cols="6" class="pr-1">
        <v-select hide-details="auto" v-model="localRequestView.operation"
          :items="operationItems" 
          :label="t('request.operation')" 
          :rules="[rules.charRequired(t('request.operation'))]" 
          variant="outlined" density="comfortable"/>
      </v-col>
      <v-col cols="6" class="pl-1">
        <v-select hide-details="auto" v-model="localRequestView.result"
          :items="resultItems" 
          :label="t('request.result')"
          :rules="[rules.charRequired(t('request.result'))]" 
          variant="outlined" density="comfortable">
          <template #item="{ item, props: itemProps }">
            <v-list-item v-bind="itemProps" title="">
              <template #prepend>
                <v-chip :color="getResultColor(item.value)" 
                        size="small" label variant="flat">
                  {{ item.title }}
                </v-chip>
              </template>
            </v-list-item>
          </template>
          <template #selection="{ item }">
            <v-chip :color="getResultColor(item.value)" 
                    size="small" label variant="flat">
              {{ item.title }}
            </v-chip>
          </template>
        </v-select>
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
    import { ref, onMounted }             from "vue";
    import { useI18n }                    from "vue-i18n";
    import type { VForm }                 from "vuetify/components";
    import { useRules }                   from "@/composables/useRules";
    import { useEntitySearch }            from "@/composables/useEntitySearch";
    import { getOperations }              from "@/types/Operation";
    import { getResults, getResultColor } from "@/types/Result";

    import type RequestView          from "@/types/RequestView";
    import type Person               from "@/types/Person";
    import type Product              from "@/types/Product";
    import PersonService             from "@/services/PersonService";
    import ProductService            from "@/services/ProductService";
    import Decimal                   from "decimal.js";

    interface PersonWithDisplay extends Person {
      displayPerson: string
    }

    interface ProductWithDisplay extends Product {
      displayProduct: string
    }

    const props = defineProps<{
      requestView: RequestView;
      isEdit:      boolean;
      loading:     boolean;
      isError:     boolean;
    }>()

    const emit = defineEmits<{
      (e: "submit",         payload: RequestView): void;
      (e: "cancel"):                               void;
      (e: "update:isError", value:  boolean):      void;
    }>()

    const { t }            = useI18n();
    const rules            = useRules();
    const formRef          = ref<VForm | null>(null);
    const isValid          = ref(false);
    const localRequestView = ref<RequestView>({ ...props.requestView });

    const selectedPerson  = ref<PersonWithDisplay | null>(null);
    const selectedProduct = ref<ProductWithDisplay | null>(null);

    const {
        items: persons, loading: loadingPersons, search: personSearch, runSearch: searchPersons
    } = useEntitySearch<Person, PersonWithDisplay>({
        fetchAll:     () => PersonService.getAll(),
        fetchByQuery: (query) => PersonService.findByFirstName(query),
        mapToDisplay: (p) => ({ ...p, displayPerson: `${p.firstName} - ${p.lastName}` }),
        selectedDisplay: () => selectedPerson.value?.displayPerson
    });

    const {
        items: products, loading: loadingProducts, search: productSearch, runSearch: searchProducts
    } = useEntitySearch<Product, ProductWithDisplay>({
        fetchAll:     () => ProductService.getAll(),
        fetchByQuery: (query) => ProductService.findByCode(query),
        mapToDisplay: (p) => ({ ...p, displayProduct: `${p.code} – ${p.description}` }),
        selectedDisplay: () => selectedProduct.value?.displayProduct
    });

    onMounted(() => {
        searchPersons("");
        searchProducts("");

        if (localRequestView.value.personId) {
          selectedPerson.value = {
              personId:      localRequestView.value.personId,
              firstName:     localRequestView.value.personFirstName,
              lastName:      localRequestView.value.personLastName,
              displayPerson: `${localRequestView.value.personFirstName} – ${localRequestView.value.personLastName}`,
              balance:       new Decimal(0)
          }
          persons.value = [selectedPerson.value];
        }

        if (localRequestView.value.productId) {
          selectedProduct.value = {
              productId:      localRequestView.value.productId,
              code:           localRequestView.value.productCode,
              description:    localRequestView.value.productDescription,
              displayProduct: `${localRequestView.value.productCode} – ${localRequestView.value.productDescription}`,
              price:          new Decimal(0)
          }
          products.value = [selectedProduct.value];
        }
    })

    const onPersonSelected = (person: Person | null) => {
        localRequestView.value.personId        = person?.personId  ?? null;
        localRequestView.value.personFirstName = person?.firstName ?? "";
        localRequestView.value.personLastName  = person?.lastName  ?? "";
    }

    const onProductSelected = (product: Product | null) => {
        localRequestView.value.productId          = product?.productId   ?? null;
        localRequestView.value.productCode        = product?.code        ?? "";
        localRequestView.value.productDescription = product?.description ?? "";
    }

    const operationItems = getOperations().map(op => ({
        key: op.translationKey,
        title: t(op.translationKey),
        value: op.value
    }))

    const resultItems = getResults().map(re => ({
        key: re.translationKey,
        title: t(re.translationKey),
        value: re.value
    }))

    const handleSubmit = async () => {
        const result = await formRef.value?.validate();
        if (!result?.valid) 
            return;

        emit("submit", { ...localRequestView.value });
    }
</script>

<style scoped>
</style>