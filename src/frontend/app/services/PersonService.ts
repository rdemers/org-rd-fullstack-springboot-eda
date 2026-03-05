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
import type Person            from "@/types/Person";

class PersonService {
    private readonly endpoint = "/persons";

    public getAll(): Promise<AxiosResponse<Person[]>> {

        return apiClient.get<Person[]>(this.endpoint);
    };

    public get(id: number): Promise<AxiosResponse<Person>> {
        
        if (id < 0) 
            throw new Error("ID.");
       
        return apiClient.get<Person>(`${this.endpoint}/${id}`);
    };

    public findByFirstName(firstName: string): Promise<AxiosResponse<Person[]>> {
       
        const safeFirstName = encodeURIComponent(firstName);
        return apiClient.get<Person[]>(`${this.endpoint}?firstName=${safeFirstName}`);
    };
    
    public create(data: Person): Promise<AxiosResponse<Person>> {
       
        return apiClient.post<Person>(this.endpoint, data);
    };

    public update(person: Person): Promise<AxiosResponse<Person>> {

        if (person.personId === null || person.personId < 0) 
            throw new Error("ID.");
       
        return apiClient.put<Person>(`${this.endpoint}/${person.personId}`, person);
    };

    public delete(id: number): Promise<AxiosResponse<void>> {

        if (id < 0) 
            throw new Error("ID.");
        
        return apiClient.delete<void>(`${this.endpoint}/${id}`);
    };
};

export default new PersonService();