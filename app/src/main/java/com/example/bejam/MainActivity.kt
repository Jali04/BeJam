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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Ensure we have a Firebase user (anonymous if nobody’s signed in)
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    Log.d("AUTH", "Signed in anonymously as ${result.user!!.uid}")
                    // now you're free to talk to Firestore / FriendsViewModel safely
                }
                .addOnFailureListener { e ->
                    Log.e("AUTH", "Anonymous sign-in failed", e)
                    Toast.makeText(this, "Auth error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

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

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(POST_NOTIFICATIONS),
                1234
            )
        }


        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

}