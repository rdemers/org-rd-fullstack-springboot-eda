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
  <v-app>
    <v-navigation-drawer v-model="drawer" :rail="miniVariant" app>
      <v-list nav density="compact" v-model:opened="openGroups">
        <template v-for="item in menuItems" :key="item.id">
          <template v-if="!item.children?.length">
            <v-tooltip v-if="miniVariant" location="right">
              <template #activator="{ props }">
                <v-list-item v-bind="props" :to="item.to" :prepend-icon="item.icon" link/>
              </template>
              {{ t(item.title) }}
            </v-tooltip>
            <v-list-item v-else :to="item.to" :prepend-icon="item.icon" :title="t(item.title)" link/>
          </template>
          <template v-else>
            <v-tooltip v-if="miniVariant" location="right">
              <template #activator="{ props }">
                <v-list-item v-bind="props" :prepend-icon="item.icon"/>
              </template>
              {{ t(item.title) }}
            </v-tooltip>
            <v-list-group v-else :value="item.id">
              <template #activator="{ props }">
                <v-list-item v-bind="props" :prepend-icon="item.icon" :title="t(item.title)"/>
              </template>
              <v-list-item v-for="child in item.children" :key="child.id" :to="child.to" :prepend-icon="child.icon" :title="t(child.title)" link/>
            </v-list-group>
          </template>
        </template>
      </v-list>
    </v-navigation-drawer>
    <v-app-bar shadow>
      <v-app-bar-nav-icon @click="toggleDrawer" />
      <v-toolbar-title>{{ title }}</v-toolbar-title>
      <v-spacer/>
      <v-switch v-model="isDark" inset color="primary" hide-details class="mx-5">
        <template #label>
          {{ isDark ? t('layout.theme-dark') : t('layout.theme-light') }}
        </template>
        <template #thumb>
          <v-icon>
            {{ isDark ? 'mdi-weather-night' : 'mdi-white-balance-sunny' }}
          </v-icon>
        </template>
      </v-switch>
      <v-menu>
        <template #activator="{ props }">
          <v-btn v-bind="props" variant="text">
            <v-icon start>mdi-translate-variant</v-icon>
            <span class="mx-2 text-capitalize">{{ currentLocaleName }}</span>
          </v-btn>
        </template>
        <v-list>
          <v-list-item v-for="loc in availableLocales" :key="loc.code" @click="changeLocale(loc)">
            <v-list-item-title>{{ loc.name }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-app-bar>
    <v-main>
      <v-container fluid>
        <NuxtPage :key="locale"/>
      </v-container>
    </v-main>
    <v-footer app class="border-t">
      <span>Copyright &copy; {{ currentYear }} - Réal Demers</span>
    </v-footer>
  </v-app>
</template>

<script setup lang="ts">
    import { ref, computed, watch, onMounted } from "vue";
    import { useTheme }                        from "vuetify";
    import type { LocaleObject }               from "@nuxtjs/i18n";
    import type MenuItem                       from "@/types/MenuItem";

    const { t, locale, locales, setLocale } = useI18n();
    const theme = useTheme();

    const drawer = ref(true);
    const miniVariant = ref(false);
    const isDark = ref(false);
    const openGroups = ref<string[]>([]);

    const title = "org.rd.fullstack.springboot-eda";
    const currentYear = new Date().getFullYear();

    const menuItems: MenuItem[] = [
    { id: "home", title: "layout.home", icon: "mdi-home", to: "/" },
    {
      id: "db", title: "layout.database", icon: "mdi-database",
      children: [
        { id: "persons", title: "layout.persons", icon: "mdi-account", to: "/persons" },
        { id: "products", title: "layout.products", icon: "mdi-package-variant", to: "/products" },
        { id: "inventories", title: "layout.inventories", icon: "mdi-warehouse", to: "/inventories" },
        { id: "requests", title: "layout.requests", icon: "mdi-cart", to: "/requests" }
      ]
    },
    {
      id: "infra", title: "layout.infrastructure", icon: "mdi-server-network",
      children: [
        { id: "kafka", title: "layout.kafka", icon: "mdi-vector-polyline", to: "/kafka" },
        { id: "flink", title: "layout.flink", icon: "mdi-pipe", to: "/flink" },
        { id: "operations", title: "layout.operations", icon: "mdi-cogs", to: "/operations" }
      ]
    },
    { id: "about", title: "layout.about", icon: "mdi-copyright", to: "/about" }
    ];

    const availableLocales = computed<LocaleObject[]>(() => locales.value as LocaleObject[]);
    const currentLocaleName = computed(() => availableLocales.value.find(l => l.code === locale.value)?.name);

    async function changeLocale(loc: LocaleObject) {
        await setLocale(loc.code);
        localStorage.setItem("user-locale", loc.code);
    }

    function toggleDrawer(): void {
        if (drawer.value) {
            miniVariant.value = !miniVariant.value;
        } else {
            drawer.value = true;
            miniVariant.value = false;
        }
    }

    watch(isDark, (newVal) => {
        theme.change(newVal ? "dark" : "light");
        localStorage.setItem("darkMode", String(newVal));
    });

    watch(drawer, (val) => localStorage.setItem("drawer", String(val)));
    watch(miniVariant, (val) => localStorage.setItem("miniVariant", String(val)));

    onMounted(() => {
        const savedTheme = localStorage.getItem("darkMode");
        if (savedTheme !== null) {
            isDark.value = savedTheme === "true";
        } else {
            isDark.value = window.matchMedia("(prefers-color-scheme: dark)").matches;
        }

        drawer.value = localStorage.getItem("drawer") !== "false";
        miniVariant.value = localStorage.getItem("miniVariant") === "true";

        const savedLocale = localStorage.getItem("user-locale");
        const isAvailable = availableLocales.value.some(l => l.code === savedLocale);

        if (savedLocale && isAvailable && savedLocale !== locale.value) {
            setLocale(savedLocale as "fr" | "en"); 
        }
    });
</script>