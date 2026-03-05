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
import { ref, type Ref } from "vue";

export type SnackbarColor = "success" | "error" | "info" | "warning";
export interface Snackbar {
    show: boolean;
    message: string;
    color: SnackbarColor;
    timeout: number;
}

export function useSnackbar(defaultTimeout = 3000) {

   const snackbar: Ref<Snackbar> = ref({
        show: false,
        message: "",
        color: "success",
        timeout: defaultTimeout
    });
    
    const notify = (message: string, color: SnackbarColor = "success", timeout = defaultTimeout) => {
        snackbar.value.show    = true;
        snackbar.value.message = message;
        snackbar.value.color   = color;
        snackbar.value.timeout = timeout;
    }

    const success = (message: string, timeout?: number) => notify(message, "success", timeout);
    const error   = (message: string, timeout?: number) => notify(message, "error", timeout);
    const info    = (message: string, timeout?: number) => notify(message, "info", timeout);
    const warning = (message: string, timeout?: number) => notify(message, "warning", timeout);

    return {
        snackbar,
        notify,
        success,
        error,
        info,
        warning
    };
}