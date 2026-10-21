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
import axios, { type AxiosInstance, AxiosError } from "axios";
import type Session                              from "@/types/Session";
import { SessionStore }                          from "@/store/SessionStore";

export default defineNuxtPlugin(() => {
    const config = useRuntimeConfig();

    const apiAuth: AxiosInstance = axios.create({
        baseURL: config.public.authURL,
        headers: {
            "Content-type": "application/json",
            "Accept": "application/json"
        }
    });

    const apiClient: AxiosInstance = axios.create({
        baseURL: config.public.apiURL,
        headers: {
            "Content-type": "application/json",
            "Accept": "application/json"
        }
    });

    apiClient.interceptors.request.use((requestConfig) => {
        const session: Session = SessionStore().getSession;
        if (session.jwtToken) {
            requestConfig.headers.Authorization = "Bearer " + session.jwtToken;
        }
        return requestConfig;
    });

    apiClient.interceptors.response.use((response) => response, (error: AxiosError) => {
        if (error.response?.status === 401) {
            SessionStore().clearSession();
            navigateTo("/login");
        }
        return Promise.reject(error);
    });

    return {
        provide: {
            apiAuth,
            apiClient
        }
    };
});
