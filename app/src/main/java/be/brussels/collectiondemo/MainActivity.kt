package be.brussels.collectiondemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.brussels.collectiondemo.ui.theme.BrusselsCollectionDemoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrusselsCollectionDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DemoApp()
                }
            }
        }
    }
}

private enum class WasteType(val label: String) {
    WHITE("Sacs blancs"),
    BLUE("Sacs bleus"),
    YELLOW("Sacs jaunes"),
    ORGANIC("Déchets organiques"),
    BULKY("Encombrants")
}

private data class CollectionEvent(
    val wasteType: WasteType,
    val date: LocalDate,
    val notes: String
)

private data class DemoLocation(
    val neighborhood: String,
    val address: String,
    val events: List<CollectionEvent>
)

private val demoLocations = listOf(
    DemoLocation(
        neighborhood = "Châtelain",
        address = "Rue du Bailli 1, 1050 Ixelles",
        events = listOf(
            CollectionEvent(WasteType.WHITE, LocalDate.now().plusDays(1), "Sortir avant 18h"),
            CollectionEvent(WasteType.BLUE, LocalDate.now().plusDays(2), "PMC"),
            CollectionEvent(WasteType.YELLOW, LocalDate.now().plusDays(4), "Papiers-cartons"),
            CollectionEvent(WasteType.ORGANIC, LocalDate.now().plusDays(5), "Bac organique")
        )
    ),
    DemoLocation(
        neighborhood = "Jourdan",
        address = "Place Jourdan 1, 1040 Etterbeek",
        events = listOf(
            CollectionEvent(WasteType.WHITE, LocalDate.now().plusDays(1), "Sortir avant 18h"),
            CollectionEvent(WasteType.BLUE, LocalDate.now().plusDays(3), "PMC"),
            CollectionEvent(WasteType.BULKY, LocalDate.now().plusDays(8), "Sur réservation")
        )
    ),
    DemoLocation(
        neighborhood = "Sablon",
        address = "Rue de la Régence 3, 1000 Bruxelles",
        events = listOf(
            CollectionEvent(WasteType.WHITE, LocalDate.now().plusDays(2), "Sortir avant 18h"),
            CollectionEvent(WasteType.YELLOW, LocalDate.now().plusDays(3), "Papiers-cartons"),
            CollectionEvent(WasteType.ORGANIC, LocalDate.now().plusDays(6), "Bac organique")
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoApp() {
    var selectedNeighborhood by remember { mutableStateOf(demoLocations.first().neighborhood) }
    var addressQuery by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }

    val selected = demoLocations.firstOrNull { it.neighborhood == selectedNeighborhood }
    val addressResult = demoLocations.firstOrNull {
        addressQuery.isNotBlank() && it.address.contains(addressQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Collectes Bruxelles") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Démo rapide du prototype Android.",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choisis un quartier ou cherche une adresse pour voir des passages simulés.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RowTitle(icon = Icons.Outlined.Home, title = "Choisir un quartier")
                        demoLocations.forEach { location ->
                            Button(
                                onClick = { selectedNeighborhood = location.neighborhood },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(location.neighborhood)
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RowTitle(icon = Icons.Outlined.Search, title = "Recherche par adresse")
                        OutlinedTextField(
                            value = addressQuery,
                            onValueChange = { addressQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Rue ou adresse") },
                            singleLine = true
                        )
                        Text(
                            text = if (addressQuery.isBlank()) {
                                "Exemple : Bailli, Jourdan, Régence"
                            } else {
                                addressResult?.let { "Correspondance: ${it.address}" }
                                    ?: "Aucune adresse de démonstration trouvée"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                val location = addressResult ?: selected
                location?.let {
                    ResultCard(
                        location = it,
                        favorite = favorite,
                        onRefresh = { /* demo only */ },
                        onToggleFavorite = { favorite = !favorite }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    location: DemoLocation,
    favorite: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RowTitle(icon = Icons.Outlined.LocationOn, title = "Résultat")
            Text(location.neighborhood, style = MaterialTheme.typography.titleLarge)
            Text(location.address, style = MaterialTheme.typography.bodyMedium)

            AssistChip(
                onClick = onToggleFavorite,
                label = { Text(if (favorite) "Favori ajouté" else "Ajouter aux favoris") },
                leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) }
            )

            AssistChip(
                onClick = onRefresh,
                label = { Text("Actualiser") },
                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) }
            )

            Divider()

            location.events.sortedBy { it.date }.forEach { event ->
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(event.wasteType.label, style = MaterialTheme.typography.titleMedium)
                        Text("Passage: ${event.date.format(formatter)}")
                        Text(event.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
