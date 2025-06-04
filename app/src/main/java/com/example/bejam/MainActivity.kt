package com.example.bejam

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.bejam.auth.SpotifyAuthManager
import com.example.bejam.auth.SpotifyAuthManager.Companion.refreshAccessToken
import com.example.bejam.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Die Haupt-Activity, die das BottomNavigationView-Layout (Home, Friends, Profile) einrichtet.
 * Außerdem prüft sie beim Start, ob ein Firebase-User angemeldet ist, und falls nicht,
 * leitet direkt zur Profile-Route (Login) weiter. Sie kümmert sich außerdem um das
 * automatische Refreshen des Spotify-Zugriffstokens und ggf. um die
 * POST_NOTIFICATIONS-Permission unter Android 13+.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Firebase-Anonym-SignIn prüfen
        val auth = FirebaseAuth.getInstance()
        // Binding initialisieren und Layout setzen
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Wenn kein Firebase-User angemeldet, direkt zur Profile-Route navigieren (Login)
        if (auth.currentUser == null) {
            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            navController.navigate(R.id.navigation_profile)
        }

        // BottomNavigationView & NavController konfigurieren
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_friends, R.id.navigation_profile
            )
        )

        // Spotify Access-Token Ablauf prüfen
        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        val expirationTime = prefs.getLong("expiration_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (expirationTime == 0L || currentTime >= expirationTime) {
            // → Token fehlt oder abgelaufen → Refresh anstoßen
            refreshAccessToken(this) { success, errorMessage ->
                if (success) {
                    Log.d("MAIN", "Token refreshed successfully.")
                } else {
                    Log.d("MAIN", "Token refresh failed: $errorMessage")
                }
            }
        } else {
            Log.d("MAIN", "Access token is still valid.")
        }

        // Unter Android 13+ Notifications-Permission anfragen, wenn noch nicht erteilt
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(POST_NOTIFICATIONS),
                1234
            )
        }

        // ActionBar und BottomNav mit NavController verknüpfen
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    /**
     * Falls die Up-Navigation gedrückt wurde, verarbeiten wir sie hier.
     */
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

}