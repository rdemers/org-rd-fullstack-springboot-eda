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

interface CrudListPageOptions<T> {
    items:      Ref<T[]>;
    fetchAll:   () => Promise<AxiosResponse<T[]> | undefined | null>;
    /** Called with the formatted error message — route it to a snackbar or a local alert ref. */
    onError?:   (message: string) => void;
    /** Called after items are refreshed successfully — e.g. to clear a previous error alert. */
    onSuccess?: () => void;
}

// Shared "fetch the list on mount" flow used by every persons/products/inventories/requests/
// jrn-events list page.
export function useCrudListPage<T>(options: CrudListPageOptions<T>) {

    const { t } = useI18n();
    const loading = ref(false);

    async function retrieve() {
        loading.value = true;

        try {
            const response = await options.fetchAll();
            options.items.value = (!response || !response.data) ? [] : response.data;
            options.onSuccess?.();
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            options.onError?.(t("common.message.select-failed", { message: msg }));
        } finally {
            loading.value = false;
        }
    }

    onMounted(retrieve);

    return { loading, retrieve };
}
