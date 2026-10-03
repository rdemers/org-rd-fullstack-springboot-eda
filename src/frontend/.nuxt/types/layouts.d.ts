import type { ComputedRef, MaybeRef } from "vue";
import type { ComponentProps } from "../../node_modules/vue-component-type-helpers/index.js";

declare module 'nuxt/app' {
  interface NuxtLayouts {
    default: ComponentProps<typeof import("/Users/rdemers/devhub/scm/org-rd-fullstack-springboot-eda/src/frontend/app/layouts/default.vue").default>
  }
  export type LayoutKey = keyof NuxtLayouts extends never ? string : keyof NuxtLayouts
  interface PageMeta {
    layout?: MaybeRef<LayoutKey | false> | ComputedRef<LayoutKey | false> | {
      [K in LayoutKey]: {
        name?: MaybeRef<K | false> | ComputedRef<K | false>
        props?: NuxtLayouts[K]
      }
    }[LayoutKey]
  }
}