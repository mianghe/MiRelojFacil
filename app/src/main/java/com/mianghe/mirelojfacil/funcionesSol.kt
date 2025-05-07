package com.mianghe.mirelojfacil

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.*

fun horaANumero(hora:Int, minutos:Int):Double {
    val nuevaHora:Double = hora/24.0
    val nuevosMinutos:Double = minutos/60.0/24.0
    return nuevaHora+nuevosMinutos
}

fun numeroAHora(numero:Double):String {
    val horas = numero*24.0
    val digitoHora = floor(horas)
    val minutos = (horas - digitoHora)*60.0
    val digitoMinutos = floor(minutos)
    val laHora:Int = digitoHora.toInt()
    val losMinutos:Int = digitoMinutos.toInt()
    //return String.format("%02d:%02d", digitoHora.toInt(), digitoMinutos.toInt())
    return "$laHora:$losMinutos"
}

fun fechaAExcel(fecha: LocalDate): Double {
    val eraExcel = LocalDate.of(1900, 1, 1)
    val dias = fecha.toEpochDay() - eraExcel.toEpochDay()
    // Ajuste para el bug de Excel que considera 1 de febrero de 1900 como el día 1
    if (dias < 0) {
        return dias.toDouble() - 1.0
    }
    return dias.toDouble()
}

