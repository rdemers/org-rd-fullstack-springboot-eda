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
import { SessionStore } from "@/store/SessionStore";

/*
* Rehydrates the session (JWT token) from localStorage when the application starts.
* This plugin runs before the authentication middleware,
* allowing the session to survive a page reload (F5).
*/
export default defineNuxtPlugin(() => {
    SessionStore().loadSession();
});
