package com.example.vitalarmapp.models

data class Medication(
    val id: String = "",
    val personId: String = "",
    val userId: String = "",
    val name: String = "",
    val dosage: String = "",
    val frequency: String = "",
    val alarmTimes: List<String> = emptyList(),
    val createdAt: Long = 0
)