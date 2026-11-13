package com.ganus.mobile

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ganus.mobile.core.local.RepositorioLocalGanus
import com.ganus.mobile.core.network.AuthenticatedSession
import com.ganus.mobile.core.network.GanusApi
import com.ganus.mobile.core.network.LoginRequest
import com.ganus.mobile.core.network.PersistentAccessTokenProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var ganusApi: GanusApi

    @Inject
    lateinit var accessTokenProvider: PersistentAccessTokenProvider

    @Inject
    lateinit var repositorioLocal: RepositorioLocalGanus

    private var isPasswordVisible = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) GanusApplication.permitirCaptura(this)
    }

    override fun onResume() {
        super.onResume()
        GanusApplication.permitirCaptura(this)
        accessTokenProvider.getSession()?.let(::abrirJornada)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GanusApplication.permitirCaptura(this)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = getColor(R.color.screen_background)
        window.navigationBarColor = getColor(R.color.screen_background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_main)
        GanusApplication.permitirCaptura(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_scroll)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom,
            )
            insets
        }

        val passwordInput = findViewById<EditText>(R.id.password_input)
        val passwordVisibilityButton = findViewById<ImageButton>(R.id.password_visibility_button)
        passwordVisibilityButton.setOnClickListener {
            val selection = passwordInput.selectionStart
            isPasswordVisible = !isPasswordVisible
            passwordInput.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordInput.setSelection(selection.coerceIn(0, passwordInput.length()))
            passwordVisibilityButton.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_off,
            )
            passwordVisibilityButton.contentDescription = getString(
                if (isPasswordVisible) R.string.hide_password else R.string.show_password,
            )
        }

        val loginButton = findViewById<Button>(R.id.login_button)
        loginButton.setOnClickListener {
            val emailInput = findViewById<EditText>(R.id.email_input)
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = getString(R.string.invalid_email)
                emailInput.requestFocus()
            } else if (password.isBlank()) {
                passwordInput.error = getString(R.string.empty_password)
                passwordInput.requestFocus()
            } else {
                loginButton.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val session = ganusApi.login(LoginRequest(email, password))
                        val authenticatedSession = AuthenticatedSession(
                            accessToken = session.accessToken,
                            userId = session.usuario.identificador,
                            userName = session.usuario.nombre,
                        )
                        accessTokenProvider.saveSession(authenticatedSession)
                        val syncError = try {
                            repositorioLocal.guardarTareasRemotas(ganusApi.getTasks().data)
                            repositorioLocal.guardarVacasRemotas(ganusApi.getCows().data)
                            null
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            exception
                        }
                        abrirJornada(authenticatedSession)
                        syncError?.let {
                            Toast.makeText(
                                this@MainActivity,
                                "Sesión iniciada, pero no se pudieron actualizar los datos: ${it.localizedMessage ?: "error de conexión"}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        val message = when (exception) {
                            is retrofit2.HttpException -> when (exception.code()) {
                                401 -> "Correo o contraseña incorrectos."
                                else -> "El servicio respondió con un error (${exception.code()})."
                            }
                            else -> "No se pudo iniciar sesión: ${exception.localizedMessage ?: "verifica tu conexión"}"
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    } finally {
                        loginButton.isEnabled = true
                    }
                }

            }
        }

        findViewById<Button>(R.id.preview_button).setOnClickListener {
            val email = findViewById<EditText>(R.id.email_input).text.toString().trim()
            val currentUser = email.takeIf { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
                ?: "Usuario de demostración"
            startActivity(
                Intent(this, JourneyActivity::class.java)
                .putExtra(JourneyActivity.EXTRA_CURRENT_USER, currentUser)
                .putExtra(JourneyActivity.EXTRA_AUTHENTICATED, false),
            )
        }
    }

    private fun abrirJornada(session: AuthenticatedSession) {
        startActivity(
            Intent(this, JourneyActivity::class.java)
                .putExtra(JourneyActivity.EXTRA_CURRENT_USER, session.userName)
                .putExtra(JourneyActivity.EXTRA_CURRENT_USER_ID, session.userId)
                .putExtra(JourneyActivity.EXTRA_AUTHENTICATED, true),
        )
    }
}
