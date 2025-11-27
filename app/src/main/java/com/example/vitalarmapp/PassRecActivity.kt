package com.example.vitalarmapp

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.example.vitalarmapp.databinding.ActivityPassRecBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class PassRecActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPassRecBinding
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPassRecBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        setupToolbar()
        setupEmailWatcher()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.passRecToolbar)
        binding.passRecToolbar.navigationContentDescription =
            getString(R.string.login_toolbar_navigation_description)
        if (isNightModeActive()) {
            val navigationColor = ContextCompat.getColor(this, R.color.md_theme_onBackground)
            binding.passRecToolbar.navigationIcon?.setTint(navigationColor)
        }
        binding.passRecToolbar.setNavigationOnClickListener {
            navigateBackToLogin()
        }
    }

    private fun setupEmailWatcher() {
        binding.passRecConfirmBtn.isEnabled = false
        binding.passRecEmailTv.doOnTextChanged { text, _, _, _ ->
            val isValid = !text.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(text).matches()
            binding.passRecConfirmBtn.isEnabled = isValid
            if (isValid) {
                binding.passRecEmailInputLayout.error = null
            }
        }
    }

    private fun setupListeners() {
        binding.passRecConfirmBtn.setOnClickListener {
            navigateToPassReset()
        }
    }

    private fun isNightModeActive(): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun navigateBackToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun navigateToPassReset() {
        val email = binding.passRecEmailTv.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.passRecEmailInputLayout.error = getString(R.string.pass_rec_invalid_email)
            return
        }

        sendResetEmail(email)
    }

    private fun sendResetEmail(email: String) {
        setLoading(true)
        firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                val intent = Intent(this@PassRecActivity, PassResetWaitingActivity::class.java)
                intent.putExtra(PassResetWaitingActivity.EXTRA_EMAIL, email)
                startActivity(intent)
            } else {
                val exception = task.exception
                val message = when (exception) {
                    is FirebaseAuthInvalidUserException -> getString(R.string.pass_rec_error_user_not_found)
                    is FirebaseNetworkException -> getString(R.string.error_detail_network)
                    else -> getString(R.string.pass_rec_error_generic)
                }

                if (exception is FirebaseAuthInvalidUserException) {
                    showEmailNotRegisteredDialog(email)
                }

                binding.passRecEmailInputLayout.error = message
                Snackbar.make(binding.passRecCoordinator, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showEmailNotRegisteredDialog(email: String) {
        MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle(getString(R.string.pass_rec_not_registered_title))
            .setMessage(getString(R.string.pass_rec_not_registered_message, email))
            .setNegativeButton(getString(R.string.not_for_now), null)
            .setPositiveButton(R.string.pass_rec_not_registered_register) { _, _ ->
                startActivity(Intent(this, SignUpActivity::class.java))
            }
            .show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.passRecConfirmBtn.isEnabled = !isLoading
        binding.passRecEmailTv.isEnabled = !isLoading
        binding.passRecProgressIndicator.isVisible = isLoading
        binding.passRecConfirmBtn.text =
            getString(if (isLoading) R.string.pass_rec_sending_email else R.string.confirm_email)
    }
}
