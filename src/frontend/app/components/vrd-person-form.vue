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
    <v-form ref="formRef" v-model="isValid" @submit.prevent="handleSubmit">
      <v-row>
        <v-col cols="12" md="6">
          <v-text-field
            v-model="localPerson.firstName" :label="t('person.firstname')"
            :rules="[rules.charRequired(t('person.firstname')), rules.charMin(t('person.firstname'), 2)]"
            variant="outlined" autofocus/>
        </v-col>        
        <v-col cols="12" md="6">
          <v-text-field 
            v-model="localPerson.lastName" :label="t('person.lastname')" 
            :rules="[rules.charRequired(t('person.lastname')), rules.charMin(t('person.lastname'), 2)]"
            variant="outlined"/>
        </v-col>
        <v-col cols="12">
          <v-text-field
            v-model="balanceText" :label="t('person.balance')"
            placeholder="0.00" variant="outlined" inputmode="decimal"
            :rules="[rules.charRequired(t('person.balance')), rules.currency()]">
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
    import { ref }                from "vue";
    import { useI18n }            from "vue-i18n";
    import type { VForm }         from "vuetify/components";
    import type Person            from "@/types/Person";
    import { useRules }           from "@/composables/useRules";
    import { useCurrencyField }   from "@/composables/useCurrencyField";

    const props = defineProps<{
      person:  Person;
      loading: boolean;
    }>();

    const emit = defineEmits<{
      (e: "submit", person: Person): void;
      (e: "cancel"):                 void;
    }>();

    const { t }       = useI18n();
    const rules       = useRules();
    const formRef     = ref<VForm | null>(null);
    const isValid     = ref(false);
    const localPerson = ref<Person>({ ...props.person });

    const { text: balanceText, formattedPreview, resolve: resolveBalance } =
        useCurrencyField(localPerson.value.balance);

    const handleSubmit = async () => {
        const result = await formRef.value?.validate();
        if (!result?.valid)
            return;

        const balance = resolveBalance();
        if (balance === null)
            return;

        emit("submit", { ...localPerson.value, balance });
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
