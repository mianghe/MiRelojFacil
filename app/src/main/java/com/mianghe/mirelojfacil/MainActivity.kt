package com.mianghe.mirelojfacil

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.icu.util.Calendar
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mianghe.mirelojfacil.network.fetchActividadesFromApi
import com.mianghe.mirelojfacil.workers.MedioPlazoWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

// NUEVAS IMPORTACIONES PARA ROOM Y RECYCLERVIEW
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mianghe.mirelojfacil.adapters.ActividadAdapter
import com.mianghe.mirelojfacil.database.AppDatabase
import com.mianghe.mirelojfacil.database.ActividadEntity
//import com.mianghe.mirelojfacil.funcionesauxiliares.loadActividadesFromDatabase

//singleton para DataStore
val Context.dataStore by preferencesDataStore(name = "USER_PREFERENCES")

// Claves para DataStore
val PREF_24H_FORMATO = booleanPreferencesKey("sw24h")
val PREF_BARRAS_COLORES = booleanPreferencesKey("swbarrascolores")
val PREF_LINEA_MOVIMIENTO = booleanPreferencesKey("swlineamovimiento")
val PREF_EMAIL = stringPreferencesKey("email")
val PREF_PASSWORD = stringPreferencesKey("password")
val PREF_UUID = stringPreferencesKey("app_uuid")


