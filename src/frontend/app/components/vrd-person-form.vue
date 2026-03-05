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
    <v-form v-model="isValid" @submit.prevent="submit">
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
            v-model="balanceInput" :label="t('person.balance')" 
            placeholder="0.00" variant="outlined" inputmode="decimal"
            :rules="[rules.numRequired(t('person.balance')), rules.currency()]">
            <template #append-inner>
              <span class="formatted-preview">{{ formattedPreview }}</span>
            </template>
          </v-text-field>
        </v-col>
      </v-row>
      <v-divider class="my-4"/>
      <div class="d-flex justify-end ga-2">
        <v-btn variant="text" :disabled="loading" @click="emit('cancel')">
          {{ t('common.button.cancel') }}
        </v-btn>
        <v-btn type="submit" color="primary" :disabled="!isValid" :loading="loading">
          {{ t('common.button.ok') }}
        </v-btn>
      </div>
    </v-form>
  </v-container>
</template>

<script setup lang="ts">
    import { ref, computed, watch } from "vue";
    import { useI18n }              from "vue-i18n";
    import Decimal                  from "decimal.js";
    import type Person              from "@/types/Person";
    import { useMoneyFormatter }    from "@/composables/useMoneyFormatter";
    import { useRules }             from "@/composables/useRules";

    const props = defineProps<{
      person: Person;
      loading: boolean;
    }>();

    const emit = defineEmits<{
      (e: "submit", person: Person): void;
      (e: "cancel"): void;
    }>();

    const { t } = useI18n();
    const { formatMoney } = useMoneyFormatter("CAD");
    const rules = useRules();
    const isValid = ref(false);
    const localPerson = ref<Person>({ ...props.person });

    const balanceInput = computed<string>({
        get() {
            return localPerson.value.balance?.toString().replace('.', ',') ?? "";
        },
        set(val: string) {
            const cleaned = val
                .replace(/[^0-9.,-]/g, '')       // Keep numbers and separators.
                .replace(/(?!^)-/g, '')          // One less at the beginning.
                .replace(/([.,].*)[.,]/g, '$1'); // A single separator.
            
            // Update only if an intermediate format is acceptable.
            // (allows typing "123," while typing).
            if (/^-?\d*[.,]?\d*$/.test(cleaned) || cleaned === '-' || cleaned === '') {
                try {
                    if (cleaned && cleaned !== '-') {
                        const normalized = cleaned.replace(',', '.');
                        localPerson.value.balance = new Decimal(normalized);
                    }
                } catch {
                    // Ignore intermediate input errors.
                }
            }
            
          return cleaned;
      }
    });

    watch(() => props.person, (newVal) => {
        localPerson.value = { ...newVal };
    },{ immediate: true });

    const formattedPreview = computed(() => {
        const value = balanceInput.value;
        if (!value) 
          return "";
        
        return formatMoney(value);
    });

    function submit() {
      if (isValid.value) {
        emit("submit",  { ...localPerson.value });
      }
    };
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