package com.example.vitalarmapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vitalarmapp.adapters.MedicationSearchAdapter
import com.example.vitalarmapp.adapters.MedicationSearchItem
import com.example.vitalarmapp.databinding.ActivityAddMedsBinding
import com.example.vitalarmapp.utils.firebase.FirebaseManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddMedsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMedsBinding
    private val allMedications = mutableListOf<MedicationSearchItem>()
    private val adapter by lazy { MedicationSearchAdapter(emptyList(), ::onMedicationSelected) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMedsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        setupToolbar()
        setupRecycler()
        setupSearch()
        loadBaseMedications()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecycler() {
        binding.rvMedications.layoutManager = LinearLayoutManager(this)
        binding.rvMedications.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchBar.setNavigationOnClickListener {
            if (binding.searchView.isShowing) {
                binding.searchView.hide()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.searchBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_search) {
                openSearchView()
                true
            } else {
                false
            }
        }

        binding.searchView.setupWithSearchBar(binding.searchBar)
        binding.searchView.editText.hint = getString(R.string.add_meds_search_placeholder)

        binding.searchView.editText.doOnTextChanged { text, _, _, _ ->
            filterResults(text?.toString())
            updateAddMedicationState()
        }

        binding.searchView.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterResults(binding.searchView.editText.text?.toString())
                binding.searchView.hide()
                true
            } else {
                false
            }
        }

        updateAddMedicationState()
    }

    private fun openSearchView() {
        if (!binding.searchView.isShowing) {
            binding.searchView.show()
        }
        binding.searchView.editText.requestFocus()
    }

    private fun loadBaseMedications() {
        lifecycleScope.launch {
            toggleLoading(true)
            try {
                val medications = withContext(Dispatchers.IO) {
                    FirebaseManager.getBaseMedications().map { baseMedication ->
                        MedicationSearchItem(
                            name = baseMedication["name"] as? String
                                ?: getString(R.string.add_meds_unknown),
                            description = baseMedication["description"] as? String
                        )
                    }
                }.sortedBy { it.name.lowercase() }

                allMedications.clear()
                allMedications.addAll(medications)
                filterResults(binding.searchView.editText.text?.toString())
            } catch (error: Exception) {
                Snackbar.make(binding.root, getString(R.string.add_meds_load_error), Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.rvMedications)
                    .show()
                adapter.updateData(emptyList())
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun filterResults(query: String?) {
        val filtered = if (query.isNullOrBlank()) {
            allMedications
        } else {
            val lowerQuery = query.lowercase()
            allMedications.filter { medication ->
                medication.name.lowercase().contains(lowerQuery) ||
                    medication.description?.lowercase()?.contains(lowerQuery) == true
            }
        }
        adapter.updateData(filtered)
    }

    private fun onMedicationSelected(item: MedicationSearchItem) {
        val message = getString(R.string.add_meds_selected_format, item.name)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.rvMedications)
            .show()
    }

    private fun toggleLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.searchBar.isEnabled = !isLoading
        binding.searchView.isEnabled = !isLoading
        binding.searchView.editText.isEnabled = !isLoading
        binding.addMedicationButton.isEnabled = !isLoading && !binding.searchView.editText.text.isNullOrBlank()
        binding.rvMedications.isVisible = !isLoading
    }

    private fun updateAddMedicationState() {
        binding.addMedicationButton.isEnabled = binding.progressBar.isVisible.not() &&
            binding.searchView.editText.text.isNullOrBlank().not()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AddMedsActivity::class.java)
    }
}