class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var timer: Timer? = null
    var primeraEjecucion = true
    private var appUUID: String = ""

    // Referencias a las vistas del reloj para facilitar el acceso
    private lateinit var textClockHora: TextClock
    private lateinit var colorBarsContainer: LinearLayout
    private lateinit var lineaMovimiento: View

    // NUEVAS PROPIEDADES PARA RECYCLERVIEW
    private lateinit var recyclerViewActividades: RecyclerView
    private lateinit var actividadAdapter: ActividadAdapter
    private lateinit var appDatabase: AppDatabase // Instancia de la base de datos

    fun iniciarTareaPeriodica() {
        val calendar = Calendar.getInstance()
        val currentSecond = calendar.get(Calendar.SECOND)
        val esperaParaSincronizar =
            TimeUnit.SECONDS.toMillis(60 - currentSecond.toLong())
        getCurrentLocation()
        timer = Timer()
        timer?.schedule(
            delay = esperaParaSincronizar, // Ejecutar la primera vez inmediatamente
            period = 60 * 1000 // Ejecutar cada 60 segundos (1 minuto)
        ) {
            //Funciones que se ejecutan periodicamente cada 60 segundos
            getCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation()
        iniciarTareaPeriodica()
        // Cuando la actividad vuelve a estar visible, podemos recargar las actividades de la DB
        /*lifecycleScope.launch {
            loadActividadesFromDatabase(applicationContext, actividadAdapter)
        }*/
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // Configuración del comportamiento del sistema de barras de estado ocultas
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Inicializar referencias a las vistas
        textClockHora = findViewById(R.id.txtHora)
        colorBarsContainer = findViewById(R.id.colorBarsContainer)
        lineaMovimiento = findViewById(R.id.lineaMovimiento)

        // Inicializar Room database
        appDatabase = AppDatabase.getDatabase(applicationContext)

        // Configurar RecyclerView
        recyclerViewActividades = findViewById(R.id.recyclerViewActividades)
        recyclerViewActividades.layoutManager = LinearLayoutManager(this)
        actividadAdapter = ActividadAdapter(emptyList()) // Inicializa con lista vacía
        recyclerViewActividades.adapter = actividadAdapter

        // *** OBSERVAMOS EL FLOW DE LA BASE DE DATOS ROOM ***
        lifecycleScope.launch {
            appDatabase.actividadDao().getAllActividades().collect { actividades ->
                // Este bloque se ejecutará cada vez que los datos en la tabla 'actividades' cambien
                withContext(Dispatchers.Main) {
                    actividadAdapter.updateActividades(actividades)
                    Log.d("MainActivity", "RecyclerView actualizado por Flow de DB: ${actividades.size} actividades.")
                }
            }
        }

        if (primeraEjecucion) {
            primeraEjecucion = false
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
            getCurrentLocation()
            iniciarTareaPeriodica()
            loadPreferences() // Cargar las preferencias al inicio
        }

        planificarTareasMedioPlazo()

        //Obtener el tamaño de la ventana main
        val ventanaPrincipal = findViewById<LinearLayout>(R.id.main_root_layout)
        ventanaPrincipal.post {
            val width = ventanaPrincipal.width
            val height = ventanaPrincipal.height
            val widthDp = width / resources.displayMetrics.density
            val heightDp = height / resources.displayMetrics.density
            Log.d("TAG", "Ancho: $width px ($widthDp dp)")
            Log.d("TAG", "Alto: $height px ($heightDp dp)")
        }

        //Al pulsar en el día de la semana se abre el cuadro de diálogo de configuración
        val mainLayout = findViewById<TextClock>(R.id.txtDia)
        mainLayout.setOnClickListener {
            showConfigDialog()
        }
    } // onCreate

    // Función para cargar actividades de la base de datos y actualizar el RecyclerView
    /*private fun loadActividadesFromDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            val actividades = appDatabase.actividadDao().getAllActividades()
            withContext(Dispatchers.Main) {
                actividadAdapter.updateActividades(actividades)
                Log.d("MainActivity", "Actividades cargadas desde DB: ${actividades.size}")
            }
        }
    }*/

    // Este es el planificador de tareas a Medio plazo (cada 15 minutos)
    private fun planificarTareasMedioPlazo() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // Solo ejecutar si la batería no está baja
            .setRequiredNetworkType(NetworkType.CONNECTED) // Solo ejecutar si hay conexión a internet
            .build()

        val medioPlazoWorkRequest = PeriodicWorkRequestBuilder<MedioPlazoWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "TareasMedioPlazo", // Nombre único para la tarea periódica
                ExistingPeriodicWorkPolicy.KEEP,
                medioPlazoWorkRequest
            )
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                //Manifest.permission.ACCESS_COARSE_LOCATION
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                //arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                Log.d("location", "No se pudo obtener ubicación")
            } else {
                val latitud = location.latitude
                val longitud = location.longitude
                Log.d("probando", "longitud: $longitud")
                actualizarHoras(longitud, latitud)
            }
        }
    }

    fun aplicarColoresHora(colorFondo: Int, colorTexto: Int, textoDia: String) {
        val vcMiInfo = findViewById<LinearLayout>(R.id.main_root_layout)
        //val vcMiInfo = findViewById<ConstraintLayout>(R.id.right_panel)
        val txtDia = findViewById<TextClock>(R.id.txtDia)
        val txtFecha = findViewById<TextClock>(R.id.txtFecha)
        val txtHora = findViewById<TextClock>(R.id.txtHora)
        val tvEstadoDia = findViewById<TextView>(R.id.tvEstadoDia)

        vcMiInfo.setBackgroundColor(colorFondo)
        txtDia.setTextColor(colorTexto)
        txtFecha.setTextColor(colorTexto)
        txtHora.setTextColor(colorTexto)
        tvEstadoDia.setTextColor(colorTexto)
        tvEstadoDia.text = textoDia
    }

    fun actualizarIconoMovimiento(colorImagen: Int, imagenDibujo: Drawable?, horaActual: Double) {
        val movableImage = findViewById<ImageView>(R.id.iconoMovimiento)
        movableImage.setColorFilter(colorImagen, android.graphics.PorterDuff.Mode.SRC_IN)
        movableImage.alpha = 1f
        movableImage.setImageDrawable(imagenDibujo)

        val iconoMovimientoDia = findViewById<FrameLayout>(R.id.iconoMovimientoDia)
        iconoMovimientoDia.post { // Asegurarse de que las dimensiones estén disponibles
            val anchoContenedor = iconoMovimientoDia.width
            val anchoImagen = movableImage.width
            val normalizedPosition = horaActual.toFloat().coerceIn(0f, 1f)
            val newPosition = ((anchoContenedor - anchoImagen) * normalizedPosition).toInt()

            val layoutParams = movableImage.layoutParams as FrameLayout.LayoutParams
            layoutParams.leftMargin = newPosition
            movableImage.layoutParams = layoutParams
        }
    }

    fun actualizarLineaMovimiento(colorLinea: Int) {
        lineaMovimiento.setBackgroundColor(colorLinea)
    }

    fun actualizarHoras(longitud: Double, latitud: Double) {
        val calendar = Calendar.getInstance()
        val anioActual = calendar.get(Calendar.YEAR)
        val mesActual = calendar.get(Calendar.MONTH) + 1
        val diaActual = calendar.get(Calendar.DAY_OF_MONTH)
        val horaActual = calendar.get(Calendar.HOUR_OF_DAY)
        val minutoActual = calendar.get(Calendar.MINUTE)
        val laHora: Double = horaANumero(horaActual, minutoActual)
        val zonaHoraria = Calendar.getInstance().timeZone.id
        val salidaSol = horaSol(anioActual, mesActual, diaActual, longitud, latitud, zonaHoraria, 0)
        val puestaSol = horaSol(anioActual, mesActual, diaActual, longitud, latitud, zonaHoraria, 1)

        val hora1: Double = salidaSol - (0.5 / 24.0) //Le quitamos media hora
        val hora2: Double = salidaSol
        val hora3: Double = 12.0 / 24.0
        val hora4: Double = 15.5 / 24.0
        val hora5: Double = puestaSol - (0.5 / 24.0) //Le quitamos media hora
        val hora6: Double = puestaSol + (0.25 / 24.0) //Le añadimos un cuarto de hora
        val hora7 = 24.0

        //Para la barra de colores
        val linearLayout = findViewById<LinearLayout>(R.id.colorBarsContainer)
        val view1 = linearLayout.getChildAt(0)
        val view2 = linearLayout.getChildAt(1)
        val view3 = linearLayout.getChildAt(2)
        val view4 = linearLayout.getChildAt(3)
        val view5 = linearLayout.getChildAt(4)
        val view6 = linearLayout.getChildAt(5)
        val view7 = linearLayout.getChildAt(6)
        setViewWeight(view1, (hora1.toFloat() * 100))
        setViewWeight(view2, ((hora2.toFloat() - hora1.toFloat()) * 100))
        setViewWeight(view3, ((hora3.toFloat() - hora2.toFloat()) * 100))
        setViewWeight(view4, ((hora4.toFloat() - hora3.toFloat()) * 100))
        setViewWeight(view5, ((hora5.toFloat() - hora4.toFloat()) * 100))
        setViewWeight(view6, ((hora6.toFloat() - hora5.toFloat()) * 100))
        setViewWeight(view7, ((24.0f - hora6.toFloat()) * 100))

        //val horaSalidaSol = numeroAHora(salidaSol)
        //val horaPuestaSol = numeroAHora(puestaSol)

        // Definimos una data class para representar cada rango horario
        data class RangoHorario(
            val inicio: Double,
            val fin: Double,
            val fondoResId: Int,
            val textoResId: Int,
            val stringResId: Int,
            val imagenMovimiento: Int
        )

        // Definimos una lista de rangos horarios
        val rangos = listOf(
            RangoHorario(
                hora1,
                hora2,
                R.color.fondo_amaneciendo,
                R.color.texto_amaneciendo,
                R.string.dia_amaneciendo,
                R.drawable.icono_semisol,
            ),
            RangoHorario(
                hora2,
                hora3,
                R.color.fondo_porlamanana,
                R.color.texto_porlamanana,
                R.string.dia_por_la_manana,
                R.drawable.icono_sol1
            ),
            RangoHorario(
                hora3,
                hora4,
                R.color.fondo_mediodia,
                R.color.texto_mediodia,
                R.string.dia_mediodia,
                R.drawable.icono_dia
            ),
            RangoHorario(
                hora4,
                hora5,
                R.color.fondo_tarde,
                R.color.texto_tarde,
                R.string.dia_por_la_tarde,
                R.drawable.icono_dia
            ),
            RangoHorario(
                hora5,
                hora6,
                R.color.fondo_anocheciendo,
                R.color.texto_anocheciendo,
                R.string.dia_anocheciendo,
                R.drawable.icono_semisol
            ),
            RangoHorario(
                hora6,
                hora7,
                R.color.fondo_noche,
                R.color.texto_noche,
                R.string.dia_por_la_noche,
                R.drawable.icono_noche
            ),
        )

        // Buscamos el primer rango que coincida
        val rango = rangos.firstOrNull {
            it.inicio <= laHora && laHora < it.fin
        } ?: RangoHorario(
            0.0,
            hora1,
            R.color.fondo_madrugada,
            R.color.texto_madrugada,
            R.string.dia_de_madrugada,
            R.drawable.icono_noche
        )

        aplicarColoresHora(
            ContextCompat.getColor(this, rango.fondoResId),
            ContextCompat.getColor(this, rango.textoResId),
            resources.getString(rango.stringResId)
        )
        actualizarLineaMovimiento(ContextCompat.getColor(this, rango.textoResId))

        // Para el icono de movimiento
        actualizarIconoMovimiento(
            ContextCompat.getColor(this, rango.textoResId),
            ContextCompat.getDrawable(this, rango.imagenMovimiento),
            laHora
        )
    }

    fun setViewWeight(view: View, weight: Float) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight
        view.layoutParams = params
    }

    private fun showConfigDialog() {
        // Creamos un LinearLayout para contener todos los elementos del diálogo
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.dialog_padding),
                resources.getDimensionPixelSize(R.dimen.dialog_padding),
                resources.getDimensionPixelSize(R.dimen.dialog_padding),
                resources.getDimensionPixelSize(R.dimen.dialog_padding)
            )
        }

        // TextView para el estado de la batería
        val estadoBateria = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin_bottom)
            }
            text = "Bateria: ${getBatteryPercentage(this@MainActivity)}%"
        }
        dialogLayout.addView(estadoBateria)

        // TextView para el UUID de la aplicación
        val tvUUID = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin_bottom)
            }
            text = "UUID de la aplicación: $appUUID" // Mostrar el UUID cargado
            // NOTA: Para ajustes de estilo del texto:
            // setTextStyle(Typeface.MONOSPACE, Typeface.NORMAL)
            // setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        dialogLayout.addView(tvUUID)

        // Switch para formato de 24h
        val switch24h = Switch(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Formato 24 horas"
        }
        dialogLayout.addView(switch24h)

        // Switch para barra de colores
        val switchBarraColores = Switch(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Mostrar barra de colores"
        }
        dialogLayout.addView(switchBarraColores)

        // Switch para línea de movimiento
        val switchLineaMovimiento = Switch(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Mostrar línea de movimiento"
        }
        dialogLayout.addView(switchLineaMovimiento)

        // EditText para Email
        val editTextEmail = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin_top)
            }
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        dialogLayout.addView(editTextEmail)

        // EditText para Contraseña
        val editTextPassword = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        dialogLayout.addView(editTextPassword)

        // *** Botón "Sincronizar mensajes" ***
        val btnSyncMessages = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL // Centrar el botón
                topMargin = resources.getDimensionPixelSize(R.dimen.dialog_margin_top)
            }
            text = "Sincronizar mensajes"
            setOnClickListener {
                // Al pulsar, obtenemos las credenciales y el UUID para llamar a la API directamente
                lifecycleScope.launch(Dispatchers.IO) {
                    val preferences = dataStore.data.first()
                    val currentUuid = preferences[PREF_UUID]
                    val currentEmail = preferences[PREF_EMAIL]
                    val currentPassword = preferences[PREF_PASSWORD]

                    if (currentUuid.isNullOrEmpty() || currentEmail.isNullOrEmpty() || currentPassword.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error: UUID, email o contraseña no configurados.", Toast.LENGTH_LONG).show()
                        }
                        Log.w("MainActivity", "No se pudo sincronizar: UUID, email o contraseña faltan.")
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Iniciando sincronización...", Toast.LENGTH_SHORT).show()
                    }

                    //Llama a la API y guarda la información en ROOM
                    val actividades = fetchActividadesFromApi(applicationContext, currentUuid, currentEmail, currentPassword) //

                    withContext(Dispatchers.Main) {
                        if (actividades != null) {
                            Toast.makeText(this@MainActivity, "Sincronización completada. ${actividades.size} mensajes recibidos.", Toast.LENGTH_LONG).show()
                            Log.d("MainActivity", "Actividades sincronizadas exitosamente: $actividades")
                            //PALAUI
                            // Aquí obtener los datos de la DB para mostrar
                            // Por ejemplo: val actividadesDB = AppDatabase.getDatabase(applicationContext).actividadDao().getAllActividades()
                            // y actualizar UI con ellos.
                            //loadActividadesFromDatabase(applicationContext, actividadAdapter)
                        } else {
                            Toast.makeText(this@MainActivity, "Fallo al sincronizar mensajes. Verifique conexión y credenciales.", Toast.LENGTH_LONG).show()
                            Log.e("MainActivity", "Fallo al sincronizar actividades desde el diálogo.")
                        }
                    }
                }
            }
        }
        dialogLayout.addView(btnSyncMessages)
        // **********************************

        // Cargar el estado actual de las preferencias para inicializar los switches y los EditText
        lifecycleScope.launch(Dispatchers.Main) {
            dataStore.data.first().let { preferences ->
                switch24h.isChecked = preferences[PREF_24H_FORMATO] ?: true
                switchBarraColores.isChecked = preferences[PREF_BARRAS_COLORES] ?: true
                switchLineaMovimiento.isChecked = preferences[PREF_LINEA_MOVIMIENTO] ?: true
                editTextEmail.setText(preferences[PREF_EMAIL] ?: "")
                editTextPassword.setText(preferences[PREF_PASSWORD] ?: "")
                // El UUID ya se cargó en loadPreferences(), solo lo mostramos aquí
                tvUUID.text = "UUID de la aplicaciión: ${appUUID}"

                // Aplicar el formato de hora y visibilidad inmediatamente al abrir el diálogo
                aplicarFormatoTiempo(switch24h.isChecked)
                aplicarVisibilidadBarraColores(switchBarraColores.isChecked)
                aplicarVisibilidadLineaMovimiento(switchLineaMovimiento.isChecked)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Configuración")
            .setView(dialogLayout) // Usamos el LinearLayout creado programáticamente
            .setPositiveButton("Aceptar") { _, _ ->
                // Acciones al aceptar
                val is24hChecked = switch24h.isChecked
                val isColorBarsChecked = switchBarraColores.isChecked
                val isLineMovementChecked = switchLineaMovimiento.isChecked
                val email = editTextEmail.text.toString()
                val password = editTextPassword.text.toString()

                aplicarFormatoTiempo(is24hChecked)
                aplicarVisibilidadBarraColores(isColorBarsChecked)
                aplicarVisibilidadLineaMovimiento(isLineMovementChecked)

                lifecycleScope.launch(Dispatchers.IO) {
                    savePreferences(
                        is24hChecked,
                        isColorBarsChecked,
                        isLineMovementChecked,
                        email,
                        password
                    )
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }

    // Funciones para aplicar los cambios en la UI
    private fun aplicarFormatoTiempo(is24Hour: Boolean) {
        if (is24Hour) {
            textClockHora.format12Hour = null
            textClockHora.format24Hour = "H:mm"
        } else {
            textClockHora.format12Hour = "h:mm"
            textClockHora.format24Hour = null
        }
    }

    private fun aplicarVisibilidadBarraColores(isVisible: Boolean) {
        if (!isVisible) {
            colorBarsContainer.animate().alpha(0f).setDuration(1500).start()
        } else {
            colorBarsContainer.animate().alpha(0.8f).setDuration(1500).start()
        }
    }

    private fun aplicarVisibilidadLineaMovimiento(isVisible: Boolean) {
        if (!isVisible) {
            lineaMovimiento.animate().alpha(0f).setDuration(1500).start()
        } else {
            lineaMovimiento.animate().alpha(0.3f).setDuration(1500).start()
        }
    }

    private suspend fun savePreferences(
        is24h: Boolean,
        isColorBars: Boolean,
        isLineMovement: Boolean,
        email: String,
        password: String
    ) {
        dataStore.edit { preferences ->
            preferences[PREF_24H_FORMATO] = is24h
            preferences[PREF_BARRAS_COLORES] = isColorBars
            preferences[PREF_LINEA_MOVIMIENTO] = isLineMovement
            preferences[PREF_EMAIL] = email
            preferences[PREF_PASSWORD] = password
            // El UUID no se guarda aquí, ya que se genera una vez en loadPreferences y se persiste en ese momento.
        }
        Log.d("DataStore", "Preferencias guardadas: 24h=$is24h, BarrasColores=$isColorBars, LineaMovimiento=$isLineMovement, Email=$email, Password=$password")
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        return batteryPct?.toInt() ?: 0
    }

    // Función para cargar las preferencias guardadas al iniciar la actividad
    private fun loadPreferences() {
        lifecycleScope.launch(Dispatchers.Main) {
            dataStore.data.first().let { preferences ->
                val is24h = preferences[PREF_24H_FORMATO] ?: true
                val isColorBars = preferences[PREF_BARRAS_COLORES] ?: true
                val isLineMovement = preferences[PREF_LINEA_MOVIMIENTO] ?: true
                val email = preferences[PREF_EMAIL] ?: ""
                val password = preferences[PREF_PASSWORD] ?: ""

                // Cargar el UUID, o generarlo si no existe
                appUUID = preferences[PREF_UUID] ?: UUID.randomUUID().toString().also { newUuid ->
                    // Guardar el nuevo UUID generado en DataStore
                    lifecycleScope.launch(Dispatchers.IO) {
                        dataStore.edit { prefs ->
                            //prefs[PREF_UUID] = newUuid
                            //Hardcodeamos el UUID para pruebas
                            prefs[PREF_UUID] = "7a59b181-6cd1-4d68-9f57-da9a9467589b"
                        }
                    }
                }
                Log.d("UUID","UUID: $appUUID")

                aplicarFormatoTiempo(is24h)
                aplicarVisibilidadBarraColores(isColorBars)
                aplicarVisibilidadLineaMovimiento(isLineMovement)

                Log.d("DataStore", "Preferencias cargadas al inicio: 24h=$is24h, BarrasColores=$isColorBars, LineaMovimiento=$isLineMovement, Email=$email, Password=$password")
            }
        }
    }
}
