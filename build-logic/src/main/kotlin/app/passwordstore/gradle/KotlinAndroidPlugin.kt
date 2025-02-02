/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import app.passwordstore.gradle.KotlinCommonPlugin.Companion.JVM_TOOLCHAIN_ACTION
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("Unused")
class KotlinAndroidPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.run {
      apply(KotlinAndroidPluginWrapper::class)
      apply(KotlinCommonPlugin::class)
    }
    project.extensions.getByType<KotlinProjectExtension>().jvmToolchain(JVM_TOOLCHAIN_ACTION)
    val catalog = project.extensions.getByType<VersionCatalogsExtension>()
    val libs = catalog.named("libs")
    val composeCompilerVersion = libs.getVersion("composeCompiler")
    val kotlinVersion = libs.getVersion("kotlin")
    val matches = COMPOSE_COMPILER_VERSION_REGEX.find(composeCompilerVersion)

    if (matches != null) {
      val (compilerKotlinVersion) = matches.destructured
      if (compilerKotlinVersion != kotlinVersion) {
        project.tasks.withType<KotlinCompile>().configureEach {
          compilerOptions.freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=$kotlinVersion",
          )
        }
      }
    }
  }

  private fun VersionCatalog.getVersion(key: String) =
    findVersion(key).map(VersionConstraint::toString).get()

  private companion object {
    // Matches against 1.5.0-dev-k1.9.0-6a60475e07f
    val COMPOSE_COMPILER_VERSION_REGEX = "\\d.\\d.\\d-dev-k(\\d.\\d.\\d)-[a-z0-9]+".toRegex()
  }
}
