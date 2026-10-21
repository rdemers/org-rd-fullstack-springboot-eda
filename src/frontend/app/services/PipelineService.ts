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
import type { AxiosResponse } from "axios";
import { apiClient }          from "./HTTPService";
import type PipelineContext   from "@/types/PipelineContext";
import type StatsContext      from "@/types/StatsContext";

class PipelineService {

    private readonly endpoint = "/pipeline";

    // Parameters (read once on mount).
    public getPipelineContext(): Promise<AxiosResponse<PipelineContext>> {
        return apiClient().get<PipelineContext>(`${this.endpoint}/context`);
    }

    // Stats & state (polled).
    public getStats(): Promise<AxiosResponse<StatsContext>> {
        return apiClient().get<StatsContext>(`${this.endpoint}/stats`);
    }

    public startPipeline(pipelineContext: PipelineContext): Promise<AxiosResponse<void>> {
        return apiClient().post<void>(`${this.endpoint}/start`, pipelineContext);
    }

    // Immediately pause/resume the Kafka processor listener (and persist the flag).
    public setPause(value: boolean): Promise<AxiosResponse<PipelineContext>> {
        return apiClient().put<PipelineContext>(`${this.endpoint}/pause/${value}`);
    }

    public resetPipeline(): Promise<AxiosResponse<StatsContext>> {
        return apiClient().post<StatsContext>(`${this.endpoint}/reset`);
    }
}
export default new PipelineService();