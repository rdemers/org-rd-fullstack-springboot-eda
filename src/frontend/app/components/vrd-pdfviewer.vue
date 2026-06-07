<!--
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
  -->
<template>
    <!-- Loading error. -->
    <div v-if="error" class="vrd_error">
        <span>{{ error }}</span>
    </div>

    <!-- Loading indicator. -->
    <div v-if="isLoading && !error" class="vrd_loading">
        <span>{{ t("vrd-pdfviewer.loading") }}</span>
    </div>

    <!-- Navigation bar (visible only when the PDF is ready). -->
    <ul v-if="!isLoading && !error" class="vrd_navigation">
        <!--Navigate through the pages. -->
        <li class="vrd_navigation_item_left">
            <button class="vrd_item" :aria-label="t('vrd-pdfviewer.prevPage')" @click="prevPage()">
                <font-awesome-icon class="fa-lg" icon="fa-solid fa-arrow-left"/>
            </button>
            <input class="vrd_item vrd_item_input" type="number"
                   v-model.number="page" :min="1" :max="numPages"
                   :aria-label="t('vrd-pdfviewer.pageInput')"/>
            <button class="vrd_item" :aria-label="t('vrd-pdfviewer.nextPage')" @click="nextPage()">
                <font-awesome-icon class="fa-lg" icon="fa-solid fa-arrow-right"/>
            </button>
        </li>

        <!-- Information about the current page. -->
        <li class="vrd_navigation_item_left">
            <span class="vrd_item" aria-live="polite">{{ summary }}</span>
        </li>

        <!-- Zoom / Print. -->
        <li class="vrd_navigation_item_right">
            <button class="vrd_item" :aria-label="t('vrd-pdfviewer.print')" @click="print()">
                <font-awesome-icon class="fa-lg" icon="fa-solid fa-print"/>
            </button>
            <button class="vrd_item" :aria-label="t('vrd-pdfviewer.zoomIn')" @click="zoomIn()">
                <font-awesome-icon class="fa-lg" icon="fa-solid fa-magnifying-glass-plus"/>
            </button>
            <button class="vrd_item" :aria-label="t('vrd-pdfviewer.zoomOut')" @click="zoomOut()">
                <font-awesome-icon class="fa-lg" icon="fa-solid fa-magnifying-glass-minus"/>
            </button>
        </li>
    </ul>
    <div class="vrd_viewport">
        <canvas ref="canvasRef" class="vrd_canvas"/>
    </div>
</template>

<script setup lang="ts">
    import { ref, computed, onMounted, 
             onUnmounted, watch }  from "vue";

    import { PDFService }          from "@/services/PDFService";
    import { PDFServiceCode }      from "@/services/PDFServiceCode";
    import { PDFServiceException } from "@/services/PDFServiceException";

    const props = withDefaults(
        defineProps<{
            url: string;
            useAuthorization?: boolean;
            title?: string;
            subtitle?: string;
        }>(),
        { useAuthorization: true }
    );

    const { t }     = useI18n();
    const canvasRef = ref<HTMLCanvasElement | null>(null);
    const page      = ref(1);
    const numPages  = ref(0);
    const isLoading = ref(true);
    const error     = ref<string | null>(null);

    const pdfService:PDFService = new PDFService({
        pdfSource: props.url,
        useAuthorization: props.useAuthorization,
        title: props.title,
        subtitle: props.subtitle
    });

    // Guard to avoid the infinite loop of the watcher.
    let suppressWatch = false;

    const summary = computed(() =>
        `${t("vrd-pdfviewer.page")} ${page.value} ${t("vrd-pdfviewer.of")} ${numPages.value}`
    );

    onMounted((): void => {
        error.value = null;
        isLoading.value = true;

        if (!canvasRef.value) {
            error.value = t("vrd-pdfviewer.no-canvas");
            isLoading.value = false;
            return;
       }

        pdfService.init(canvasRef.value as HTMLCanvasElement).then(() => {
            page.value = pdfService.getPage();
            numPages.value = pdfService.getNumPages();
        }).catch((ex: Error) => {
            if (ex instanceof PDFServiceException) {
                switch((ex as PDFServiceException).getCode()) {
                    case PDFServiceCode.NO_SOURCE: {
                        error.value = t('vrd-pdfviewer.no-source');
                        break;
                    }
                    case PDFServiceCode.NO_NODE_SUPPORTED: {
                        error.value = t('vrd-pdfviewer.no-node-supported');
                        break;
                    }
                    default: {
                        error.value = t('vrd-pdfviewer.unknown');
                        break;
                    }
                }
            }
            console.log(ex);
        }).finally(() => {
            isLoading.value = false;
        });
    });   

    onUnmounted(() => {
        pdfService.destroy();
    });

    watch(page, async (newPage) => {
        if (suppressWatch)
            return;

        try {
            await pdfService.setPage(newPage);
        } catch (ex) {
            console.error("Error changing page:", ex);
        } finally {
            suppressWatch = true;
            page.value = pdfService.getPage();
            nextTick(() => { suppressWatch = false; });
        }
    });

    const nextPage = async () => {
        try {
            await pdfService.nextPage();
            suppressWatch = true;
            page.value = pdfService.getPage();
            nextTick(() => { suppressWatch = false; });
        } catch (ex) {
            console.error("Error navigating:", ex);
        }
    };

    const prevPage = async () => {
        try {
            await pdfService.prevPage();
            suppressWatch = true;
            page.value = pdfService.getPage();
            nextTick(() => { suppressWatch = false; });
        } catch (ex) {
            console.error("Error navigating:", ex);
        }
    };

    const zoomIn = async () => {
        try {
            await pdfService.zoomIn();
        } catch (ex) {
            console.error("Error zooming in:", ex);
        }
    };

    const zoomOut = async () => {
        try {
            await pdfService.zoomOut();
        } catch (ex) {
            console.error("Error zooming out:", ex);
        }
    };

    const print = async () => {
        try {
            await pdfService.print();
        } catch (ex) {
            console.error("Error printing:", ex);
        }
    };
</script>

<style scoped>
    .vrd_navigation {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        justify-content: space-between;
        background: #333;
        color: white;
    }

    .vrd_navigation_item_left,
    .vrd_navigation_item_right {
        display: flex;
        align-items: center;
        gap: 8px;
    }

    .vrd_item {
        background: transparent;
        border: none;
        color: white;
        padding: 12px 16px;
        cursor: pointer;
        font-size: 1.1rem;
    }

    .vrd_item:hover,
    .vrd_item:focus {
        background: #111;
    }

    .vrd_item_input {
        width: 4rem;
        text-align: center;
        border-radius: 4px;
        appearance: textfield;
    }

    .vrd_item_input::-webkit-outer-spin-button,
    .vrd_item_input::-webkit-inner-spin-button {
        -webkit-appearance: none;
        margin: 0;
    }

    .vrd_viewport {
        background: #e2e2e2;
        overflow: auto;
        height: 70vh;
        min-height: 400px;
        display: flex;
        justify-content: center;
        align-items: flex-start;
        padding: 20px 0;
    }

    .vrd_error {
        display: flex;
        justify-content: center;
        align-items: center;
        height: 50px;
        color: #c0392b;
        font-size: 1.1rem;
    }

    .vrd_loading {
        display: flex;
        justify-content: center;
        align-items: center;
        height: 50px;
        color: #fdf9f9;
        font-size: 1.1rem;
    }
</style>