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
  -->
<template>
    <img class="vrd-logo" :alt="alt" :src="resolvedSrc" :style="style">
</template>

<script setup lang="ts">
    import { computed } from "vue";

    const props = withDefaults(
        defineProps<{
            src:    string;
            alt:    string;
            height?: string | number;
            width?:  string | number;
        }>(),
        { height: 120 }
    );

    // Public assets (public/*.png) are served under the app's baseURL (e.g. "/app/"
    // behind the SpringBoot static server), not under "/" — a plain "/foo.png" src
    // 404s in production even though it resolves fine against Nuxt's dev server.
    const { app } = useRuntimeConfig();
    const resolvedSrc = computed(() => app.baseURL + props.src.replace(/^\//, ""));

    const toCss = (value?: string | number) =>
        value === undefined ? undefined : (typeof value === "number" ? `${value}px` : value);

    const style = computed(() => ({
        height: toCss(props.height),
        width:  toCss(props.width)
    }));
</script>

<style scoped>
    .vrd-logo {
        object-fit: contain;
    }
</style>
