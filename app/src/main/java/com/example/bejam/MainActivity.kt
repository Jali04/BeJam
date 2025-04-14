package com.example.bejam

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.bejam.auth.SpotifyAuthManager
import com.example.bejam.auth.SpotifyAuthManager.Companion.refreshAccessToken
import com.example.bejam.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_friends, R.id.navigation_profile

            )
        )

        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        val expirationTime = prefs.getLong("expiration_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (expirationTime == 0L || currentTime >= expirationTime) {
            // Token is missing or expired — attempt refresh
            refreshAccessToken(this) { success ->
                if (success) {
                    // Token refreshed, update UI or proceed as logged in
                    Log.d("MAIN", "Token refreshed successfully.")
                } else {
                    // Refresh failed: possibly prompt user to log in again.
                    Log.d("MAIN", "Token refresh failed; user may need to log in again.")
                }
            }
        } else {
            // Token is still valid; no need to refresh.
            Log.d("MAIN", "Access token is still valid.")
        }


        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}