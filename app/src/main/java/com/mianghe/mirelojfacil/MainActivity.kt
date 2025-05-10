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
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextClock
import android.widget.TextView
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule
import androidx.work.*
import com.mianghe.mirelojfacil.workers.MedioPlazoWorker



class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var timer: Timer? = null
    //private var is24HourFormat = true
    //private var isIconoVisible = true



    fun iniciarTareaPeriodica() {
        val calendar = Calendar.getInstance()
        val currentSecond = calendar.get(Calendar.SECOND)
        //val currentMillisecond = calendar.get(Calendar.MILLISECOND)
        val esperaParaSincronizar =
            TimeUnit.SECONDS.toMillis(60 - currentSecond.toLong())// - currentMillisecond
        Log.d("location", "$currentSecond - $esperaParaSincronizar")
        getCurrentLocation()
        timer = Timer()
        timer?.schedule(
            delay = esperaParaSincronizar, // Ejecutar la primera vez inmediatamente
            period = 60 * 1000 // Ejecutar cada 60 segundos (1 minuto)
        ) {
            // Aquí va el código de la función que quieres ejecutar
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
        //getCurrentLocation()
        iniciarTareaPeriodica()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // Configuración del comportamiento del sistema de barras de estado ocultas.
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        planificarTareasMedioPlazo()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        iniciarTareaPeriodica()


        //Obtener el tamaño de la ventana main
        val ventanaPrincipal = findViewById<ConstraintLayout>(R.id.main)
        ventanaPrincipal.post {
            val width = ventanaPrincipal.width   // Ancho en píxeles
            val height = ventanaPrincipal.height // Alto en píxeles
            //Convertir a dp (densidad independiente)
            val widthDp = width / resources.displayMetrics.density
            val heightDp = height / resources.displayMetrics.density
            Log.d("TAG", "Ancho: $width px ($widthDp dp)")
            Log.d("TAG", "Alto: $height px ($heightDp dp)")
        }



        val mainLayout = findViewById<TextClock>(R.id.txtDia)
        mainLayout.setOnClickListener {
            showConfigDialog()
        }





    } // onCreate


    // Este es el planificador de tareas a Medio plazo (cada 15 minutos)
    // De momento se encarga de enviar los datos de la carga de la batería al servidor
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
                ExistingPeriodicWorkPolicy.KEEP, // O REPLACE si quieres reemplazar la tarea existente
                medioPlazoWorkRequest
            )
    }


    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
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
        val vcMiInfo = findViewById<ConstraintLayout>(R.id.main)
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
        val anchoContenedor = iconoMovimientoDia.width
        val anchoImagen = movableImage.width
        val normalizedPosition = horaActual.toFloat().coerceIn(0f, 1f)
        val newPosition = ((anchoContenedor - anchoImagen) * normalizedPosition).toInt()
        //val margenIzquierdo = (anchoContenedor - anchoImagen) / 2

        /*val containerWidth = iconoMovimientoDia.width - movableImage.width
        val leftMargin = (containerWidth * horaActual).toInt()*/

        val layoutParams = movableImage.layoutParams as FrameLayout.LayoutParams
        layoutParams.leftMargin = newPosition//leftMargin
        movableImage.layoutParams = layoutParams
    }
    fun actualizarLineaMovimiento(colorLinea: Int) {
        val lineaMovimiento = findViewById<View>(R.id.lineaMovimiento)
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
        //val tvInfo = findViewById<TextView>(R.id.tvInfo)
        val zonaHoraria = Calendar.getInstance().timeZone.id
        val salidaSol = horaSol(anioActual, mesActual, diaActual, longitud, latitud, zonaHoraria, 0)
        val puestaSol = horaSol(anioActual, mesActual, diaActual, longitud, latitud, zonaHoraria, 1)
        Log.d("location", "Salida Sol $salidaSol")
        Log.d("location", "Ocaso $puestaSol")

        val hora1: Double = salidaSol - (0.5 / 24.0) //Le quitamos media hora
        val hora2: Double = salidaSol
        val hora3: Double = 12.0 / 24.0
        val hora4: Double = 15.5 / 24.0
        val hora5: Double = puestaSol - (0.5 / 24.0) //Le quitamos media hora
        val hora6: Double = puestaSol + (0.25 / 24.0) //Le añadimos 1/4 de hora
        val hora7 = 24.0

        // Para la barra de colores
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

        //tvInfo.text = "Longitud: $longitud / Latitud: $latitud / Zona Horaria: $zonaHoraria / Salida Sol: $horaSalidaSol / Puesta Sol: $horaPuestaSol"

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
            //RangoHorario(0.0, hora8, R.color.fondo_noche, R.color.texto_noche, R.string.dia_por_la_noche, R.drawable.icono_noche)
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
            getResources().getString(rango.stringResId)
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
        val view1 = findViewById<View>(R.id.colorBarsContainer)
        //val view2 = findViewById<ImageView>(R.id.iconoMovimiento)
        val view3 = findViewById<View>(R.id.lineaMovimiento)



        // 1. Inflar el layout del diálogo
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_configuracion, null)

        //dialogView.findViewById<TextView>(R.id.estadoBateria2).text = "Bateria: --%"
        val estadoBateria = dialogView.findViewById<TextView>(R.id.estadoBateria)
        val batteryPercentage = getBatteryPercentage(this)
        estadoBateria.text = "Bateria: $batteryPercentage%"

        // 2. Obtener referencias a los Switch
        val switch1 = dialogView.findViewById<Switch>(R.id.switch24h)
        val switch2 = dialogView.findViewById<Switch>(R.id.switchBarraColores)
        val switch3 = dialogView.findViewById<Switch>(R.id.switchLineaMovimiento)
        val textClock = findViewById<TextClock>(R.id.txtHora)

        // 3. Configurar el diálogo
        val dialog = AlertDialog.Builder(this)
            .setTitle("Configuración")
            .setView(dialogView)
            .setPositiveButton("Aceptar") { _, _ ->
                // Acciones al aceptar
                if (switch1.isChecked) {
                    textClock.format12Hour = null // Usar el formato 24h definido en XML
                    textClock.format24Hour = "H:mm"
                } else {
                    textClock.format12Hour = "h:mm"
                    textClock.format24Hour = null // Usar el formato 12h definido en XML
                }
                if (!switch2.isChecked) {
                    view1.animate().alpha(0f).setDuration(1500).start()
                    //view2.animate().alpha(0f).setDuration(300).start()
                } else {
                    view1.animate().alpha(0.8f).setDuration(1500).start()
                    //view2.animate().alpha(1f).setDuration(300).start()
                }
                if (!switch3.isChecked) {
                    view3.animate().alpha(0f).setDuration(1500).start()
                } else {
                    view3.animate().alpha(0.3f).setDuration(1500).start()
                }

            }

            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

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


}