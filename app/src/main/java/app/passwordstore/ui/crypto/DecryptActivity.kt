/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.password.FieldItem
import app.passwordstore.databinding.DecryptLayoutBinding
import app.passwordstore.ui.adapters.FieldItemAdapter
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2

@AndroidEntryPoint
class DecryptActivity : BasePgpActivity(), OpenPgpServiceConnection.OnBound {

  private val binding by viewBinding(DecryptLayoutBinding::inflate)
  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory

  private val relativeParentPath by unsafeLazy { getParentPath(fullPath, repoPath) }
  private var passwordEntry: PasswordEntry? = null

  private val userInteractionRequiredResult =
    registerForActivityResult(StartIntentSenderForResult()) { result ->
      if (result.data == null) {
        setResult(RESULT_CANCELED, null)
        finish()
        return@registerForActivityResult
      }

      when (result.resultCode) {
        RESULT_OK -> decryptAndVerify(result.data)
        RESULT_CANCELED -> {
          setResult(RESULT_CANCELED, result.data)
          finish()
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    bindToOpenKeychain(this)
    title = name
    with(binding) {
      setContentView(root)
      passwordCategory.text = relativeParentPath
      passwordFile.text = name
      passwordFile.setOnLongClickListener {
        copyTextToClipboard(name)
        true
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.pgp_handler, menu)
    passwordEntry?.let { entry ->
      menu.findItem(R.id.edit_password).isVisible = true
      if (!entry.password.isNullOrBlank()) {
        menu.findItem(R.id.share_password_as_plaintext).isVisible = true
        menu.findItem(R.id.copy_password).isVisible = true
      }
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> onBackPressed()
      R.id.edit_password -> editPassword()
      R.id.share_password_as_plaintext -> shareAsPlaintext()
      R.id.copy_password -> copyPasswordToClipboard(passwordEntry?.password)
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  override fun onBound(service: IOpenPgpService2) {
    super.onBound(service)
    decryptAndVerify()
  }

  override fun onError(e: Exception) {
    logcat(ERROR) { e.asLog() }
  }

  /**
   * Automatically finishes the activity 60 seconds after decryption succeeded to prevent
   * information leaks from stale activities.
   */
  @OptIn(ExperimentalTime::class)
  private fun startAutoDismissTimer() {
    lifecycleScope.launch {
      delay(Duration.seconds(60))
      finish()
    }
  }

  /**
   * Edit the current password and hide all the fields populated by encrypted data so that when the
   * result triggers they can be repopulated with new data.
   */
  private fun editPassword() {
    val intent = Intent(this, PasswordCreationActivity::class.java)
    intent.putExtra("FILE_PATH", relativeParentPath)
    intent.putExtra("REPO_PATH", repoPath)
    intent.putExtra(PasswordCreationActivity.EXTRA_FILE_NAME, name)
    intent.putExtra(PasswordCreationActivity.EXTRA_PASSWORD, passwordEntry?.password)
    intent.putExtra(PasswordCreationActivity.EXTRA_EXTRA_CONTENT, passwordEntry?.extraContentString)
    intent.putExtra(PasswordCreationActivity.EXTRA_EDITING, true)
    startActivity(intent)
    finish()
  }

  private fun shareAsPlaintext() {
    val sendIntent =
      Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, passwordEntry?.password)
        type = "text/plain"
      }
    // Always show a picker to give the user a chance to cancel
    startActivity(
      Intent.createChooser(sendIntent, resources.getText(R.string.send_plaintext_password_to))
    )
  }

  @OptIn(ExperimentalTime::class)
  private fun decryptAndVerify(receivedIntent: Intent? = null) {
    if (api == null) {
      bindToOpenKeychain(this)
      return
    }
    val data = receivedIntent ?: Intent()
    data.action = OpenPgpApi.ACTION_DECRYPT_VERIFY

    val inputStream = File(fullPath).inputStream()
    val outputStream = ByteArrayOutputStream()

    lifecycleScope.launch(Dispatchers.Main) {
      val result =
        withContext(Dispatchers.IO) {
          checkNotNull(api).executeApi(data, inputStream, outputStream)
        }
      when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
        OpenPgpApi.RESULT_CODE_SUCCESS -> {
          startAutoDismissTimer()
          runCatching {
              val showPassword = settings.getBoolean(PreferenceKeys.SHOW_PASSWORD, true)
              val entry = passwordEntryFactory.create(outputStream.toByteArray())

              if (settings.getBoolean(PreferenceKeys.COPY_ON_DECRYPT, false)) {
                copyPasswordToClipboard(entry.password)
              }

              passwordEntry = entry
              invalidateOptionsMenu()

              val items = arrayListOf<FieldItem>()
              if (!entry.password.isNullOrBlank()) {
                items.add(FieldItem.createPasswordField(entry.password!!))
              }

              if (entry.hasTotp()) {
                items.add(FieldItem.createOtpField(entry.totp.first()))
              }

              if (!entry.username.isNullOrBlank()) {
                items.add(FieldItem.createUsernameField(entry.username!!))
              }

              entry.extraContent.forEach { (key, value) ->
                items.add(FieldItem(key, value, FieldItem.ActionType.COPY))
              }

              val adapter =
                FieldItemAdapter(items, showPassword) { text -> copyTextToClipboard(text) }
              binding.recyclerView.adapter = adapter
              binding.recyclerView.itemAnimator = null

              if (entry.hasTotp()) {
                entry.totp.onEach(adapter::updateOTPCode).launchIn(lifecycleScope)
              }
            }
            .onFailure { e -> logcat(ERROR) { e.asLog() } }
        }
        OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
          val sender = getUserInteractionRequestIntent(result)
          userInteractionRequiredResult.launch(IntentSenderRequest.Builder(sender).build())
        }
        OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
      }
    }
  }
}