package com.specter.locate.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface LocationDao {
 @Insert suspend fun insert(point:LocationPoint)
 @Query("SELECT * FROM location_points ORDER BY capturedAt DESC") fun observe():Flow<List<LocationPoint>>
 @Query("DELETE FROM location_points") suspend fun clear()
}