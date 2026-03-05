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
      <client-only>
        <ul class="vrd_navigation">

            <!-- Navigate through the pages. -->
            <li class="vrd_navigation_item_left">
                <button class="vrd_item" id="prev_page" @click=prevPage()>
                    <font-awesome-icon class="fa-lg" icon="fa-solid fa-arrow-left"/>
                </button>
                <input class="vrd_item vrd_item_width" type="number" v-model.number="page" :min="1" :max="numPages"/>
                <button class="vrd_item" id="next_page" @click=nextPage()>
                    <font-awesome-icon class="fa-lg" icon="fa-solid fa-arrow-right"/>
                </button>
            </li>

            <!-- Information on the current page. -->
            <li class="vrd_navigation_item_left">
                <span class="vrd_item" id="page_num">{{ summary }}</span>
            </li>

            <!-- Zoom In / Out. -->
            <li class="vrd_navigation_item_right">
                <button class="vrd_item" id="printing" @click=print()>
                    <font-awesome-icon class="fa-lg" icon="fa-solid fa-print"/>
                </button>
                <button class="vrd_item" id="zoom_in" @click=zoomIn()>
                    <font-awesome-icon class="fa-lg" icon="fa-solid fa-magnifying-glass-plus"/>
                </button>
                <button class="vrd_item" id="zoom_out" @click=zoomOut()>
                    <font-awesome-icon class="fa-lg" icon="fa-solid fa-magnifying-glass-minus"/>
                </button>
            </li>
        </ul>

        <!-- Container for the PDF. -->
        <div class="vrd_viewport">
            <canvas id="canvas" ref="canvasRef" class="vrd_canvas"/>
        </div>
    </client-only>
</template>

<script setup lang="ts">
    import { ref, computed, onMounted, watch }  from "vue";
    import { PDFService }                       from "@/services/PDFService";
    import { PDFServiceException }              from "@/services/PDFServiceException";

    const props = defineProps<{ url: string }>();
    const { t } = useI18n();

    const canvasRef = ref<HTMLCanvasElement | null>(null);
    const pdf = ref<PDFService | null>(null);

    const page = ref(1);
    const numPages = ref(0);

    const isLoading = ref(true);
    const error = ref<string | null>(null);

    const summary = computed(() =>
        `${t("vrd-pdfviewer.page")} ${page.value} ${t("vrd-pdfviewer.of")} ${numPages.value}`
    );

    onMounted(async () => {
        try {
            isLoading.value = true;
            error.value = null;
            pdf.value = new PDFService(props.url);
            
            if (!canvasRef.value) {
                console.error("Canvas not found");
                return;
            }

            await pdf.value.init(canvasRef.value);
            await pdf.value.setPage(1);
            numPages.value = pdf.value.getNumPages();
            page.value = pdf.value.getPage();  
        } catch (e) {
            if (e instanceof PDFServiceException) {
                error.value = e.toString();
            }
        } finally {
            isLoading.value = false;
        }
    });

    onUnmounted(() => {
        pdf.value?.destroy();
        pdf.value = null;
    });

    watch(page, async (newPage) => {
        if (!pdf.value) 
            return;
        try {
            await pdf.value.setPage(newPage);
            page.value = pdf.value.getPage();
        } catch (e) {
            console.error("Error changing page:", e);
            page.value = pdf.value.getPage();
        }
    });

    const nextPage = async () => {
        if (!pdf.value) 
            return;
        try {
            await pdf.value.nextPage();
            page.value = pdf.value.getPage();
        } catch (e) {
            console.error("Error navigating:", e);
        }
    };

    const prevPage = async () => {
        if (!pdf.value) 
            return;

        try {
            await pdf.value.prevPage();
            page.value = pdf.value.getPage();
        } catch (e) {
            console.error("Error navigating:", e);
        }
    };
    
    const zoomIn = async () => {
        if (!pdf.value) 
            return;

        try {
            await pdf.value.zoomIn();
        } catch (e) {
            console.error("Error navigating:", e);
        }
    };
    
    const zoomOut = async () => {
        if (!pdf.value) 
            return;

        try {
            await pdf.value.zoomOut();
        } catch (e) {
            console.error("Error navigating:", e);
        }
    };

    const print = async () => {
        if (!pdf.value) 
            return;

        try {
            await pdf.value.print();
        } catch (e) {
            console.error("Error navigating:", e);
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
</style>