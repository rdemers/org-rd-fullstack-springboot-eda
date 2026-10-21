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

interface DeleteConfirmOptions {
    deleteFn:  (id: number) => Promise<unknown>;
    onDeleted: () => Promise<void> | void;
    notify:    (message: string, color?: SnackbarColor) => void;
}

// Shared list-page delete-confirmation flow (persons/products/requests/inventories).
export function useDeleteConfirm(options: DeleteConfirmOptions) {

    const { t } = useI18n();

    const deleteDialog = ref(false);
    const idToDelete    = ref<number | null>(null);

    function openDeleteDialog(id: number | null) {
        idToDelete.value = id;
        deleteDialog.value = true;
    }

    function closeDeleteDialog() {
        deleteDialog.value = false;
        idToDelete.value = null;
    }

    async function deleteID() {
        if (idToDelete.value === null)
            return;

        try {
            await options.deleteFn(idToDelete.value);
            options.notify(t("common.message.delete-success"));
            await options.onDeleted();
        } catch (err) {
            console.error(err);
            const msg = err instanceof Error ? err.message : String(err);
            options.notify(t("common.message.delete-failed", { message: msg }), "error");
        } finally {
            closeDeleteDialog();
        }
    }

    return { deleteDialog, idToDelete, openDeleteDialog, closeDeleteDialog, deleteID };
}
