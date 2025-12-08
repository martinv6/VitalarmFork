package com.example.vitalarmapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.vitalarmapp.databinding.ActivityAddPatsBinding
import com.example.vitalarmapp.utils.firebase.AddPersonResult
import com.example.vitalarmapp.utils.firebase.FirebasePatientRegistrar
import com.example.vitalarmapp.utils.firebase.PatientRegistrar
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class AddPatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPatsBinding
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPatsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.patientToolbar)
        binding.patientToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        binding.patientBirthDateInputLayout.isEndIconVisible = false
        binding.patientBirthDateTf.setOnClickListener { showBirthDatePicker() }
        binding.patientBirthDateTf.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showBirthDatePicker()
        }
        binding.patientBirthDateInputLayout.setEndIconOnClickListener { clearBirthDate() }
        binding.patientContinueBtn.setOnClickListener {
            savePatient()
        }

        binding.patientNameInputLayout.setEndIconOnClickListener { binding.patientNameTf.text = null }
        binding.patientGenderInputLayout.setEndIconOnClickListener { binding.patientGenderTf.text = null }
        binding.patientNotesInputLayout.setEndIconOnClickListener { binding.patientNotesTf.text = null }

        binding.patientNameTf.doAfterTextChanged {
            binding.patientNameInputLayout.error = null
        }

        binding.patientGenderTf.doAfterTextChanged {
            binding.patientGenderInputLayout.error = null
        }

        binding.patientBirthDateTf.doAfterTextChanged {
            if (it.isNullOrEmpty()) {
                binding.patientBirthDateInputLayout.isEndIconVisible = false
                binding.patientBirthDateInputLayout.error = null
            } else {
                binding.patientBirthDateInputLayout.isEndIconVisible = true
                binding.patientBirthDateInputLayout.error = null
            }
        }

        binding.patientNotesTf.doAfterTextChanged {
            binding.patientNotesInputLayout.error = null
        }
    }

    private fun savePatient() {
        val name = binding.patientNameTf.text?.toString()?.trim().orEmpty()
        val gender = binding.patientGenderTf.text?.toString()?.trim().orEmpty()
        val birthDate = binding.patientBirthDateTf.text?.toString()?.trim().orEmpty()
        val notes = binding.patientNotesTf.text?.toString()?.trim().orEmpty()

        var hasError = false

        if (name.isBlank()) {
            binding.patientNameInputLayout.error = getString(R.string.add_patient_name_error)
            hasError = true
        } else {
            binding.patientNameInputLayout.error = null
        }

        if (gender.isBlank()) {
            binding.patientGenderInputLayout.error = getString(R.string.add_patient_gender_error)
            hasError = true
        } else {
            binding.patientGenderInputLayout.error = null
        }

        if (birthDate.isBlank()) {
            binding.patientBirthDateInputLayout.error = getString(R.string.add_patient_birthdate_error)
            hasError = true
        } else {
            binding.patientBirthDateInputLayout.error = null
        }

        if (notes.isBlank()) {
            binding.patientNotesInputLayout.error = getString(R.string.add_patient_notes_error)
            hasError = true
        } else {
            binding.patientNotesInputLayout.error = null
        }

        if (hasError) return

        if (patientRegistrar.getCurrentUserId().isNullOrEmpty()) {
            showErrorSnackbar(
                messageRes = R.string.add_patient_auth_error_snackbar,
                detail = getString(R.string.add_patient_generic_error_detail)
            )
            return
        }

        binding.patientContinueBtn.isEnabled = false

        lifecycleScope.launch {
            val result = patientRegistrar.addPerson(
                name = name,
                birthDate = birthDate,
                gender = gender,
                notes = notes
            )

            binding.patientContinueBtn.isEnabled = true

            when (result) {
                AddPersonResult.Success -> showSuccessDialog()

                is AddPersonResult.AuthError -> showErrorSnackbar(
                    messageRes = R.string.add_patient_auth_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.ConnectionError -> showErrorSnackbar(
                    messageRes = R.string.add_patient_connection_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.PermissionDenied -> showErrorSnackbar(
                    messageRes = R.string.add_patient_permission_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.ServiceUnavailable -> showErrorSnackbar(
                    messageRes = R.string.add_patient_unavailable_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.Timeout -> showErrorSnackbar(
                    messageRes = R.string.add_patient_timeout_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.QuotaExceeded -> showErrorSnackbar(
                    messageRes = R.string.add_patient_quota_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.InvalidData -> showErrorSnackbar(
                    messageRes = R.string.add_patient_invalid_data_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.OperationCancelled -> showErrorSnackbar(
                    messageRes = R.string.add_patient_cancelled_error_snackbar,
                    detail = result.message
                )

                is AddPersonResult.UnknownError -> showErrorSnackbar(
                    messageRes = R.string.add_patient_unknown_error_snackbar,
                    detail = result.message
                )
            }
        }
    }

    private fun showErrorSnackbar(messageRes: Int, detail: String?) {
        Snackbar.make(
            binding.root,
            getString(messageRes, detail ?: getString(R.string.add_patient_generic_error_detail)),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_patient_success_dialog_title)
            .setMessage(R.string.add_patient_success_dialog_message)
            .setNegativeButton(R.string.add_patient_success_add_another) { dialog, _ ->
                resetForm()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.add_patient_success_back) { _, _ ->
                startActivity(Intent(this, AddMainTabActivity::class.java))
                finish()
            }
            .show()
    }

    private fun resetForm() {
        binding.patientNameTf.text = null
        binding.patientGenderTf.text = null
        binding.patientNotesTf.text = null
        clearBirthDate()
    }

    private fun showBirthDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.add_patient_birthdate_placeholder))
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val formattedDate = selection?.let { dateFormatter.format(Date(it)) }.orEmpty()
            binding.patientBirthDateTf.setText(formattedDate)
        }

        picker.addOnNegativeButtonClickListener { clearBirthDate() }
        picker.addOnCancelListener { clearBirthDate() }

        picker.show(supportFragmentManager, "patient_birth_date_picker")
    }

    private fun clearBirthDate() {
        binding.patientBirthDateTf.text = null
        binding.patientBirthDateInputLayout.isEndIconVisible = false
    }

    companion object {
        internal var patientRegistrar: PatientRegistrar = FirebasePatientRegistrar

        fun intent(context: Context): Intent = Intent(context, AddPatsActivity::class.java)
    }
}
