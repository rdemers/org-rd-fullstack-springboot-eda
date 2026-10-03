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
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import type { SnackbarColor } from "@/composables/useSnackbar";

interface CrudCreatePageOptions<T> {
    create:     (data: T) => Promise<unknown>;
    notify:     (message: string, color?: SnackbarColor) => void;
    parentPath: string;
}

// Shared "submit a new entity, notify, navigate back on success" flow used by
// every persons/products/requests/inventories add page.
export function useCrudCreatePage<T>(options: CrudCreatePageOptions<T>) {

    const { t } = useI18n();

    const isSubmitting = ref(false);
    const isError      = ref(false);

    async function save(data: T) {
        isSubmitting.value = true;
        isError.value = false;
        try {
            await options.create(data);
            options.notify(t("common.message.create-success"));
        } catch (err) {
            isError.value = true;
            console.error("Error creating entity:", err);
            const msg = err instanceof Error ? err.message : String(err);
            options.notify(t("common.message.create-failed", { message: msg }), "error");
        } finally {
            isSubmitting.value = false;
        }
    }

    function onSnackbarChange(value: boolean) {
        if (!value && !isError.value) {
            navigateTo(options.parentPath);
        }
    }

    return { isSubmitting, isError, save, onSnackbarChange };
}
