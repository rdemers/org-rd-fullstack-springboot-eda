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
import apiClient              from "./HTTPService";
import type Request           from "@/types/Request";
import type RequestView       from "@/types/RequestView";

class RequestService {

    private readonly endpoint = "/requests";

    public getAll(): Promise<AxiosResponse<Request[]>> {

        return apiClient.get<Request[]>(this.endpoint);
    };

    public get(id: number): Promise<AxiosResponse<Request>> {
        
        if (id < 0) 
            throw new Error("ID.");
        
        return apiClient.get<Request>(`${this.endpoint}/${id}`);
    };

    public create(data: Request): Promise<AxiosResponse<Request>> {
        return apiClient.post<Request>(this.endpoint, data);
    };

    public update(request: Request): Promise<AxiosResponse<Request>> {

        if (request.requestId === null || request.requestId < 0) 
            throw new Error("ID.");
        
        return apiClient.put<Request>(`${this.endpoint}/${request.requestId}`, request);
    };

    public delete(id: number): Promise<AxiosResponse<void>> {
        
        if (id < 0) 
            throw new Error("ID.");

        return apiClient.delete<void>(`${this.endpoint}/${id}`);
    };

    public getView(id: number) {

        if (id < 0) 
            throw new Error("ID.");

        return apiClient.get<RequestView>(`${this.endpoint}/view/${id}`);
    };

    public getAllView(): Promise<AxiosResponse<RequestView[]>> {
        return apiClient.get<RequestView[]>(`${this.endpoint}/view/native`);
    };
};

export default new RequestService();