@file:Suppress("SpellCheckingInspection")

package com.example.vitalarmapp

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vitalarmapp.adapters.MedicationForm
import com.example.vitalarmapp.adapters.MedicationSearchAdapter
import com.example.vitalarmapp.adapters.MedicationSearchItem
import com.example.vitalarmapp.databinding.ActivityAddMedsBinding
import com.example.vitalarmapp.databinding.DialogMedicationDosageBinding
import com.example.vitalarmapp.models.Medication
import com.example.vitalarmapp.utils.firebase.FirebaseManager
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.search.SearchView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.transition.MaterialFadeThrough
import androidx.transition.TransitionManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.IdentifiedLanguage
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AddMedsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMedsBinding
    private val gson: Gson by lazy { Gson() }
    private val defaultCardTopMargin by lazy {
        resources.getDimensionPixelSize(R.dimen.add_meds_card_margin_with_search)
    }
    private val selectionCardTopMargin by lazy {
        resources.getDimensionPixelSize(R.dimen.add_meds_card_margin_with_selection)
    }
    private var searchJob: Job? = null
    private var translationJob: Job? = null
    private val languageIdClient by lazy {
        val options = LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
        LanguageIdentification.getClient(options)
    }
    private val translatorCache = mutableMapOf<String, Translator>()
    private lateinit var searchAdapter: MedicationSearchAdapter
    private var patientId: String? = null
    private var selectedMedication: MedicationSearchItem? = null
    private var displayedMedication: MedicationSearchItem? = null
    private var ignoreQueryChanges: Boolean = false
    private var isSelectionMode: Boolean = false
    private var dosageDialog: AlertDialog? = null
    private var pendingDosage: DosageInput? = null
    private val fadeThrough by lazy {
        MaterialFadeThrough().apply {
            duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        }
    }

    private fun buildDosageLabel(item: MedicationSearchItem): String? {
        val value = item.dosageValue?.takeIf { it.isNotBlank() }
        val unit = item.dosageUnit?.takeIf { it.isNotBlank() }
        return if (value != null && unit != null) "$value $unit" else null
    }

    private fun formLabelRes(form: MedicationForm): Int = when (form) {
        MedicationForm.TABLET -> R.string.add_meds_dosage_form_tablet
        MedicationForm.CAPSULE -> R.string.add_meds_dosage_form_capsule
        MedicationForm.SYRUP -> R.string.add_meds_dosage_form_syrup
        MedicationForm.DROPS -> R.string.add_meds_dosage_form_drops
        MedicationForm.INJECTION -> R.string.add_meds_dosage_form_injection
        MedicationForm.OTHER -> R.string.add_meds_dosage_form_other
    }

    private fun promptDosageDialog(item: MedicationSearchItem) {
        dosageDialog?.dismiss()
        val dialogBinding = DialogMedicationDosageBinding.inflate(LayoutInflater.from(this))
        val formOptions = MedicationForm.entries.toTypedArray()
        val formLabels = formOptions.map { getString(formLabelRes(it)) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.add_meds_dosage_save, null)
            .create()
        dialog.setCanceledOnTouchOutside(false)

        dialog.setOnShowListener {
            dialogBinding.medicationFormField.setSimpleItems(formLabels.toTypedArray())
            dialogBinding.medicationFormField.setOnItemClickListener { _, _, position, _ ->
                val form = formOptions.getOrNull(position) ?: return@setOnItemClickListener
                dialogBinding.medicationFormField.tag = form
                dialogBinding.medicationFormLayout.error = null
                dialogBinding.medicationUnitLayout.error = null
                updateUnitsForForm(dialogBinding.medicationUnitField, form)
            }

            dialogBinding.medicationUnitField.setOnItemClickListener { _, _, _, _ ->
                dialogBinding.medicationUnitLayout.error = null
            }

            dialogBinding.medicationDoseField.addTextChangedListener {
                dialogBinding.medicationDoseLayout.error = null
            }

            prefillDosageFields(dialogBinding, item)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = dialogBinding.medicationDoseField.text?.toString()?.trim().orEmpty()
                val form = dialogBinding.medicationFormField.tag as? MedicationForm
                val unit = dialogBinding.medicationUnitField.text?.toString()?.trim().orEmpty()

                var hasError = false
                if (form == null || unit.isBlank()) {
                    dialogBinding.medicationFormLayout.error =
                        getString(R.string.add_meds_dosage_unit_error)
                    dialogBinding.medicationUnitLayout.error =
                        getString(R.string.add_meds_dosage_unit_error)
                    hasError = true
                }

                if (amount.isBlank()) {
                    dialogBinding.medicationDoseLayout.error =
                        getString(R.string.add_meds_dosage_amount_error)
                    hasError = true
                }

                if (hasError) return@setOnClickListener

                val dosage = DosageInput(
                    amount = amount,
                    unit = unit,
                    form = form!!,
                )
                pendingDosage = dosage
                applyDosageToSelection(dosage)
                dialog.dismiss()
            }
        }

        dosageDialog = dialog
        dialog.show()
    }

    private fun prefillDosageFields(
        dialogBinding: DialogMedicationDosageBinding,
        item: MedicationSearchItem,
    ) {
        val existingForm = item.form ?: pendingDosage?.form
        val existingAmount = item.dosageValue ?: pendingDosage?.amount
        val existingUnit = item.dosageUnit ?: pendingDosage?.unit

        existingForm?.let { form ->
            dialogBinding.medicationFormField.tag = form
            dialogBinding.medicationFormField.setText(getString(formLabelRes(form)), false)
            updateUnitsForForm(dialogBinding.medicationUnitField, form)
        }

        existingUnit?.let { unit ->
            dialogBinding.medicationUnitField.setText(unit, false)
        }

        existingAmount?.let { dialogBinding.medicationDoseField.setText(it) }
    }

    private fun updateUnitsForForm(
        autoCompleteTextView: MaterialAutoCompleteTextView,
        form: MedicationForm,
    ) {
        autoCompleteTextView.setSimpleItems(form.allowedUnits.toTypedArray())
        if (autoCompleteTextView.text.isNullOrBlank()) {
            autoCompleteTextView.setText(form.allowedUnits.firstOrNull().orEmpty(), false)
        }
    }

    private fun applyDosageToSelection(dosage: DosageInput) {
        val current = displayedMedication ?: selectedMedication ?: return
        val updated = current.copy(
            dosageValue = dosage.amount,
            dosageUnit = dosage.unit,
            form = dosage.form,
        )
        displayedMedication = updated
        selectedMedication = updated
        showSelectedMedicationCard(updated)
        updateAddMedicationState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMedsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        patientId = intent.getStringExtra(EXTRA_PATIENT_ID)

        setupSearchBar()
        setupSearch()
        setupSelectionAppBar()
        setupSummaryAppBar()
        setupAddAction()
        updateSelectedMedicationCardSpacing(false)
    }

    private fun setupSearchBar() {
        binding.searchBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_search) {
                openSearchView()
                true
            } else {
                false
            }
        }
    }

    private fun setupSearch() {
        binding.searchView.setupWithSearchBar(binding.searchBar)
        binding.searchBar.setOnClickListener { openSearchView() }

        binding.searchBar.setNavigationOnClickListener {
            if (binding.searchView.isShowing) {
                binding.searchView.hide()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.searchView.addTransitionListener { _, _, newState ->
            when (newState) {
                SearchView.TransitionState.HIDDEN -> {
                    binding.searchView.visibility = View.GONE
                    if (selectedMedication != null) {
                        binding.selectedMedicationCard.isVisible = true
                    }
                    showAppropriateTopBar()
                }

                SearchView.TransitionState.SHOWN, SearchView.TransitionState.SHOWING -> {
                    binding.summaryCollapsingToolbar.visibility = View.GONE
                    binding.searchBar.visibility = View.GONE
                    binding.searchView.visibility = View.VISIBLE
                    binding.selectedMedicationCard.isVisible = false
                }

                else -> Unit
            }
        }
        binding.searchView.editText.hint = getString(R.string.add_meds_search_placeholder)

        searchAdapter = MedicationSearchAdapter(emptyList()) { medication ->
            onMedicationSelected(medication)
        }
        binding.rvMedicationResults.apply {
            layoutManager = LinearLayoutManager(this@AddMedsActivity)
            adapter = searchAdapter
            addItemDecoration(
                DividerItemDecoration(
                    this@AddMedsActivity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        binding.searchView.editText.addTextChangedListener { editable ->
            if (ignoreQueryChanges) return@addTextChangedListener
            onQueryChanged(editable?.toString().orEmpty())
        }

        updateAddMedicationState()
    }

    private fun setupAddAction() {
        binding.addMedicationButton.setOnClickListener {
            val medication = displayedMedication ?: return@setOnClickListener

            if (medication.dosageValue.isNullOrBlank() ||
                medication.dosageUnit.isNullOrBlank() ||
                medication.form == null
            ) {
                promptDosageDialog(medication)
                return@setOnClickListener
            }

            if (FirebaseManager.getCurrentUserId().isNullOrEmpty()) {
                Snackbar.make(
                    binding.root,
                    R.string.add_patient_auth_error_snackbar,
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            binding.addMedicationButton.isEnabled = false

            lifecycleScope.launch {
                val saved = if (patientId.isNullOrBlank()) {
                    FirebaseManager.saveRegisteredMedication(medication)
                } else {
                    FirebaseManager.saveMedicationForPerson(
                        patientId = patientId!!,
                        medication = medication.toMedication(patientId!!)
                    )
                }
                binding.addMedicationButton.isEnabled = true

                if (saved) {
                    showAddConfirmationDialog()
                } else {
                    Snackbar.make(
                        binding.root,
                        R.string.add_meds_save_error,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupSelectionAppBar() {
        binding.selectionTopAppBar.setNavigationOnClickListener {
            exitSelectionMode()
        }

        binding.selectionTopAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_selection -> {
                    removeSelectedMedication()
                    true
                }

                else -> false
            }
        }

        binding.selectedMedicationCard.setOnLongClickListener {
            if (selectedMedication != null) {
                enterSelectionMode()
                true
            } else {
                false
            }
        }
    }

    private fun setupSummaryAppBar() {
        binding.summaryTopAppBar.setNavigationOnClickListener { showCancelSummaryDialog() }
    }

    private fun openSearchView() {
        if (!binding.searchView.isShowing) {
            binding.searchView.show()
        }
        binding.searchView.editText.requestFocus()
        binding.searchView.editText.post {
            val inputMethodManager =
                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(
                binding.searchView.editText,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun onQueryChanged(query: String) {
        selectedMedication = null
        displayedMedication = null
        searchJob?.cancel()
        showLoading(false)

        if (query.length < 2) {
            searchAdapter.updateData(emptyList())
            showStatusMessage(getString(R.string.add_meds_search_start))
            showAppropriateTopBar()
            updateAddMedicationState()
            return
        }

        searchJob = lifecycleScope.launch {
            delay(350)
            searchMedications(query)
        }
    }

    private suspend fun searchMedications(query: String) {
        showLoading(true, getString(R.string.add_meds_search_loading))

        val items = try {
            withContext(Dispatchers.IO) { fetchMedications(query) }
        } catch (_: Exception) {
            showLoading(false)
            showStatusMessage(getString(R.string.add_meds_search_error))
            searchAdapter.updateData(emptyList())
            Snackbar.make(binding.root, R.string.add_meds_search_error, Snackbar.LENGTH_LONG).show()
            updateAddMedicationState()
            return
        }

        showLoading(false)

        if (items.isEmpty()) {
            showStatusMessage(getString(R.string.add_meds_search_no_results, query))
        } else {
            showStatusMessage(null)
        }
        searchAdapter.updateData(items)

        updateAddMedicationState()
    }

    private fun fetchMedications(query: String): List<MedicationSearchItem> {
        val encodedQuery = URLEncoder.encode(
            "patient.drug.medicinalproduct:\"$query\"",
            StandardCharsets.UTF_8.toString()
        )
        val urlBuilder = StringBuilder("https://api.fda.gov/drug/event.json")
            .append("?limit=20&search=")
            .append(encodedQuery)

        if (BuildConfig.OPEN_FDA_API_KEY.isNotBlank()) {
            urlBuilder.append("&api_key=").append(BuildConfig.OPEN_FDA_API_KEY)
        }

        val connection = URL(urlBuilder.toString()).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        return try {
            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                throw IllegalStateException("OpenFDA error ${connection.responseCode}")
            }

            responseStream.bufferedReader().use { reader ->
                gson.fromJson(reader, OpenFdaResponse::class.java)
            }.toMedicationItems()
        } finally {
            connection.disconnect()
        }
    }

    private fun onMedicationSelected(item: MedicationSearchItem) {
        ignoreQueryChanges = true
        selectedMedication = item
        displayedMedication = item
        pendingDosage = null
        binding.searchView.editText.setText("")
        binding.searchBar.setText("")
        binding.searchView.hide()
        binding.searchView.editText.clearFocus()
        binding.searchBar.clearFocus()
        hideKeyboard()
        ignoreQueryChanges = false
        showSelectedMedicationCard(item)
        updateAddMedicationState()
    }

    private fun showSelectedMedicationCard(item: MedicationSearchItem) {
        TransitionManager.beginDelayedTransition(binding.contentContainer, fadeThrough)
        binding.selectedMedicationCard.isVisible = true
        val formattedName = formatCardText(item.name)
        binding.selectedMedicationName.text = formattedName.orEmpty()
        updateSelectedMedicationCardSpacing(false)

        val indicationText = formatCardText(item.indication)
        binding.selectedMedicationIndication.isVisible = indicationText != null
        binding.selectedMedicationIndication.text = indicationText ?: ""

        val pathologyText = formatCardText(item.pharmacology)
        binding.selectedMedicationPathology.isVisible = pathologyText != null
        binding.selectedMedicationPathology.text = pathologyText ?: ""

        val routeText = formatCardText(item.route)
        binding.selectedMedicationRouteContainer.isVisible = routeText != null
        binding.selectedMedicationRoute.text = routeText ?: ""

        val compositionText = formatCardText(item.composition ?: item.pharmacology)
        binding.selectedMedicationCompositionContainer.isVisible = compositionText != null
        binding.selectedMedicationComposition.text = compositionText ?: ""

        val dosageText = buildDosageLabel(item)
        binding.selectedMedicationDosageContainer.isVisible = dosageText != null
        binding.selectedMedicationDosage.text = dosageText ?: ""
        val formLabel = item.form?.let { getString(formLabelRes(it)) }
        binding.selectedMedicationFormContainer.isVisible = formLabel != null
        binding.selectedMedicationForm.text = formLabel.orEmpty()

        updateDisplayedMedication(
            item,
            formattedName,
            indicationText,
            pathologyText,
            routeText,
            compositionText,
            item.dosageValue,
            item.dosageUnit,
            item.form
        )

        if (item.dosageUnit.isNullOrBlank()) {
            promptDosageDialog(item)
        }
        translateSelectedMedicationCard(item)
        showAppropriateTopBar()
    }

    private fun translateSelectedMedicationCard(item: MedicationSearchItem) {
        if (!shouldTranslateToSpanishUnitedStates()) return

        translationJob?.cancel()
        translationJob = lifecycleScope.launch {
            val translatedName = translateText(item.name)
            val translatedIndication = translateText(item.indication)
            val translatedPharmacology = translateText(item.pharmacology)
            val translatedRoute = translateText(item.route)
            val translatedComposition = translateText(item.composition ?: item.pharmacology)

            if (selectedMedication != item) return@launch

            val finalName = translatedName ?: binding.selectedMedicationName.text?.toString()
            binding.selectedMedicationName.text = finalName

            val indicationText = translatedIndication ?: formatCardText(item.indication)
            binding.selectedMedicationIndication.isVisible = indicationText != null
            binding.selectedMedicationIndication.text = indicationText ?: ""

            val pathologyText = translatedPharmacology ?: formatCardText(item.pharmacology)
            binding.selectedMedicationPathology.isVisible = pathologyText != null
            binding.selectedMedicationPathology.text = pathologyText ?: ""

            val routeText = translatedRoute ?: formatCardText(item.route)
            binding.selectedMedicationRouteContainer.isVisible = routeText != null
            binding.selectedMedicationRoute.text = routeText ?: ""

            val compositionText = translatedComposition
                ?: formatCardText(item.composition ?: item.pharmacology)
            binding.selectedMedicationCompositionContainer.isVisible = compositionText != null
            binding.selectedMedicationComposition.text = compositionText ?: ""

            updateDisplayedMedication(
                item,
                finalName,
                indicationText,
                pathologyText,
                routeText,
                compositionText,
                item.dosageValue,
                item.dosageUnit,
                item.form
            )
        }
    }

    private fun shouldTranslateToSpanishUnitedStates(): Boolean {
        val locale = resources.configuration.locales.get(0)
        return locale.language.equals("es", ignoreCase = true) &&
            locale.country.equals("US", ignoreCase = true)
    }

    private suspend fun ensureTranslatorReady(sourceLanguage: String): Translator {
        translatorCache[sourceLanguage]?.let { return it }

        return withContext(Dispatchers.IO) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build()
            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            translatorCache[sourceLanguage] = translator
            translator
        }
    }

    private suspend fun translateText(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val detectedLanguage = detectLanguage(normalized) ?: return null

        if (detectedLanguage.equals(TranslateLanguage.SPANISH, ignoreCase = true)) {
            return formatCardText(normalized)
        }

        val sourceLanguage = TranslateLanguage.fromLanguageTag(detectedLanguage) ?: return null

        return runCatching {
            val translator = ensureTranslatorReady(sourceLanguage)
            translator.translate(normalized).await()
        }.getOrNull()?.let { formatCardText(it) }
    }

    private suspend fun detectLanguage(text: String): String? {
        val primary = runCatching { languageIdClient.identifyLanguage(text).await() }
            .getOrNull()
            ?.takeUnless { it.equals("und", ignoreCase = true) }
        if (primary != null) return primary

        val possibleLanguages = runCatching { languageIdClient.identifyPossibleLanguages(text).await() }
            .getOrNull()
            .orEmpty()
        return selectBestLanguage(possibleLanguages)
    }

    private fun selectBestLanguage(possibleLanguages: List<IdentifiedLanguage>): String? {
        return possibleLanguages
            .filterNot { it.languageTag.equals("und", ignoreCase = true) }
            .maxByOrNull { it.confidence }
            ?.languageTag
    }

    private fun showLoading(isLoading: Boolean, status: String? = null) {
        binding.progressBar.isVisible = isLoading
        if (isLoading) {
            showStatusMessage(status)
        }
    }

    private fun showStatusMessage(message: String?) {
        binding.tvSearchStatus.isVisible = !message.isNullOrBlank()
        binding.tvSearchStatus.text = message
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun updateAddMedicationState() {
        val hasSelection = displayedMedication != null
        val hasDosage = displayedMedication?.let {
            !it.dosageUnit.isNullOrBlank() && !it.dosageValue.isNullOrBlank() && it.form != null
        } ?: false
        binding.addMedicationButton.isEnabled = binding.progressBar.isVisible.not() && hasSelection && hasDosage
        binding.selectedMedicationCard.isVisible = hasSelection
        showAppropriateTopBar()
    }

    private fun updateSelectedMedicationCardSpacing(isSelectionMode: Boolean) {
        val newTopMargin = if (isSelectionMode) selectionCardTopMargin else defaultCardTopMargin
        val layoutParams = binding.selectedMedicationCard.layoutParams as ConstraintLayout.LayoutParams

        if (layoutParams.topMargin != newTopMargin) {
            layoutParams.topMargin = newTopMargin
            binding.selectedMedicationCard.layoutParams = layoutParams
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        binding.searchBar.visibility = View.GONE
        binding.summaryCollapsingToolbar.visibility = View.GONE
        binding.selectionTopAppBar.visibility = View.VISIBLE
        binding.selectionTopAppBar.title = getString(R.string.add_meds_selection_count, 1)
        updateSelectedMedicationCardSpacing(true)
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        binding.selectionTopAppBar.visibility = View.GONE
        showAppropriateTopBar()
        updateSelectedMedicationCardSpacing(false)
    }

    private data class DosageInput(
        val amount: String,
        val unit: String,
        val form: MedicationForm,
    )

    private fun removeSelectedMedication() {
        val removedMedicationName = selectedMedication?.name
        selectedMedication = null
        displayedMedication = null
        pendingDosage = null
        binding.selectedMedicationName.text = null
        binding.selectedMedicationIndication.text = null
        binding.selectedMedicationPathology.text = null
        binding.selectedMedicationRoute.text = null
        binding.selectedMedicationComposition.text = null
        binding.selectedMedicationDosage.text = null
        binding.selectedMedicationForm.text = null
        binding.selectedMedicationCard.isVisible = false
        binding.searchBar.setText("")
        exitSelectionMode()
        updateAddMedicationState()

        removedMedicationName?.let { name ->
            Snackbar.make(
                binding.root,
                getString(R.string.add_meds_selection_removed_message, name),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun MedicationSearchItem.toMedication(patientId: String): Medication {
        val dosageValue = dosageValue?.takeIf { it.isNotBlank() }
        val dosageUnit = dosageUnit?.takeIf { it.isNotBlank() }
        val dosageLabel = listOfNotNull(dosageValue, dosageUnit)
            .joinToString(" ")
            .trim()

        return Medication(
            personId = patientId,
            name = name,
            dosage = dosageLabel,
            frequency = "",
            alarmTimes = emptyList(),
            createdAt = System.currentTimeMillis()
        )
    }

    override fun onDestroy() {
        searchJob?.cancel()
        translationJob?.cancel()
        dosageDialog?.dismiss()
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATIENT_ID = "extra_patient_id"

        fun intent(context: Context, patientId: String? = null): Intent =
            Intent(context, AddMedsActivity::class.java).apply {
                patientId?.let { putExtra(EXTRA_PATIENT_ID, it) }
            }
    }

    private fun OpenFdaResponse?.toMedicationItems(): List<MedicationSearchItem> {
        val uniqueItems = LinkedHashMap<String, MedicationSearchItem>()

        this?.results.orEmpty().forEach { result ->
            result.patient?.drug.orEmpty().forEach { drug ->
                val rawName =
                    drug.displayName()?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                val normalizedName = capitalizeName(rawName)
                val indication = drug.drugIndication?.trim().takeIf { it?.isNotBlank() == true }
                val pharmacology = drug.bestDescription()
                val route = drug.openFda?.route?.firstOrNull()?.trim()
                val composition = drug.openFda?.substanceName?.firstOrNull()?.trim()

                if (!uniqueItems.containsKey(rawName.lowercase())) {
                    uniqueItems[rawName.lowercase()] = MedicationSearchItem(
                        name = normalizedName,
                        indication = indication,
                        pharmacology = pharmacology,
                        route = route,
                        composition = composition
                    )
                }
            }
        }

        return uniqueItems.values.toList()
    }

    private fun Drug.displayName(): String? {
        return openFda?.genericName?.firstOrNull()
            ?: openFda?.brandName?.firstOrNull()
            ?: openFda?.substanceName?.firstOrNull()
            ?: medicinalProduct
    }

    private fun Drug.bestDescription(): String? {
        return openFda?.pharmClassEpc?.firstOrNull()
            ?: openFda?.pharmClassMoa?.firstOrNull()
            ?: drugIndication?.takeIf { it.isNotBlank() }
            ?: openFda?.route?.firstOrNull()
    }

    private fun capitalizeName(text: String): String {
        return text.lowercase().replaceFirstChar { char ->
            if (char.isLowerCase() || char.isUpperCase()) char.titlecase() else char.toString()
        }
    }

    private fun formatCardText(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
        return normalized?.replaceFirstChar { char ->
            if (char.isLowerCase() || char.isUpperCase()) char.titlecase() else char.toString()
        }
    }

    private fun updateDisplayedMedication(
        base: MedicationSearchItem,
        name: String?,
        indication: String?,
        pharmacology: String?,
        route: String?,
        composition: String?,
        dosageValue: String?,
        dosageUnit: String?,
        form: MedicationForm?,
    ) {
        displayedMedication = MedicationSearchItem(
            name?.takeIf { it.isNotBlank() } ?: base.name,
            indication,
            pharmacology,
            route,
            composition,
            dosageValue,
            dosageUnit,
            form
        )
    }

    private fun showAddConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_meds_dialog_title))
            .setMessage(getString(R.string.add_meds_dialog_body))
            .setNegativeButton(getString(R.string.add_meds_dialog_add_another)) { _, _ ->
                val restartIntent =
                    Intent(this, AddMedsActivity::class.java).addFlags(FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(restartIntent)
                finish()
            }
            .setPositiveButton(getString(R.string.add_meds_dialog_return)) { _, _ ->
                startActivity(AddMainTabActivity.intent(this).addFlags(FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .show()
    }

    private fun showCancelSummaryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_meds_summary_cancel_title))
            .setMessage(getString(R.string.add_meds_summary_cancel_message))
            .setNegativeButton(getString(R.string.add_meds_summary_cancel_confirm)) { _, _ ->
                startActivity(
                    AddMainTabActivity.intent(this).addFlags(FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            }
            .setPositiveButton(getString(R.string.add_meds_summary_cancel_dismiss)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }.addOnCanceledListener {
            continuation.cancel()
        }
    }

    private fun showAppropriateTopBar() {
        TransitionManager.beginDelayedTransition(binding.contentContainer, fadeThrough)
        if (isSelectionMode) {
            binding.searchBar.visibility = View.GONE
            binding.summaryCollapsingToolbar.visibility = View.GONE
            return
        }

        if (binding.searchView.isShowing) {
            binding.searchBar.visibility = View.GONE
            binding.summaryCollapsingToolbar.visibility = View.GONE
            return
        }

        val hasSelection = displayedMedication != null
        binding.summaryCollapsingToolbar.visibility = if (hasSelection) View.VISIBLE else View.GONE
        binding.searchBar.visibility = if (hasSelection) View.GONE else View.VISIBLE
    }

}

private data class OpenFdaResponse(
    val results: List<DrugEvent>?,
)

private data class DrugEvent(
    val patient: PatientInfo?,
)

private data class PatientInfo(
    val drug: List<Drug>?,
)

private data class Drug(
    @SerializedName("medicinalproduct") val medicinalProduct: String?,
    @SerializedName("drugindication") val drugIndication: String?,
    @SerializedName("openfda") val openFda: OpenFdaDetails?,
)

private data class OpenFdaDetails(
    @SerializedName("generic_name") val genericName: List<String>?,
    @SerializedName("brand_name") val brandName: List<String>?,
    @SerializedName("substance_name") val substanceName: List<String>?,
    @SerializedName("route") val route: List<String>?,
    @SerializedName("pharm_class_epc") val pharmClassEpc: List<String>?,
    @SerializedName("pharm_class_moa") val pharmClassMoa: List<String>?,
)
