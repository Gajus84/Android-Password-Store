/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
  id("com.github.android-password-store.android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

android {
  buildFeatures {
    compose = true
    composeOptions {
      useLiveLiterals = false
      kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
  }
  namespace = "app.passwordstore.ui.compose"
}

dependencies {
  api(platform(libs.compose.bom))
  api(libs.compose.foundation.core)
  api(libs.compose.foundation.layout)
  api(libs.compose.material3)
  api(libs.compose.ui.core)
}
