package com.example.bookfinder

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bookfinder.ui.theme.BookFinderTheme
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.squareup.moshi.Json
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookFinderTheme {
                Surface(){
                    BookFinderApp()
                }
            }
        }
    }
}


class BookViewModel : ViewModel() {
    private val repository = BookRepository()

    var searchQuery by mutableStateOf(TextFieldValue(""))
    var books by mutableStateOf<List<BookItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedBookId by mutableStateOf<String?>(null)

    fun performSearch() {
        if (searchQuery.text.isNotEmpty()) {
            isLoading = true
            error = null
            viewModelScope.launch {
                repository.searchBooks(searchQuery.text)
                    .catch { e ->
                        error = e.message ?: "Unknown error"
                        isLoading = false
                    }
                    .collect { fetchedBooks ->
                        books = fetchedBooks
                        isLoading = false
                    }
            }
        }
    }

    // Function to get a book by ID for the details screen
    fun getBookById(id: String): BookItem? {
        return books.find { it.id == id }
    }
}

@Composable
fun BookFinderApp(viewModel: BookViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Set up navigation between the book list and the details screen
    if (isLandscape) {
        BookListWithDetailsScreen(viewModel = viewModel)
    } else {
        NavHost(navController = navController, startDestination = "bookList") {
            composable("bookList") {
                BookListScreen(viewModel, onBookClick = { bookId ->
                    viewModel.selectedBookId = bookId
                    navController.navigate("bookDetails/$bookId")
                })
            }
            composable(
                "bookDetails/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId")
                if (bookId != null) {
                    BookDetailsScreen(bookId = bookId, viewModel = viewModel, navController = navController)
                }
            }
        }
    }
}

@Composable
fun BookListScreen(
    viewModel: BookViewModel,
    onBookClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.searchQuery = it },
                onSearch = { viewModel.performSearch() },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { viewModel.performSearch() }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            viewModel.error != null -> Text("Error: ${viewModel.error}", color = MaterialTheme.colorScheme.error)
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.books) { book ->
                        BookItem(book = book, onClick = { onBookClick(book.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun BookListWithDetailsScreen(viewModel: BookViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(0.4f).padding(8.dp)) {
            // Book List (on the left)
            BookListScreen(viewModel, onBookClick = { bookId ->
                viewModel.selectedBookId = bookId
            })
        }
        Column(modifier = Modifier.weight(0.6f).padding(8.dp)) {
            // Book Details (on the right)
            viewModel.selectedBookId?.let { bookId ->
                BookDetailsScreen(bookId = bookId, viewModel = viewModel, navController = null)  // No navigation needed in landscape mode
            } ?: run {
                Text("Select a book to view details", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun SearchBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search for books...") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardOptions.Default.keyboardType,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                onSearch()
            }
        ),
        modifier = modifier,
        singleLine = true
    )
}

@Composable
fun BookItem(book: BookItem, onClick: () -> Unit) {
    val imageUrl = book.volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Book Cover",
                modifier = Modifier.size(64.dp).padding(end = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("No Image", color = Color.White)
            }
        }

        Column {
            Text(text = book.volumeInfo.title, style = MaterialTheme.typography.bodyLarge)
            Text(text = book.volumeInfo.authors?.joinToString(", ") ?: "Unknown Author", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(bookId: String, viewModel: BookViewModel, navController: NavController?) {
    val book = viewModel.getBookById(bookId)

    if (book != null) {
        Scaffold(
            topBar = {
                if (navController != null) {
                    TopAppBar(
                        title = { Text("Book Details") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->  // Add paddingValues to the Scaffold content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(text = "Title: ${book.volumeInfo.title}", style = MaterialTheme.typography.titleLarge)
                Text(text = "Authors: ${book.volumeInfo.authors?.joinToString(", ") ?: "Unknown Author"}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Book ID: ${book.id}", style = MaterialTheme.typography.bodyMedium)

                if (book.volumeInfo.imageLinks?.thumbnail != null) {
                    AsyncImage(
                        model = book.volumeInfo.imageLinks.thumbnail.replace("http://", "https://"),
                        contentDescription = "Book Cover",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image", color = Color.White)
                    }
                }
            }
        }
    } else {
        Text("Book not found", color = Color.Red, modifier = Modifier.padding(16.dp))
    }
}

interface GoogleBooksApi {
    @GET("volumes")
    suspend fun searchBooks(@Query("q") query: String): BookResponse
}

object RetrofitInstance {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())  // Ensure Kotlin support
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: GoogleBooksApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/books/v1/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
            .create(GoogleBooksApi::class.java)
    }
}

class BookRepository {
    private val api = RetrofitInstance.api

    fun searchBooks(query: String) = flow {
        val response = api.searchBooks(query)
        emit(response.items ?: emptyList())  // Handle nullable "items"
    }.catch { e ->
        throw e
    }
}

data class BookResponse(
    @Json(name = "items") val items: List<BookItem>?
)

data class BookItem(
    @Json(name = "id") val id: String,
    @Json(name = "volumeInfo") val volumeInfo: VolumeInfo
)

data class VolumeInfo(
    @Json(name = "title") val title: String,
    @Json(name = "authors") val authors: List<String>?,
    @Json(name = "imageLinks") val imageLinks: ImageLinks?
)

data class ImageLinks(
    @Json(name = "thumbnail") val thumbnail: String?
)

@Preview(showBackground = true)
@Composable
fun BookFinderAppPreview() {
    BookFinderApp()
}