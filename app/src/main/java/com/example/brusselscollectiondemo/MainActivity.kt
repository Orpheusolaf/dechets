package com.example.brusselscollectiondemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brusselscollectiondemo.ui.CollectionViewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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
            Text(
                text = "Recherche par adresse",
                style = MaterialTheme.typography.titleMedium
            )

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
                        street = street,
                        number = number,
                        postalCode = postalCode,
                        municipality = municipality
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = street.isNotBlank() && number.isNotBlank()
            ) {
                Text("Rechercher")
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }

                uiState.error != null -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = uiState.error ?: "",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                uiState.schedule != null -> {
                    uiState.schedule?.calendarImageUrl?.let { url ->
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Calendrier trouvé")
import coil.compose.AsyncImage

AsyncImage(
    model = url,
    contentDescription = "Calendrier",
    modifier = Modifier.fillMaxWidth()
)
        }
    }
}

uiState.schedule?.calendarImageUrl?.let { url ->
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Calendrier trouvé")
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = url,
                contentDescription = "Calendrier de collecte",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
                    Text(
                        text = "Résultats",
                        style = MaterialTheme.typography.titleMedium
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.schedule!!.events.take(20)) { event ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = event.date.toString(),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(text = event.wasteType.name)
                                    Text(text = event.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
