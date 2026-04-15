package com.example.brusselscollectiondemo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.brusselscollectiondemo.ui.CollectionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CollectionApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionApp(viewModel: CollectionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var street by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("1000") }
    var municipality by remember { mutableStateOf("Bruxelles") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Collectes Bruxelles") })
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("Recherche par adresse")

            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("Rue") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Numéro") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = postalCode,
                onValueChange = { postalCode = it },
                label = { Text("Code postal") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = municipality,
                onValueChange = { municipality = it },
                label = { Text("Commune") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.search(
                        street,
                        number,
                        postalCode,
                        municipality
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rechercher")
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }

                uiState.error != null -> {
                    Card {
                        Text(
                            text = uiState.error!!,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                uiState.schedule != null -> {

                    val rawUrl = uiState.schedule!!.calendarImageUrl
                    val cleanUrl = rawUrl?.replace("\\/", "/")

                    Text("Calendrier trouvé")

                    cleanUrl?.let { url ->

                        // DEBUG
                        Text(url)

                        Spacer(modifier = Modifier.height(8.dp))

                        AsyncImage(
    model = url,
    contentDescription = "Calendrier",
    modifier = Modifier.fillMaxWidth(),
    onLoading = {
        Log.d("IMAGE", "Chargement...")
    },
    onSuccess = {
        Log.d("IMAGE", "Image OK")
    },
    onError = {
        Log.e("IMAGE", "Erreur chargement image")
    }
)
                    }

                    Text("Résultats")

                    LazyColumn {
                        items(uiState.schedule!!.events.take(20)) { event ->
                            Card {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(event.date.toString())
                                    Text(event.wasteType.name)
                                    Text(event.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
