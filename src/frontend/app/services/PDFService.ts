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
 *
 */
import {
    type PDFDocumentLoadingTask,
    type PDFDocumentProxy,
    type PDFPageProxy,
    type PageViewport
}                               from "pdfjs-dist";

import * as pdfjs               from "pdfjs-dist";

import { PDFServiceCode }       from "@/services/PDFServiceCode";
import { PDFServiceException }  from "@/services/PDFServiceException";
import type Session             from "@/types/Session";
import { SessionStore }         from "@/store/SessionStore";

import printJS                  from "print-js";
import { fromByteArray }        from "base64-js";
import { GlobalWorkerOptions }  from "pdfjs-dist";
import pdfWorker                from "pdfjs-dist/build/pdf.worker?url";

export interface PDFServiceOptions {
    pdfSource: string;
    title?: string;
    subtitle?: string;
    useAuthorization?: boolean;
}

export class PDFService {
    private readonly MIN_SCALE   = 0.4;
    private readonly MAX_SCALE   = 4.0;
    private readonly ZOOM_FACTOR = 1.25;

    private pdfSource: string;
    private title?: string;
    private subtitle?: string;
    private useAuthorization: boolean;

    private documentProxy?: PDFDocumentProxy;
    private canvas?: HTMLCanvasElement;

    private numPages = 0;
    private currentPage = 1;
    private scale = 1.0;

    private rendering = false;
    private pendingRender = false;
    private static workerInitialized = false;

    constructor(options: PDFServiceOptions) {
        if (!PDFService.workerInitialized) {
            GlobalWorkerOptions.workerSrc = pdfWorker;
            PDFService.workerInitialized = true;
        }
        this.pdfSource        = options.pdfSource;
        this.title            = options.title;
        this.subtitle         = options.subtitle;
        this.useAuthorization = options.useAuthorization ?? true;
    }

    public async init(canvas: HTMLCanvasElement): Promise<void> {
        if (!this.pdfSource) {
            throw new PDFServiceException(PDFServiceCode.NO_SOURCE);
        }

        if (pdfjs === undefined) {
            throw new PDFServiceException(PDFServiceCode.NO_NODE_SUPPORTED);
        }

        if (this.documentProxy) {
            this.destroy();
        }
        
        this.canvas = canvas;

        const url = new URL(this.pdfSource);
        if (this.title) {
            url.searchParams.set("title", this.title);
        }
        if (this.subtitle) {
            url.searchParams.set("sub-title", this.subtitle);
        }

        const httpHeaders: Record<string, string> = {
            Accept: "application/pdf"
        };

        if (this.useAuthorization) {
            const session: Session = SessionStore().getSession;
            if (session.jwtToken) {
                httpHeaders.Authorization = "Bearer " + session.jwtToken;
            }
        }

        const loadingTask: PDFDocumentLoadingTask = pdfjs.getDocument({
            url: url.toString(),
            httpHeaders
        });

        this.documentProxy = await loadingTask.promise;
        this.numPages = this.documentProxy.numPages;
        await this.safeRender();
    }

    public destroy(): void {
        this.documentProxy?.cleanup();
        this.documentProxy = undefined;

        if (this.canvas) {
            const context = this.canvas.getContext("2d");
            if (context) {
                context.clearRect(0, 0, this.canvas.width, this.canvas.height);
            }
            this.canvas.width = 0;
            this.canvas.height = 0;
            this.canvas = undefined;
        }

        this.rendering = false;
        this.pendingRender = false;
    }

    private async render(): Promise<void> {
        if (!this.documentProxy || !this.canvas) {
            throw new PDFServiceException(PDFServiceCode.NO_NODE_SUPPORTED);
        }

        const page: PDFPageProxy = await this.documentProxy.getPage(this.currentPage);
        const viewport: PageViewport = page.getViewport({ scale: this.scale });
        const context = this.canvas.getContext("2d");
        if (!context)
            return;

        this.canvas.width = viewport.width;
        this.canvas.height = viewport.height;

        const renderTask = page.render({
            canvasContext: context,
            viewport,
            canvas: this.canvas
        });

        await renderTask.promise;
    }

    private async safeRender(): Promise<void> {
        if (this.rendering) {
            this.pendingRender = true;
            return;
        }

        this.rendering = true;
        try {
            await this.render();
        } finally {
            this.rendering = false;
            if (this.pendingRender) {
                this.pendingRender = false;
                await this.safeRender();
            }
        }
    }

    public async nextPage(): Promise<void> {
        if (this.currentPage >= this.numPages)
            return;

        this.currentPage++;
        await this.safeRender();
    }

    public async prevPage(): Promise<void> {
        if (this.currentPage <= 1)
            return;

        this.currentPage--;
        await this.safeRender();
    }

    public async setPage(page: number): Promise<void> {
        if (page < 1 || page > this.numPages || page === this.currentPage)
            return;

        this.currentPage = page;
        await this.safeRender();
    }

    public async zoomIn(): Promise<void> {
        this.scale = Math.min(this.scale * this.ZOOM_FACTOR, this.MAX_SCALE);
        await this.safeRender();
    }

    public async zoomOut(): Promise<void> {
        this.scale = Math.max(this.scale / this.ZOOM_FACTOR, this.MIN_SCALE);
        await this.safeRender();
    }

    public async print(): Promise<void> {
        if (!this.documentProxy)
            return;

        const data = await this.documentProxy.getData();
        const base64 = fromByteArray(data);

        if (typeof window !== "undefined") {
            printJS({
                printable: base64,
                type: "pdf",
                base64: true
            });
        }
    }

    public getPage(): number {
        return this.currentPage;
    }

    public getNumPages(): number {
        return this.numPages;
    }
}