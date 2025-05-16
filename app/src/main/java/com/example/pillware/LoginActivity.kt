package com.example.pillware

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private lateinit var callbackManager: CallbackManager
    private lateinit var buttonFacebook : ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FacebookSdk.sdkInitialize(applicationContext)
        setContentView(R.layout.activity_login)

        com.example.pillware.util.Util.printFacebookKeyHash(this)

        auth = FirebaseAuth.getInstance()
        callbackManager = CallbackManager.Factory.create()

        //Google Sign In config
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.client_web)) // Usando tu nombre de recurso: client_web
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val editTextUsername = findViewById<EditText>(R.id.usuario)
        val editTextPassword = findViewById<EditText>(R.id.pass)
        val buttonLogin = findViewById<Button>(R.id.aceptar)
        val textViewTitulo = findViewById<TextView>(R.id.titulo)
        val buttonregister = findViewById<TextView>(R.id.registrarse)
        val buttongoogle = findViewById<ImageView>(R.id.imageView2)
        val buttonfacebook = findViewById<ImageView>(R.id.imageView3)

        buttonregister.setOnClickListener {
            val intento = Intent(this, RegisterActivity::class.java)
            startActivity(intento)
        }

        buttonLogin.setOnClickListener {
            val email = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                Log.d("Contrasena", password)
                signInWithEmail(email, password)
            } else {
                textViewTitulo.text = "Por favor, ingrese sus credenciales."
            }
        }

        buttongoogle.setOnClickListener {
            signInWithGoogle()
        }

        buttonfacebook.setOnClickListener {
            startFacebookLogin()
        }

        // Registra el Callback para el resultado del login de Facebook
        LoginManager.getInstance().registerCallback(callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(loginResult: LoginResult) {
                    // App code
                    Log.d("FacebookLogin", "Login con Facebook exitoso con token: ${loginResult.accessToken.token}")
                    handleFacebookAccessToken(loginResult.accessToken.token)
                }

                override fun onCancel() {
                    // App code
                    Log.d("FacebookLogin", "Login con Facebook cancelado.")
                    Toast.makeText(baseContext, "Inicio de sesión con Facebook cancelado.", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: FacebookException) {
                    // App code
                    Log.e("FacebookLogin", "Error en el login con Facebook: ${exception.message}")
                    Toast.makeText(baseContext, "Error al iniciar sesión con Facebook.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun signInWithEmail(email: String, password: String) {
        Log.d("Inicio signin", "Inicio del signin exitoso")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithEmail:success")
                    val user = auth.currentUser
                    navigateToMainActivity(user?.email)
                } else {
                    Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext,
                        "Usuario o contraseña incorrectos.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }


    private fun startFacebookLogin() {
        LoginManager.getInstance().logInWithReadPermissions(this, listOf("email", "public_profile"))
    }

    private fun handleFacebookAccessToken(token: String) {
        val credential = FacebookAuthProvider.getCredential(token)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    Log.d("LoginActivity", "firebaseAuthWithFacebook:success")
                    navigateToMainActivity(user?.email)
                } else {
                    Log.w("LoginActivity", "firebaseAuthWithFacebook:failure", task.exception)
                    Toast.makeText(baseContext, "Error de autenticación con Facebook.", Toast.LENGTH_SHORT).show()
                }
            }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //Resultado a CallbackManager para hacer el login con facebook
        callbackManager.onActivityResult(requestCode, resultCode, data)

        //Resultado para el login de google
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account?.idToken)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w("LoginActivity", "Google sign in failed", e)
                Toast.makeText(baseContext, "Error al iniciar sesión con Google.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String?) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    Log.d("LoginActivity", "firebaseAuthWithGoogle:success")
                    navigateToMainActivity(user?.email)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("LoginActivity", "firebaseAuthWithGoogle:failure", task.exception)
                    Toast.makeText(baseContext, "Error de autenticación con Google.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMainActivity(email: String?) {
        val intento = Intent(this, MainActivity::class.java)
        intento.putExtra("EXTRA_TEXTO", email)
        startActivity(intento)
        finish() // Termina la actividad de login
    }

    private fun reload() {
        // Puedes agregar lógica para recargar la interfaz de usuario si es necesario
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            //Usuario autenticado
            navigateToMainActivity(currentUser.email)
        }
    }
}