package pe.edu.upeu.pharmamobil.platform

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Milisegundos desde 1970 segun el reloj del dispositivo. */
@OptIn(ExperimentalTime::class)
fun milisegundosActuales(): Long = Clock.System.now().toEpochMilliseconds()
