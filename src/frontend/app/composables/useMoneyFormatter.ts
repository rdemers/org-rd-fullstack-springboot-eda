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
import { computed, toValue }     from "vue";
import type { MaybeRefOrGetter } from "vue";
import { useI18n }               from "vue-i18n";
import Decimal                   from "decimal.js";

interface MoneyFormatterOptions extends Omit<Intl.NumberFormatOptions, 'style' | 'currency'> {
    fallback?: string;
}

export function useMoneyFormatter(currency: MaybeRefOrGetter<string> = "USD",
                                  options:  MaybeRefOrGetter<MoneyFormatterOptions> = {}) {

    const { locale } = useI18n();

    const formatter = computed(() => {
        const activeLocale = toValue(locale);
        const activeCurrency = toValue(currency);
        const extraOptions = toValue(options);

        try {
            return new Intl.NumberFormat(activeLocale, {
              ...extraOptions,
              style: "currency",
              currency: activeCurrency,
            });
        } catch (e) {
            console.warn(`[useMoneyFormatter] Invalid configuration, falling back to decimal.`);
            return new Intl.NumberFormat(activeLocale, { style: "decimal" });
        }
    });

    const formatMoney = (amount: Decimal.Value | null | undefined): string => {

        const opt = toValue(options);
        if (amount === null || amount === undefined || amount === "") {
            return opt.fallback ?? "";
        }

        try {
            const decimalValue = new Decimal(amount);
            if (!decimalValue.isFinite()) 
                return opt.fallback ?? "—";

            return formatter.value.format(decimalValue.toNumber());
        } catch {
            return opt.fallback ?? "—";
        }
    };

    const formatMoneyShort = (amount: Decimal.Value | null | undefined): string => {
    
        const fallback = toValue(options).fallback ?? "—";

        if (amount == null || amount === "") {
            return fallback;
        }

        try {
            const val = new Decimal(amount);
            if (!val.isFinite() || val.isNaN()) {
                return fallback;
            }

            const activeLocale   = toValue(locale);
            const activeCurrency = toValue(currency);

            const nf = new Intl.NumberFormat(activeLocale, {
                notation: "compact",
                compactDisplay: "short",  // "short" → 1.2k, "long" → 1.2 thousand.
                style: "currency",
                currency: activeCurrency,
                maximumFractionDigits: 2,
                minimumFractionDigits: 0, // Let Intl round up intelligently.
            });

            return nf.format(val.toNumber());
        } catch (err) {
            console.warn("[formatMoneyShort] Format error:", err);
            return fallback;
        }
    };

    return {
        formatMoney,
        formatMoneyShort,
        formatter
    };
}