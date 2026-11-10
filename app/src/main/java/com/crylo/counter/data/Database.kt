package com.crylo.counter.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import com.crylo.counter.R
import com.crylo.counter.core.CounterState
import com.crylo.counter.core.DailyTotal
import com.crylo.counter.core.TargetAction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "counters")
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB colour used for the counting screen accent and the list. */
    val color: Int,
    val step: Int = 1,
    val target: Int? = null,
    @ColumnInfo(name = "target_action") val targetAction: TargetAction = TargetAction.ASK,
    val count: Long = 0,
    val rounds: Int = 0,
    val position: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    val state get() = CounterState(count, rounds, step, target, targetAction)

    fun with(state: CounterState) = copy(count = state.count, rounds = state.rounds)
}

/** One row per counter per local calendar day; deleted together with its counter. */
@Entity(
    tableName = "daily_totals",
    primaryKeys = ["counter_id", "epoch_day"],
    foreignKeys = [
        ForeignKey(
            entity = Counter::class,
            parentColumns = ["id"],
            childColumns = ["counter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DailyRow(
    @ColumnInfo(name = "counter_id") val counterId: Long,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val total: Long,
    val rounds: Int,
) {
    fun toDaily() = DailyTotal(epochDay, total, rounds)
}

@Dao
interface CounterDao {
    @Query("SELECT * FROM counters ORDER BY position, id")
    fun observeCounters(): Flow<List<Counter>>

    @Query("SELECT * FROM counters WHERE id = :id")
    fun observeCounter(id: Long): Flow<Counter?>

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun counter(id: Long): Counter?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM counters")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(counter: Counter): Long

    @Update
    suspend fun update(counter: Counter)

    @Query("DELETE FROM counters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM daily_totals WHERE counter_id = :counterId AND epoch_day = :day")
    suspend fun daily(counterId: Long, day: Long): DailyRow?

    @Upsert
    suspend fun upsertDaily(row: DailyRow)

    @Query("SELECT * FROM daily_totals WHERE counter_id = :counterId ORDER BY epoch_day DESC")
    fun observeDaily(counterId: Long): Flow<List<DailyRow>>
}

class Converters {
    @TypeConverter
    fun fromAction(action: TargetAction): String = action.name

    @TypeConverter
    fun toAction(name: String): TargetAction =
        TargetAction.entries.firstOrNull { it.name == name } ?: TargetAction.ASK
}

@Database(entities = [Counter::class, DailyRow::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class CounterDatabase : RoomDatabase() {
    abstract fun dao(): CounterDao

    companion object {
        fun build(context: Context): CounterDatabase =
            Room.databaseBuilder(context, CounterDatabase::class.java, "counter.db")
                .addCallback(
                    object : Callback() {
                        // One ready-to-use counter on first launch, so the app is useful
                        // before anyone opens a settings screen.
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "INSERT INTO counters (name, color, step, target, target_action, count, rounds, position, created_at) " +
                                    "VALUES (?, ?, 1, NULL, 'ASK', 0, 0, 0, ?)",
                                arrayOf<Any>(
                                    context.getString(R.string.default_counter_name),
                                    CounterColors.first(),
                                    System.currentTimeMillis(),
                                ),
                            )
                        }
                    },
                )
                .build()
    }
}

/** The colour choices offered for a counter (ARGB). */
val CounterColors: List<Int> = listOf(
    0xFF2E7D32, 0xFF00897B, 0xFF0277BD, 0xFF3949AB, 0xFF6A1B9A,
    0xFFAD1457, 0xFFC62828, 0xFFEF6C00, 0xFFF9A825, 0xFF5D4037,
).map { it.toInt() }
