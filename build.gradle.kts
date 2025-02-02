/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
  id("com.github.android-password-store.git-hooks")
  id("com.github.android-password-store.kotlin-common")
  id("com.github.android-password-store.ktfmt")
  id("com.github.android-password-store.versions")
  alias(libs.plugins.hilt) apply false
}
