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
export const PipelineState = {
    READY:     { value: 10, translationKey: "pipeline.available" },
    EXECUTING: { value: 20, translationKey: "pipeline.executing" },
    COMPLETED: { value: 30, translationKey: "pipeline.completed" },
    EXCEPTION: { value: 99, translationKey: "pipeline.exception" },
    UNKNOWN:   { value: 0,  translationKey: "pipeline.unknown" }
} as const;

export type PipelineStateKey   = keyof typeof PipelineState;
export type PipelineStateValue = typeof PipelineState[PipelineStateKey];

export function getPipelineState(): PipelineStateValue[] {
    return Object.values(PipelineState);
}

export function findPipelineState(value: number): PipelineStateValue {
    return Object.values(PipelineState).find(r => r.value === value) ?? PipelineState.UNKNOWN;
}

export const getPipelineStateColor = (value: number): string => {
    if (value === 10) return "info";
    if (value === 20) return "yellow";
    if (value === 30) return "success";
    if (value === 99) return "error";
    return "grey";
}