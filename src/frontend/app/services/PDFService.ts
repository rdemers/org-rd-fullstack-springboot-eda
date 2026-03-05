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

import printJS                  from "print-js";
import { fromByteArray }        from "base64-js";
import { PDFServiceCode }       from "@/services/PDFServiceCode";
import { PDFServiceException }  from "@/services/PDFServiceException";
import { GlobalWorkerOptions }  from "pdfjs-dist";
import pdfWorker                from "pdfjs-dist/build/pdf.worker?url";
// import "pdfjs-dist/webpack"; // Allow proper initialization -- Old way.

export class PDFService {

    private readonly MIN_SCALE = 0.4;
    private readonly MAX_SCALE = 4.0;

    private pdfSource: string;
    private documentProxy?: PDFDocumentProxy;
    private canvas?: HTMLCanvasElement;

    private numPages = 0;
    private currentPage = 1;
    private scale = 1.0;

    private rendering = false;
    private static workerInitialized = false;

    constructor(pdfSource: string) {
        if (!PDFService.workerInitialized) {
            GlobalWorkerOptions.workerSrc = pdfWorker;
            PDFService.workerInitialized = true;
        }
        this.pdfSource = pdfSource;
    };

    public async init(canvas: HTMLCanvasElement): Promise<void> {
        if (!this.pdfSource) {
            throw new PDFServiceException(PDFServiceCode.NO_SOURCE);
        };

        this.canvas = canvas;

        const loadingTask: PDFDocumentLoadingTask = pdfjs.getDocument({
            url: this.pdfSource,
            httpHeaders: { Accept: "application/pdf" }
        });

        this.documentProxy = await loadingTask.promise;
        this.numPages = this.documentProxy.numPages;
    }

    public destroy(): void {
        this.documentProxy?.destroy();
        this.documentProxy = undefined;
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
    };

    private async safeRender(): Promise<void> {
        if (this.rendering) 
            return;
        
        this.rendering = true;

        try {
            await this.render();
        } finally {
            this.rendering = false;
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
        this.scale = Math.min(this.scale * 4 / 3, this.MAX_SCALE);
        await this.safeRender();
    }

    public async zoomOut(): Promise<void> {
        this.scale = Math.max(this.scale * 2 / 3, this.MIN_SCALE);
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
};