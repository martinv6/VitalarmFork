package com.example.vitalarmapp.ui.lists

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.vitalarmapp.R
import com.example.vitalarmapp.adapters.MedicationSearchItem
import com.example.vitalarmapp.databinding.ItemAlarmEntryBinding
import com.example.vitalarmapp.databinding.ItemMedicationEntryBinding
import com.example.vitalarmapp.databinding.ItemPatientEntryBinding
import com.example.vitalarmapp.models.Patient
import com.google.android.material.color.MaterialColors
import java.time.LocalDateTime

internal data class AlarmListItem(
    val id: String,
    val medicationId: String,
    val patientId: String,
    val patientName: String,
    val medicationName: String,
    val scheduleText: String,
    val scheduledAt: LocalDateTime?,
    val originalTime: String,
)

internal data class MedicationListItem(
    val id: String,
    val medication: MedicationSearchItem,
)

internal data class PatientListItem(
    val patient: Patient,
)

internal class AlarmListAdapter(
    private val onLongPress: (AlarmListItem) -> Unit,
    private val onItemSelected: (AlarmListItem) -> Unit,
) : RecyclerView.Adapter<AlarmListAdapter.AlarmViewHolder>() {

    private val items = mutableListOf<AlarmListItem>()
    private val selectedIds = mutableSetOf<String>()
    private var selectionMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(items[position], selectionMode, selectedIds, onLongPress, onItemSelected)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<AlarmListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(selection: Set<String>, selectionMode: Boolean) {
        this.selectionMode = selectionMode
        selectedIds.clear()
        selectedIds.addAll(selection)
        notifyDataSetChanged()
    }

    fun currentItems(): List<AlarmListItem> = items.toList()

    class AlarmViewHolder(private val binding: ItemAlarmEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: AlarmListItem,
            selectionMode: Boolean,
            selectedIds: Set<String>,
            onLongPress: (AlarmListItem) -> Unit,
            onItemSelected: (AlarmListItem) -> Unit,
        ) {
            binding.alarmPatientName.text = item.patientName
            binding.alarmMedicationInfo.text = binding.root.context.getString(
                R.string.alarm_list_medication_format,
                item.medicationName,
                item.scheduleText
            )

            binding.alarmSelectionCheckBox.isVisible = selectionMode
            binding.alarmSelectionCheckBox.isChecked = selectedIds.contains(item.id)

            binding.root.setOnLongClickListener {
                onLongPress(item)
                true
            }

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onItemSelected(item)
                }
            }
        }
    }
}

internal class MedicationListAdapter(
    private val onLongPress: (MedicationListItem) -> Unit,
    private val onItemSelected: (MedicationListItem) -> Unit,
) : RecyclerView.Adapter<MedicationListAdapter.MedicationViewHolder>() {

    private val items = mutableListOf<MedicationListItem>()
    private val selectedIds = mutableSetOf<String>()
    private var selectionMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationViewHolder {
        val binding = ItemMedicationEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicationViewHolder, position: Int) {
        holder.bind(items[position], selectionMode, selectedIds, onLongPress, onItemSelected)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<MedicationListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(selection: Set<String>, selectionMode: Boolean) {
        this.selectionMode = selectionMode
        selectedIds.clear()
        selectedIds.addAll(selection)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeItems(predicate: (MedicationListItem) -> Boolean) {
        items.removeAll(predicate)
        notifyDataSetChanged()
    }

    fun currentItems(): List<MedicationListItem> = items.toList()

    class MedicationViewHolder(private val binding: ItemMedicationEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: MedicationListItem,
            selectionMode: Boolean,
            selectedIds: Set<String>,
            onLongPress: (MedicationListItem) -> Unit,
            onItemSelected: (MedicationListItem) -> Unit,
        ) {
            binding.medicationName.text = item.medication.name
            binding.medicationDetail.text = listOfNotNull(
                buildDosageLabel(item.medication),
                item.medication.route?.takeIf { it.isNotBlank() },
                item.medication.composition?.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
                .ifBlank {
                    binding.root.context.getString(R.string.medication_detail_placeholder)
                }

            binding.medicationSelectionCheckBox.isVisible = selectionMode
            binding.medicationSelectionCheckBox.isChecked = selectedIds.contains(item.id)

            binding.root.setOnLongClickListener {
                onLongPress(item)
                true
            }

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onItemSelected(item)
                }
            }
        }

        private fun buildDosageLabel(item: MedicationSearchItem): String? {
            val value = item.dosageValue?.takeIf { it.isNotBlank() }
            val unit = item.dosageUnit?.takeIf { it.isNotBlank() }
            return if (value != null && unit != null) "$value $unit" else null
        }
    }
}

internal class PatientListAdapter(
    private val onLongPress: (PatientListItem) -> Unit,
    private val onItemSelected: (PatientListItem) -> Unit,
) : RecyclerView.Adapter<PatientListAdapter.PatientViewHolder>() {

    private val items = mutableListOf<PatientListItem>()
    private val selectedIds = mutableSetOf<String>()
    private var selectionMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemPatientEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PatientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(items[position], selectionMode, selectedIds, onLongPress, onItemSelected)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<PatientListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(selection: Set<String>, selectionMode: Boolean) {
        this.selectionMode = selectionMode
        selectedIds.clear()
        selectedIds.addAll(selection)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeItems(predicate: (PatientListItem) -> Boolean) {
        items.removeAll(predicate)
        notifyDataSetChanged()
    }

    fun currentItems(): List<PatientListItem> = items.toList()

    class PatientViewHolder(private val binding: ItemPatientEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: PatientListItem,
            selectionMode: Boolean,
            selectedIds: Set<String>,
            onLongPress: (PatientListItem) -> Unit,
            onItemSelected: (PatientListItem) -> Unit,
        ) {
            binding.patientName.text = item.patient.name
            binding.patientSelectionCheckBox.isVisible = selectionMode
            binding.patientSelectionCheckBox.isChecked = selectedIds.contains(item.patient.id)

            val avatarBackground = MaterialColors.getColor(
                binding.patientAvatar,
                com.google.android.material.R.attr.colorSecondaryContainer
            )
            val avatarTextColor = MaterialColors.getColor(
                binding.patientAvatar,
                com.google.android.material.R.attr.colorOnSecondaryContainer
            )
            binding.patientAvatar.setCardBackgroundColor(avatarBackground)
            binding.patientAvatarInitial.setTextColor(avatarTextColor)
            binding.patientAvatarInitial.text = item.patient.name
                .takeIf { it.isNotBlank() }
                ?.trim()
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"

            binding.root.setOnLongClickListener {
                onLongPress(item)
                true
            }

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onItemSelected(item)
                }
            }
        }
    }
}
