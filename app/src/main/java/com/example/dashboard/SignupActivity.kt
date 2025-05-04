package com.example.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dashboard.databinding.ActivitySignupBinding // Change this
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding // Change this
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = ActivitySignupBinding.inflate(layoutInflater) // Change this
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Use the correct ID from activity_signup.xml
        binding.loginPromptTextView.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // Use the correct ID from activity_signup.xml
        binding.signupButton.setOnClickListener {
            // Use the correct IDs from activity_signup.xml
            val email = binding.emailEditText.text.toString()
            val pass = binding.passwordEditText.text.toString()
            // There's no confirm password field in your provided signup XML,
            // so you might need to add one or adjust the logic.
            // For now, I'll remove the confirmPass part as it's not in the layout.
            // If you add a confirm password field, make sure to give it an ID
            // and access it correctly here.

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                // Assuming you want to create a user with email and password
                firebaseAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, it.exception.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Empty Fields Are not Allowed !!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
