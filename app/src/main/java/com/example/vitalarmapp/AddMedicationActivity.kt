package com.example.vitalarmapp

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.vitalarmapp.databinding.ActivityAddMedicationBinding
import com.example.vitalarmapp.utils.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class AddMedicationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddMedicationBinding
    private val alarmTimes = sortedSetOf<String>()
    private var selectedMedicationName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMedicationBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        val personId = intent.getStringExtra("personId") ?: ""
        val personName = intent.getStringExtra("personName") ?: ""

        binding.tvPersonName.text = "Para: $personName"

        loadBaseMedications()
        setupClickListeners(personId)
    }

    private fun loadBaseMedications() {
        lifecycleScope.launch {
            try {
                setLoading(true)

                val medications = withContext(Dispatchers.IO) {
                    FirebaseManager.getBaseMedications()
                }

                if (medications.isEmpty()) {
                    showNoMedicationsMessage()
                    return@launch
                }

                val medicationNames = medications.map {
                    it["name"] as? String ?: "Sin nombre"
                }

                // Spinner para seleccionar medicamento
                val adapter = ArrayAdapter(
                    this@AddMedicationActivity,
                    android.R.layout.simple_spinner_item,
                    medicationNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerMedications.adapter = adapter

                binding.spinnerMedications.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        selectedMedicationName = parent.getItemAtPosition(position) as? String ?: ""
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {
                        selectedMedicationName = ""
                    }
                }

                if (medicationNames.isNotEmpty()) {
                    binding.spinnerMedications.setSelection(0)
                }

                showMedicationSelection()

            } catch (e: Exception) {
                Log.e("AddMedication", "Error: ${e.message}")
                Toast.makeText(this@AddMedicationActivity, "Error al cargar medicamentos", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showNoMedicationsMessage() {
        binding.layoutMedicationSelection.visibility = View.GONE
        binding.layoutNoMedications.visibility = View.VISIBLE
    }

    private fun showMedicationSelection() {
        binding.layoutMedicationSelection.visibility = View.VISIBLE
        binding.layoutNoMedications.visibility = View.GONE
    }

    private fun setupClickListeners(personId: String) {
        binding.btnAddTime.setOnClickListener {
            showTimePicker()
        }

        binding.btnSaveMedication.setOnClickListener {
            // Obtener el medicamento seleccionado del spinner
            selectedMedicationName = binding.spinnerMedications.selectedItem as? String ?: ""
            addMedication(personId)
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnGoCreateMedications.setOnClickListener {
            startActivity(Intent(this, BaseMedicationsActivity::class.java))
            finish()
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val timeString = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)

            if (alarmTimes.add(timeString)) {
                updateAlarmTimesDisplay()
            } else {
                Toast.makeText(this, "El horario ya fue agregado", Toast.LENGTH_SHORT).show()
            }
        }, hour, minute, true)

        timePicker.show()
    }

    private fun updateAlarmTimesDisplay() {
        binding.tvAlarmTimes.text = if (alarmTimes.isNotEmpty()) {
            alarmTimes.sorted().joinToString("\n") { "⏰ $it" }
        } else {
            "No hay horarios"
        }
    }

    private fun addMedication(personId: String) {
        val dosage = binding.etDosage.text.toString().trim()
        val frequency = binding.etFrequency.text.toString().trim()

        if (selectedMedicationName.isEmpty()) {
            Toast.makeText(this, "Selecciona un medicamento", Toast.LENGTH_SHORT).show()
            return
        }

        if (dosage.isEmpty()) {
            binding.etDosage.error = "Ingresa la dosis"
            return
        }

        if (frequency.isEmpty()) {
            binding.etFrequency.error = "Ingresa la frecuencia"
            return
        }

        if (alarmTimes.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un horario", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                setLoading(true)

                val success = withContext(Dispatchers.IO) {
                    FirebaseManager.addMedication(personId, selectedMedicationName, dosage, frequency, alarmTimes.toList())
                }

                if (success) {
                    Toast.makeText(this@AddMedicationActivity, "✅ Medicamento agregado", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddMedicationActivity, "❌ Error al agregar", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("AddMedication", "Error: ${e.message}")
                Toast.makeText(this@AddMedicationActivity, "Error al agregar", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }

    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
    }
}
