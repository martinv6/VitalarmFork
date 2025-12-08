package com.example.vitalarmapp.utils.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseOptions.Builder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class FirebaseManagerInstrumentedTest {

    private lateinit var host: String
    private var port: Int = 8080

    @Before
    fun setUpEmulatorConnection() {
        val (resolvedHost, resolvedPort) = resolveEmulatorEndpoint()
        host = resolvedHost
        port = resolvedPort

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options: FirebaseOptions = Builder()
                .setApplicationId("1:000000000000:android:firebase-manager-test")
                .setProjectId("demo-test-project")
                .setApiKey("fake-api-key")
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        Firebase.firestore.useEmulator(host, port)
        Firebase.auth.useEmulator(host, 9099)
    }

    @Test
    fun writeAndReadFromFirestoreEmulator() = runBlocking {
        assumeTrue("Firestore emulator unavailable", canReachEmulator(host, port))

        val payload = mapOf("ping" to System.currentTimeMillis())
        val collection = Firebase.firestore.collection("integration_tests")
        val docRef = collection.document("connectivity")

        docRef.set(payload).await()
        val snapshot = docRef.get().await()

        assertTrue(snapshot.exists())
        assertEquals(payload["ping"], snapshot.getLong("ping")?.toLong())
    }

    private fun resolveEmulatorEndpoint(): Pair<String, Int> {
        val envValue = System.getenv("FIRESTORE_EMULATOR_HOST")
        if (!envValue.isNullOrBlank() && envValue.contains(":")) {
            val (envHost, envPort) = envValue.split(":", limit = 2)
            return envHost to envPort.toIntOrNull().takeIf { it != null && it > 0 } ?: 8080
        }
        return "10.0.2.2" to 8080
    }

    private fun canReachEmulator(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 500)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
