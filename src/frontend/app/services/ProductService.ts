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
import type Product           from "@/types/Product";

class ProductService {
    private readonly endpoint = "/products";

    public getAll(): Promise<AxiosResponse<Product[]>> {

        return apiClient.get<Product[]>(this.endpoint);
    };

    public get(id: number): Promise<AxiosResponse<Product>> {
        
        if (id < 0) 
            throw new Error("ID.");
        
        return apiClient.get<Product>(`${this.endpoint}/${id}`);
    };

    public findByCode(code: string): Promise<AxiosResponse<Product[]>> {
        
        const safeCode = encodeURIComponent(code);
        return apiClient.get<Product[]>(`${this.endpoint}?code=${safeCode}`);
    };
    
    public create(data: Product): Promise<AxiosResponse<Product>> {
        
        return apiClient.post<Product>(this.endpoint, data);
    };

    public update(product: Product): Promise<AxiosResponse<Product>> {

        if (product.productId === null || product.productId < 0) 
            throw new Error("ID.");
        
        return apiClient.put<Product>(`${this.endpoint}/${product.productId}`, product);
    };

    public delete(id: number): Promise<AxiosResponse<void>> {
                
        if (id < 0) 
            throw new Error("ID.");
        
        return apiClient.delete<void>(`${this.endpoint}/${id}`);
    };
};

export default new ProductService();