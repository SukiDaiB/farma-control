package com.meusremedios.app.alarme

import com.meusremedios.app.domain.DosePrevista
import java.time.LocalDateTime

/** O que está tocando agora: as doses pendentes e os horários a que pertencem. */
data class Alarme(
    val doses: List<DosePrevista>,
    val horarios: List<LocalDateTime>,
    val teste: Boolean = false,
)