fun getJulianDay(fecha:Double, diferenciaUTC:Int):Double {
    return fecha+2415018.5-diferenciaUTC/24
}
fun getJulianCentury(julianDay:Double):Double {
    return (julianDay-2451545)/36525
}
fun getGeomMeanLongSun(julianCentury:Double):Double {
    return ((280.46646+julianCentury*(36000.76983 + julianCentury*0.0003032))%360)
}
fun getGeomMeanAnonSun(julianCentury:Double):Double {
    return 357.52911+julianCentury*(35999.05029 - 0.0001537*julianCentury)
}
fun getEccentEarthOrbit(julianCentury:Double):Double {
    return 0.016708634-julianCentury*(0.000042037+0.0000001267*julianCentury)
}
fun getSunEqCtr(julianCentury:Double, geomMeanAnonSun:Double):Double {
    return sin(Math.toRadians(geomMeanAnonSun))*(1.914602-julianCentury*(0.004817+0.000014*julianCentury))+sin(Math.toRadians(2*geomMeanAnonSun))*(0.019993-0.000101*julianCentury)+sin(Math.toRadians(3*geomMeanAnonSun))*0.000289
}
fun getSunTrueLong (geomMeanLongSun:Double, sunEqCtr:Double):Double {
    return geomMeanLongSun + sunEqCtr
}
fun getSunAppLong(sunTrueLong:Double, julianCentury:Double):Double {
    return sunTrueLong-0.00569-0.00478*sin(Math.toRadians(125.04-1934.136*julianCentury))
}
fun getMeanObliqEcliptic(julianCentury:Double):Double {
    return 23+(26+((21.448-julianCentury*(46.815+julianCentury*(0.00059-julianCentury*0.001813))))/60)/60
}
fun getObliqCorr(meanObliqEcliptic:Double, julianCentury:Double):Double {
    return meanObliqEcliptic+0.00256*cos(Math.toRadians(125.04-1934.136*julianCentury))
}
fun getSunDecLin(obliqCorr:Double, sunAppLong:Double):Double {
    return Math.toDegrees(asin(sin(Math.toRadians(obliqCorr))*sin(Math.toRadians(sunAppLong))))
}
fun getVarY(obliqCorr:Double):Double {
    return tan(Math.toRadians(obliqCorr/2))*tan(Math.toRadians(obliqCorr/2))
}
fun getEqOfTime(varY:Double, geomMeanLongSun:Double, eccentEarthOrbit:Double, geomMeanAnonSun:Double):Double {
    return 4*Math.toDegrees(varY*sin(2*Math.toRadians(geomMeanLongSun))
            -2*eccentEarthOrbit*sin(Math.toRadians(geomMeanAnonSun))
            +4*eccentEarthOrbit*varY*sin(Math.toRadians(geomMeanAnonSun))*cos(2*Math.toRadians(geomMeanLongSun))
            -0.5*varY*varY*sin(4*Math.toRadians(geomMeanLongSun))-1.25*eccentEarthOrbit*eccentEarthOrbit*sin(2*Math.toRadians(geomMeanAnonSun)))
}
fun getHASunrise(latitude:Double, sunDecLin:Double):Double {
    return Math.toDegrees(acos(cos(Math.toRadians(90.833))/(cos(Math.toRadians(latitude))*cos(Math.toRadians(sunDecLin)))-tan(Math.toRadians(latitude))*tan(Math.toRadians(sunDecLin))))
}
fun getSolarNoon(longitud:Double, eqOfTime:Double, diferenciaUTC:Int):Double {
    return (720-4*longitud-eqOfTime+diferenciaUTC*60)/1440
}
fun getSunriseTime(solarNoon:Double, haSunrise:Double):Double {
    return solarNoon-haSunrise*4/1440
}
fun getSunsetTime(solarNoon:Double, haSunrise:Double):Double {
    return solarNoon+haSunrise*4/1440
}
fun getUtcOffset(localDate: LocalDate, zoneId: ZoneId): ZoneOffset {
    val zoneRules = zoneId.rules
    val localDateTime = localDate.atStartOfDay()
    return zoneRules.getOffset(localDateTime)
}
/*
* El parametro eventoSol con valor 0 devuelve la hora de Salida, con valor 1 devuelve la hora de Puesta
* */
fun horaSol(year:Int, month:Int, day:Int, longitud:Double, latitud:Double, zonaHoraria:String, eventoSol:Int=0):Double {
    //val fecha = LocalDate.of(2025, 11, 6) // Fecha específica
    val fecha = LocalDate.of(year, month, day) // Fecha específica
    //val latitud = 37.9952 // Ejemplo para Murcia, España
    //val longitud = -1.0975
    //val zonaHoraria = "Europe/Madrid"

    val fechaEnNumero:Double = fechaAExcel(fecha)
    val zoneId = ZoneId.of(zonaHoraria)
    val offsetUTC = getUtcOffset(fecha, zoneId)
    val diferenciaUTC:Int = offsetUTC.totalSeconds/3600
    val julianDay:Double = getJulianDay(fechaEnNumero, diferenciaUTC)
    val julianCentury:Double = getJulianCentury(julianDay)
    val geomMeanLongSun:Double = getGeomMeanLongSun(julianCentury)
    val geomMeanAnonSun:Double = getGeomMeanAnonSun(julianCentury)
    val eccentEarthOrbit:Double = getEccentEarthOrbit(julianCentury)
    val sunEqCtr:Double = getSunEqCtr(julianCentury, geomMeanAnonSun)
    val sunTrueLong:Double = getSunTrueLong(geomMeanLongSun, sunEqCtr)
    val sunAppLong:Double = getSunAppLong(sunTrueLong, julianCentury)
    val meanObliqEcliptic:Double = getMeanObliqEcliptic(julianCentury)
    val obliqCorr:Double = getObliqCorr(meanObliqEcliptic, julianCentury)
    val sunDecLin:Double = getSunDecLin(obliqCorr, sunAppLong)
    val varY:Double = getVarY(obliqCorr)
    val eqOfTime:Double = getEqOfTime(varY, geomMeanLongSun, eccentEarthOrbit, geomMeanAnonSun)
    val haSunrise:Double = getHASunrise(latitud, sunDecLin)
    val solarNoon:Double = getSolarNoon(longitud, eqOfTime, diferenciaUTC)
    val sunriseTime:Double = getSunriseTime(solarNoon, haSunrise)
    val sunsetTime:Double = getSunsetTime(solarNoon, haSunrise)

    val respuesta = when (eventoSol) {
        0 -> sunriseTime
        1 -> sunsetTime
        else -> 0.0
    }

    return respuesta
}