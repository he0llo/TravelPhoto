package he.travelphoto.llo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import he.travelphoto.llo.data.AlbumRepository
import he.travelphoto.llo.ui.TravelPhotoApp
import he.travelphoto.llo.ui.theme.TravelPhotoTheme
import he.travelphoto.llo.ui.theme.rememberThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AlbumRepository(applicationContext)
        setContent {
            val themeState = rememberThemeState()
            TravelPhotoTheme(themeState = themeState) {
                TravelPhotoApp(repository = repository, themeState = themeState)
            }
        }
    }
}
