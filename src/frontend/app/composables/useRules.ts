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
import { useI18n } from "vue-i18n";
import Decimal     from "decimal.js";

export function useRules() {

    const { t } = useI18n();

    const charRequired = (label: string) => (v: any) => !!v || t('common.rule.required', { field: label });
    const numRequired = (label?: string) => (v: number | null) => v !== null && v !== undefined || t("common.rule.required", { field: label });

    const integer = (v: number | null) => Number.isInteger(v) || t("common.rule.integer");
    const positive = (v: number | null) => v !== null && v > 0 || t("common.rule.positive");

    const charMin = (label: string, min: number) => (v: string) => (!v || v.length >= min) || t('common.rule.min', { field: label, min });
    const charMax = (label: string, max: number) => (v: string) => (!v || v.length <= max) || t('common.rule.max', { field: label, max });

    const numMin = (minValue: number) => (v: number | null) => v !== null && v >= minValue || t("common.rule.min", { min: minValue });
    const numMax = (maxValue: number) => (v: number | null) => v !== null && v <= maxValue || t("common.rule.max", { max: maxValue });

    const precision = (decimals: number = 2) => (v: string | number) => {

        if (!v && v !== 0) 
            return true;
    
        const str = v.toString().trim();
    
        if ((str.match(/[.,]/g) || []).length > 1)
            return true;
    
        const parts = str.split(/[.,]/);
    
        // Guard clause explicite.
        if (parts.length !== 2) {
            return true;
        }
    
        const fraction = parts[1];
        if (fraction === undefined)
            return true;
        else
            if (fraction.length > decimals) 
                return t('common.rule.maxdecimals', { decimals });
    
        return true;
    };

    const currency = (opts = { min: 0, max: undefined, decimals: 2 }) => (v: any) => {

        if (v === "" || v === null || v === undefined) 
              return true;

        const str = v.toString().trim().replace(",", ".").replace(/\s/g, "");
        
        try {
            const d = new Decimal(str);
            
            if (d.isNaN() || !d.isFinite()) 
                return t('common.rule.invalidamount');
            
            if (d.decimalPlaces() > opts.decimals) 
                return t('common.rule.maxdecimals', { decimals: opts.decimals });
            
            if (opts.min !== undefined && d.lessThan(opts.min)) 
                return t('common.rule.min', { min: opts.min });

            if (opts.max !== undefined && d.greaterThan(opts.max)) 
                return t('common.rule.max', { max: opts.max });

            return true;
        } catch {
            return t('common.rule.invalidamount');
        }
    };

    const sanitizeToDecimal = (v: string | number | null): Decimal | null => {

        if (v === null || v === undefined || v === '') 
            return null;

        let str = v.toString().trim();

        // 1. Remove currency symbols and non-breaking spaces.
        str = str.replace(/[$\s\xA0]/g, "");

        // 2. Handle European/Quebec formats (e.g., 1.250,50)
        // If we have both a dot and a comma, we assume the last one is the decimal separator.
        if (str.includes(",") && str.includes(".")) {

            const lastComma = str.lastIndexOf(",");
            const lastDot = str.lastIndexOf(".");
          
            if (lastComma > lastDot) {
                str = str.replace(/\./g, "").replace(",", "."); // Format 1.250,50 -> 1250.50.
            } else {
                str = str.replace(/,/g, ""); // Format 1,250.50 -> 1250.50.
            }
        } else {
            // If there is only one type of separator, normalize the comma to a dot.
            str = str.replace(",", ".");
        }

        try {
            const d = new Decimal(str);
            return d.isFinite() ? d : null;
        } catch {
            return null;
        }
    };

  return {
    charRequired,
    numRequired,
    integer,
    positive,
    charMin,
    charMax,
    numMin,
    numMax,
    currency,
    precision,
    sanitizeToDecimal
  };
};

// Example of use : sanitizeToDecimal.
//
//  const finalAmount = sanitizeToDecimal(displayAmount.value);
//  if (finalAmount) {
//    const payload = {
//      amount: finalAmount.toNumber();
//    };
//
//
// Example of use : VuesJS.
//
//  <v-text-field v-model="form.amount"
//    :rules="[charRequired('Montant'), rules.money({ min: 0, decimals: 2 })]"
//    label="Montant de la transaction" @blur="onBlurFormat"
//  />