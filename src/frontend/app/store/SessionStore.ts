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
import { defineStore } from "pinia";
import type Session    from "@/types/Session";

const STORAGE_KEY = "SessionStore";

function emptySession(): Session {
    return {
        jwtToken: "",
        user: "",
        roles: []
    };
}

function persist(session: Session): void {
    if (import.meta.client) {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    }
}

export const SessionStore = defineStore(STORAGE_KEY, {

    state: () => ({
        currentSession: emptySession(),
    }),

    getters: {
        getSession():Session {
            return this.currentSession;
        },
    },

    actions: {
        setSession(session: Session) {
            this.currentSession = session;
            persist(session);
        },

        clearSession() {
            this.currentSession = emptySession();
            if (import.meta.client) {
                window.localStorage.removeItem(STORAGE_KEY);
            }
        },

        /**
        * Rehydrates the session from localStorage. 
        * Call this at application startup (client plugin) so that 
        * the session survives a page reload (F5).
        */
        loadSession() {
            if (!import.meta.client) {
                return;
            }
            const raw = window.localStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return;
            }
            try {
                const parsed = JSON.parse(raw) as Partial<Session>;
                this.currentSession = {
                    jwtToken: parsed.jwtToken ?? "",
                    user: parsed.user ?? "",
                    roles: parsed.roles ?? []
                };
            } catch {
                window.localStorage.removeItem(STORAGE_KEY);
            }
        },

        isActiveSession(): boolean {
            if (this.currentSession.jwtToken.trim().length !== 0) {
                return true;
            }
            return false;
        }
    }
})