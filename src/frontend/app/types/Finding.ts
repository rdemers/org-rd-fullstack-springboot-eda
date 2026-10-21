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
export const FindingStep = {
    DLT:            { translationKey: "finding.step.dlt" },
    JOURNAL:        { translationKey: "finding.step.journal" },
    BATCH_FILE:     { translationKey: "finding.step.batch-file" },
    CLIENT_BALANCE: { translationKey: "finding.step.client-balance" },
    INVENTORY_QTY:  { translationKey: "finding.step.inventory-qty" },
} as const;

export type FindingStepKey = keyof typeof FindingStep;

export const FindingSeverity = {
    INFO:  { rank: 1, color: "info",        translationKey: "finding.severity.info" },
    WARN:  { rank: 2, color: "deep-orange", translationKey: "finding.severity.warn" },
    ERROR: { rank: 3, color: "error",       translationKey: "finding.severity.error" },
} as const;

export type FindingSeverityKey = keyof typeof FindingSeverity;

// Mirrors ConciliationSrv.Finding.
export default interface Finding {
    step:     FindingStepKey;
    severity: FindingSeverityKey;
    message:  string;
}

export function findStep(key: string) {
    return (FindingStep as Record<string, { translationKey: string }>)[key];
}

export function findSeverity(key: string) {
    return (FindingSeverity as Record<string, { rank: number; color: string; translationKey: string }>)[key];
}

export const getSeverityColor = (key: string): string => findSeverity(key)?.color ?? "grey";
