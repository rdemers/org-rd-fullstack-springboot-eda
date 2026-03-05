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
import type Inventory         from "@/types/Inventory";
import type InventoryView     from "@/types/InventoryView";

class InventoryService {

    private readonly endpoint = "/inventories";

    public getAll(): Promise<AxiosResponse<Inventory[]>> {
        return apiClient.get<Inventory[]>(this.endpoint);
    };

    public get(id: number): Promise<AxiosResponse<Inventory>> {

        if (id < 0) 
            throw new Error("ID.");

        return apiClient.get<Inventory>(`${this.endpoint}/${id}`);
    };

    public findByCode(productCode: string): Promise<AxiosResponse<Inventory[]>> {

        const safeProductCode = encodeURIComponent(productCode);
        return apiClient.get<Inventory[]>(`${this.endpoint}?code=${safeProductCode}`);
    };

    public create(data: Inventory): Promise<AxiosResponse<Inventory>> {

        return apiClient.post<Inventory>(this.endpoint, data);
    };

    public update(inventory: Inventory): Promise<AxiosResponse<Inventory>> {

        if (inventory.inventoryId === null || inventory.inventoryId < 0) 
            throw new Error("ID.");

        return apiClient.put<Inventory>(`${this.endpoint}/${inventory.inventoryId}`, inventory);
    };

    public delete(id: number): Promise<AxiosResponse<void>> {

        if (id < 0) 
            throw new Error("ID.");
        
        return apiClient.delete<void>(`${this.endpoint}/${id}`);
    };

    public getView(id: number) {

        return apiClient.get<InventoryView>(`${this.endpoint}/view/${id}`);
    };

    public getAllView(): Promise<AxiosResponse<InventoryView[]>> {

        return apiClient.get<InventoryView[]>(`${this.endpoint}/view`);
    };
};

export default new InventoryService();