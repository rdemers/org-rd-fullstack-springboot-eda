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
import { ref, onMounted, type Ref } from "vue";
import { useI18n } from "vue-i18n";
import type { AxiosResponse } from "axios";
import type { SnackbarColor } from "@/composables/useSnackbar";

interface CrudDetailPageOptions<T> {
    entity:     Ref<T>;
    load:       (id: number) => Promise<AxiosResponse<T>>;
    update:     (data: T) => Promise<unknown>;
    notify:     (message: string, color?: SnackbarColor) => void;
    parentPath: string;
}

// Shared "load entity by route id, then edit/update it" flow used by every
// persons/products/requests/inventories detail page.
export function useCrudDetailPage<T>(options: CrudDetailPageOptions<T>) {

    const { t }   = useI18n();
    const route   = useRoute();

    const isFetching   = ref(true);
    const isSubmitting = ref(false);
    const isError      = ref(false);

    async function load(id: number) {
        isFetching.value = true;
        isError.value = false;
        try {
            const response = await options.load(id);
            options.entity.value = response.data;
        } catch (err) {
            isError.value = true;
            console.error("Error loading entity:", err);
            const msg = err instanceof Error ? err.message : String(err);
            options.notify(t("common.message.select-failed", { message: msg }), "error");
        } finally {
            isFetching.value = false;
        }
    }

    async function update(data: T) {
        isSubmitting.value = true;
        isError.value = false;
        try {
            await options.update(data);
            options.notify(t("common.message.update-success"));
        } catch (err) {
            isError.value = true;
            console.error("Error updating entity:", err);
            const msg = err instanceof Error ? err.message : String(err);
            options.notify(t("common.message.update-failed", { message: msg }), "error");
        } finally {
            isSubmitting.value = false;
        }
    }

    function onSnackbarChange(value: boolean) {
        if (!value && !isError.value) {
            navigateTo(options.parentPath);
        }
    }

    onMounted(() => {
        const idStr = route.params.id as string;
        const id = parseInt(idStr, 10);

        if (!isNaN(id)) {
            load(id);
        } else {
            isError.value = true;
            isFetching.value = false;
            options.notify(t("common.message.select-failed", { message: "Not a number" }), "error");
        }
    });

    return { isFetching, isSubmitting, isError, update, onSnackbarChange };
}
