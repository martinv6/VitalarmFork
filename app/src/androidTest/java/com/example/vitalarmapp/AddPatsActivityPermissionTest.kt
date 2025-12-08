package com.example.vitalarmapp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vitalarmapp.utils.firebase.AddPersonResult
import com.example.vitalarmapp.utils.firebase.FirebasePatientRegistrar
import com.example.vitalarmapp.utils.firebase.PatientRegistrar
import com.google.android.material.textfield.TextInputEditText
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddPatsActivityPermissionTest {

    private val fakeRegistrar = RecordingPatientRegistrar()

    @Before
    fun setup() {
        AddPatsActivity.patientRegistrar = fakeRegistrar
    }

    @After
    fun tearDown() {
        AddPatsActivity.patientRegistrar = FirebasePatientRegistrar
    }

    @Test
    fun showsPermissionDeniedSnackbarWithDetail() {
        fakeRegistrar.currentUserId = "tester"
        fakeRegistrar.resultToReturn = AddPersonResult.PermissionDenied("Firestore rules blocked access")

        val scenario = ActivityScenario.launch(AddPatsActivity::class.java)

        scenario.onActivity { activity ->
            activity.findViewById<TextInputEditText>(R.id.patient_name_tf)
                .setText("Paciente Demo")
            activity.findViewById<TextInputEditText>(R.id.patient_gender_tf)
                .setText("Masculino")
            activity.findViewById<TextInputEditText>(R.id.patient_birth_date_tf)
                .setText("01/01/2000")
            activity.findViewById<TextInputEditText>(R.id.patient_notes_tf)
                .setText("Detalle clínico")
        }

        onView(withId(R.id.patient_continue_btn)).perform(click())

        onView(withText(containsString("Firestore rules blocked access")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun forwardsPatientDataToRegistrar() {
        fakeRegistrar.currentUserId = "tester"
        fakeRegistrar.resultToReturn = AddPersonResult.PermissionDenied("")

        val scenario = ActivityScenario.launch(AddPatsActivity::class.java)

        scenario.onActivity { activity ->
            activity.findViewById<TextInputEditText>(R.id.patient_name_tf)
                .setText("Paciente Demo")
            activity.findViewById<TextInputEditText>(R.id.patient_gender_tf)
                .setText("Femenino")
            activity.findViewById<TextInputEditText>(R.id.patient_birth_date_tf)
                .setText("02/02/2002")
            activity.findViewById<TextInputEditText>(R.id.patient_notes_tf)
                .setText("Notas")
        }

        onView(withId(R.id.patient_continue_btn)).perform(click())

        val captured = fakeRegistrar.lastPayload
        onView(withText(containsString("No tienes permiso"))).check(matches(isDisplayed()))
        assertNotNull(captured)
        assertEquals("Paciente Demo", captured?.name)
        assertEquals("Femenino", captured?.gender)
        assertEquals("02/02/2002", captured?.birthDate)
        assertEquals("Notas", captured?.notes)
    }

    private class RecordingPatientRegistrar : PatientRegistrar {
        var currentUserId: String? = null
        var resultToReturn: AddPersonResult = AddPersonResult.Success
        var lastPayload: Payload? = null

        override fun getCurrentUserId(): String? = currentUserId

        override suspend fun addPerson(
            name: String,
            birthDate: String,
            gender: String,
            notes: String
        ): AddPersonResult {
            lastPayload = Payload(name, birthDate, gender, notes)
            return resultToReturn
        }
    }

    private data class Payload(
        val name: String,
        val birthDate: String,
        val gender: String,
        val notes: String,
    )
}
