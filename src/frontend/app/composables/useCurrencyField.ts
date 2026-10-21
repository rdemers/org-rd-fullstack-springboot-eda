/*
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
 */
import { ref, computed } from "vue";
import Decimal from "decimal.js";
import { useRules } from "@/composables/useRules";
import { useMoneyFormatter } from "@/composables/useMoneyFormatter";

/**
 * Shared "text-field-backed Decimal with a live formatted preview" behavior used by the person
 * balance and product price fields: a `text` ref to bind with `v-model`, a `formattedPreview`
 * computed for the append-inner slot, and `resolve()` to sanitize the text back into a Decimal
 * (or null, when it doesn't parse) at submit time.
 */
export function useCurrencyField(initial: Decimal | null | undefined, currency = "CAD") {

    const rules = useRules();
    const { formatMoney } = useMoneyFormatter(currency);

    const text = ref<string>(initial ? initial.toString() : "");

    const formattedPreview = computed(() => {
        const amount = rules.sanitizeToDecimal(text.value);
        return amount ? formatMoney(amount) : "";
    });

    function resolve(): Decimal | null {
        return rules.sanitizeToDecimal(text.value);
    }

    return { text, formattedPreview, resolve };
}
