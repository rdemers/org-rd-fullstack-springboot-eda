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
import { ref, watch } from "vue";
import { useDebounceFn } from "@vueuse/core";

export interface UseEntitySearchOptions<T, D> {
    fetchAll: () => Promise<{ data: T[] }>;
    fetchByQuery: (query: string) => Promise<{ data: T[] }>;
    mapToDisplay: (item: T) => D;
    debounceMs?: number;
    /** Display label of the currently selected item, if any — skips a redundant re-search
     *  right after a selection sets the autocomplete's search text to that same label. */
    selectedDisplay?: () => string | undefined;
}

/**
 * Shared "autocomplete search" behavior for a `v-autocomplete` backed by a debounced service
 * call (used by the person/product pickers in the request and inventory forms): a `search` ref
 * to bind with `v-model:search`, an `items` ref of display-mapped results, a `loading` ref, and
 * `runSearch` to trigger a search directly (e.g. the initial unfiltered load on mount).
 *
 * Guards against out-of-order responses: if the user types quickly, a later search's result can
 * resolve before an earlier one — only the most recently issued search's result is ever applied,
 * so the list never ends up showing results for a query the user has since changed.
 */
export function useEntitySearch<T, D>(options: UseEntitySearchOptions<T, D>) {
    const { fetchAll, fetchByQuery, mapToDisplay, debounceMs = 300, selectedDisplay } = options;

    const items   = ref<D[]>([]);
    const loading = ref(false);
    const search  = ref("");

    let requestSeq = 0;

    async function runSearch(query: string) {
        if (typeof query !== "string") {
            console.error("Invalid query string.");
            return;
        }

        const seq = ++requestSeq;
        loading.value = true;
        try {
            const response = (query.length === 0) ? await fetchAll() : await fetchByQuery(query);
            if (seq !== requestSeq)
                return; // A newer search has since been issued — discard this stale result.

            items.value = response.data.map(mapToDisplay);
        } catch (err) {
            console.error("Error searching:", err);
        } finally {
            if (seq === requestSeq)
                loading.value = false;
        }
    }

    const debouncedSearch = useDebounceFn((query: string) => runSearch(query), debounceMs);

    watch(search, (val) => {
        if (selectedDisplay && selectedDisplay() === val)
            return; // selection in progress.

        debouncedSearch(val ?? "");
    });

    return { items, loading, search, runSearch, debouncedSearch };
}
