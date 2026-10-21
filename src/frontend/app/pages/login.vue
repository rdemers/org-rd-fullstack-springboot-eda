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
  <v-dialog v-model="dialog" max-width="440" persistent>
    <v-card rounded="lg">
      <v-card-title class="d-flex align-center justify-space-between pt-5 px-5">
        <div class="d-flex align-center gap-2">
          <v-icon icon="mdi-account-circle" color="primary"/>
          {{ $t('login.title') }}
        </div>
      </v-card-title>
      <v-card-subtitle class="px-5 pb-2">
        {{ $t('login.information') }}
      </v-card-subtitle>
      <v-alert
        v-if="isErrorLogin" type="error" variant="tonal"
                            class="mx-4 mb-2" density="compact" :text="$t('login.alert.text')"/>
      <v-card-text class="px-5">
        <v-form ref="formRef" @submit.prevent="doLogin">
          <v-text-field v-model="loginRequest.username" prepend-inner-icon="mdi-account"
                        :label="$t('login.login')" type="text" variant="outlined" density="comfortable"
                        :rules="[v => !!v || $t('login.required')]" class="mb-3"/>
          <v-text-field v-model="loginRequest.password" prepend-inner-icon="mdi-lock"
                        :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                        :label="$t('login.password')"
                        :type="showPassword ? 'text' : 'password'" variant="outlined" density="comfortable"
                        :rules="[v => !!v || $t('login.required')]"
                        @click:append-inner="showPassword = !showPassword"/>
        </v-form>
      </v-card-text>
      <v-card-actions class="px-5 pb-5">
        <v-btn type="submit" color="primary" variant="flat" block
          :loading="isActionDisabled" @click="doLogin">
          {{ $t('login.login') }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
    import { ref }                 from 'vue';
    import type { VForm }          from "vuetify/components";
    import type LoginRequest       from "@/types/LoginRequest";
    import type ResponseData       from "@/types/ResponseData";
    import type Session            from "@/types/Session";
    import { SessionStore }        from "@/store/SessionStore";
    import authentificationService from "@/services/AuthentificationService";

    const dialog           = ref<boolean>(true);
    const formRef          = ref<VForm | null>(null);
    const isActionDisabled = ref<boolean>(false);
    const isErrorLogin     = ref<boolean>(false);
    const showPassword     = ref<boolean>(false);
    const loginRequest     = ref<LoginRequest>({ username: "", password: "" });

    async function doLogin(): Promise<void> {

        const result = await formRef.value?.validate();
        if (!result?.valid) 
            return;

        isActionDisabled.value = true;
        isErrorLogin.value = false;

        try {
            const response: ResponseData = await authentificationService.doLogin(loginRequest.value);
            const session: Session = SessionStore().getSession;
            session.jwtToken = response.data.message;
            SessionStore().setSession(session);
            navigateTo("/");
        } catch (error: any) {
            isErrorLogin.value = true;
            console.error('[Login]', error?.response?.data ?? error);
        } finally {
            isActionDisabled.value = false;
        }
    }
</script>

<style scoped>
</style>