package com.meusremedios.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Medicamento::class,
        Horario::class,
        RegistroDose::class,
        ToquePendente::class,
        Receita::class,
        ReceitaMedicamento::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Conversores::class)
abstract class BancoDados : RoomDatabase() {
    abstract fun medicamentoDao(): MedicamentoDao
    abstract fun registroDoseDao(): RegistroDoseDao
    abstract fun toqueDao(): ToqueDao
    abstract fun receitaDao(): ReceitaDao

    companion object {
        fun criar(context: Context): BancoDados =
            Room.databaseBuilder(context, BancoDados::class.java, "remedios.db").build()
    }
}
